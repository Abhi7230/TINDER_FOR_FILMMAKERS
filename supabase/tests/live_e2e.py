#!/usr/bin/env python3
"""
Calltime LIVE (Supabase) L0 end-to-end check.

Creates 3 throwaway users through the GoTrue admin API (service key, email_confirm), signs them in with a
password to get real JWTs, then drives the whole L0 loop through PostgREST / RPC exactly as the app does:
publish (with a draft rejected by listing_rules_ok), discover, apply, applicants with rate_fit, talk → match,
chat + RLS, safety flags, booking card v1/v2, role filled closing pending apps, auditions, scout + reverse
gates, asks, privacy, blocks, export and account deletion. FIX1 (F01-F17): contact details stored masked before booking
(chat, Ask notes, applicants' notes) with the raw copy only in schema ct, in-country remote roles, and discover_roles
skipping projects the viewer still has business on and shoots on dates they're booked.

Prints PASS/FAIL per check and exits non-zero on any failure. Re-runnable: it deletes its own users
(e2e-calltime-*@calltime.test) before and after; everything they own cascades.

  python3 supabase/tests/live_e2e.py            # against the local stack (npx supabase start)
  SUPABASE_URL=... SUPABASE_ANON_KEY=... SUPABASE_SERVICE_ROLE_KEY=... python3 supabase/tests/live_e2e.py

Never point this at a production project: it creates and deletes users.
"""
import datetime as dt
import json
import os
import shutil
import subprocess
import sys
import uuid

# Local Supabase defaults (public demo keys of `supabase start`, not secrets).
URL = os.environ.get("SUPABASE_URL", "http://127.0.0.1:54321").rstrip("/")
ANON = os.environ.get("SUPABASE_ANON_KEY",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0")
SERVICE = os.environ.get("SUPABASE_SERVICE_ROLE_KEY",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU")
DB_URL = os.environ.get("SUPABASE_DB_URL", "postgresql://postgres:postgres@127.0.0.1:54322/postgres")
MAILPIT = os.environ.get("MAILPIT_URL", "http://127.0.0.1:54324").rstrip("/")
PREFIX, DOMAIN, PASSWORD = "e2e-calltime-", "@calltime.test", "Calltime-e2e-pw-1"

# ───────────────────────── tiny HTTP layer (requests if present, else urllib) ─────────────────────────
try:
    import requests  # type: ignore

    def _http(method, url, headers, body):
        r = requests.request(method, url, headers=headers, data=None if body is None else json.dumps(body), timeout=30)
        return r.status_code, r.text
except ImportError:  # pragma: no cover
    import urllib.error
    import urllib.request

    def _http(method, url, headers, body):
        req = urllib.request.Request(url, method=method, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return resp.status, resp.read().decode()
        except urllib.error.HTTPError as e:
            return e.code, e.read().decode()


def http(method, path, token=None, key=None, body=None, extra=None):
    key = key or ANON
    h = {"apikey": key, "Authorization": f"Bearer {token or key}", "Content-Type": "application/json"}
    h.update(extra or {})
    status, text = _http(method, URL + path, h, body)
    try:
        data = json.loads(text) if text else None
    except ValueError:
        data = text
    return status, data


class Client:
    """One signed-in person (or anon when token is None)."""

    def __init__(self, name, token=None, uid=None, email=None):
        self.name, self.token, self.uid, self.email = name, token, uid, email

    def rpc(self, fn, **args):
        return http("POST", f"/rest/v1/rpc/{fn}", self.token, body=args)

    def select(self, table, query):
        return http("GET", f"/rest/v1/{table}?{query}", self.token)

    def insert(self, table, row, minimal=False):
        return http("POST", f"/rest/v1/{table}", self.token, body=row,
                    extra={"Prefer": "return=minimal" if minimal else "return=representation"})

    def patch(self, table, query, row):
        return http("PATCH", f"/rest/v1/{table}?{query}", self.token, body=row, extra={"Prefer": "return=representation"})


SERVICE_C = Client("service", token=SERVICE)
SERVICE_C.rpc = lambda fn, **a: http("POST", f"/rest/v1/rpc/{fn}", SERVICE, key=SERVICE, body=a)
SERVICE_C.select = lambda t, q: http("GET", f"/rest/v1/{t}?{q}", SERVICE, key=SERVICE)
SERVICE_C.insert = lambda t, r: http("POST", f"/rest/v1/{t}", SERVICE, key=SERVICE, body=r, extra={"Prefer": "return=representation"})
SERVICE_C.patch = lambda t, q, r: http("PATCH", f"/rest/v1/{t}?{q}", SERVICE, key=SERVICE, body=r, extra={"Prefer": "return=representation"})

# ───────────────────────── check bookkeeping ─────────────────────────
RESULTS = []


def check(cid, label, ok, detail=""):
    ok = bool(ok)
    RESULTS.append((cid, label, ok))
    print(f"{'PASS' if ok else 'FAIL'}  {cid:<4} {label}" + ("" if ok or not detail else f"\n        ↳ {str(detail)[:600]}"))
    return ok


def err_code(resp):
    status, data = resp
    if status < 300:
        return None
    return (data or {}).get("details") or (data or {}).get("code") if isinstance(data, dict) else str(data)


def is_error(resp):
    return resp[0] >= 300


# ───────────────────────── auth admin ─────────────────────────
def admin(method, path, body=None):
    return http(method, "/auth/v1/admin" + path, SERVICE, key=SERVICE, body=body)


def cleanup():
    status, data = admin("GET", "/users?page=1&per_page=1000")
    users = (data or {}).get("users", []) if isinstance(data, dict) else []
    n = 0
    for u in users:
        if (u.get("email") or "").startswith(PREFIX) and (u.get("email") or "").endswith(DOMAIN):
            admin("DELETE", f"/users/{u['id']}")
            n += 1
    return n


def make_user(tag):
    email = f"{PREFIX}{tag}-{uuid.uuid4().hex[:6]}{DOMAIN}"
    s, u = admin("POST", "/users", {"email": email, "password": PASSWORD, "email_confirm": True})
    if s >= 300:
        raise SystemExit(f"admin create user failed: {s} {u}")
    s, tok = http("POST", "/auth/v1/token?grant_type=password", body={"email": email, "password": PASSWORD})
    if s >= 300:
        raise SystemExit(f"password sign-in failed: {s} {tok}")
    return Client(tag, token=tok["access_token"], uid=u["id"], email=email)


# ───────────────────────── fixtures ─────────────────────────
TODAY = dt.date.today()
D1, D2 = (TODAY + dt.timedelta(days=14)).isoformat(), (TODAY + dt.timedelta(days=15)).isoformat()


def profile(name, primary, area, *, level="CREDITED_INDIE", comp="PAID_AND_SELECTIVE", secondary=(), links=(), wont=(), langs=("hi", "en")):
    city = area.split("_")[0]
    return {
        "display_name": name, "primary_role": primary, "level": level, "city_id": city, "area_id": area,
        "travel_radius_km": 20, "remote_ok": False, "languages": list(langs), "availability_pattern": "ANY_DAY",
        "comp_mode": comp, "unpaid_must_haves": [], "goals": ["REEL_FOOTAGE"], "content_wont_do": list(wont),
        "secondary_roles": [{"code": c, "proficiency": 2} for c in secondary], "step_up_roles": [],
        "work_links": list(links), "busy_dates": [], "gear": ["Zoom F6", "Sennheiser MKH 416"] if primary == "SOUND_MIXER" else [],
        "bio": "Crew in Mumbai.", "adult_confirmed": True, "email_verified": True, "id_verified": True,   # the last two must be ignored
    }


def project(pid, *, logline, content_flags, none_confirmed, roles):
    return {
        "id": pid, "title": "Last Local", "logline": logline, "format": "SHORT", "runtime_min": 12, "genres": ["DRAMA"],
        "languages": ["hi", "en"], "stage": "PRE", "script_status": "LOCKED", "ambition_tier": "FESTIVAL",
        "city_id": "mumbai", "area_id": "mumbai_andheri_west", "shoot_location_type": "OUTDOOR_PUBLIC",
        "exact_address": "Platform 3, Andheri station (west exit), Mumbai", "shoot_dates": [D2, D1, D1],
        "date_certainty": "LOCKED", "hours_cap": 10, "meals": "TWO_PLUS_MEALS", "transport": "MILEAGE_OR_TA_PAID",
        "travel_allowance_per_day": 300, "lodging": False, "content_flags": content_flags,
        "content_none_confirmed": none_confirmed, "audition_mode": "STUDIO_OR_PUBLIC_WITH_OTHERS", "bring_friend_welcome": True,
    }, roles


def terms(amount, pay_by):
    return {
        "role_code": "SOUND_MIXER", "credit_title": "Sound Recordist", "project_title": "Last Local", "is_post_role": False,
        "dates": [D1, D2], "call_window": "~07:00-17:00", "comp_type": "PAID_DAY", "amount": amount, "currency": "INR",
        "kit_fee_day": 1000, "payment_terms": "ON_WRAP", "pay_by_date": pay_by, "perks": [], "meals": "TWO_PLUS_MEALS",
        "transport": "MILEAGE_OR_TA_PAID", "travel_allowance_per_day": 300,
        "deliverables": [{"kind": "PAID", "due_date": pay_by, "owed_by": "PRODUCTION"},
                         {"kind": "CREDIT", "due_date": pay_by, "owed_by": "PRODUCTION"}],
        "payer_id": "spoofed", "payer_name": "Spoofed",
        "notice_pledge": "If you can't make it, tell the production at least 72 hours before.",
    }


def ids(rows, key="id"):
    return [r[key] for r in (rows or [])] if isinstance(rows, list) else []


def role_ids(rwps):
    return [x["role"]["id"] for x in rwps] if isinstance(rwps, list) else []


def otp_check():
    import re
    import time
    s, _ = _http("GET", MAILPIT + "/api/v1/messages?limit=1", {}, None)
    if s >= 300:
        print("SKIP  C113 email OTP sign-in (mailpit not running; start the stack without -x mailpit)")
        return
    email = f"{PREFIX}otp-{uuid.uuid4().hex[:6]}{DOMAIN}"
    s, d = http("POST", "/auth/v1/otp", body={"email": email, "create_user": True})
    code = None
    for _ in range(20):
        time.sleep(0.5)
        st, found = _http("GET", MAILPIT + "/api/v1/search?query=" + requests_quote(f'to:"{email}"'), {}, None)
        msgs = json.loads(found).get("messages", []) if st < 300 else []
        if msgs:
            st, msg = _http("GET", MAILPIT + f"/api/v1/message/{msgs[0]['ID']}", {}, None)
            m = re.search(r"\b(\d{6})\b", json.loads(msg).get("Text", ""))
            code = m.group(1) if m else None
            break
    s2, tok = http("POST", "/auth/v1/verify", body={"type": "email", "email": email, "token": code or "000000"})
    ok = s < 300 and code is not None and s2 < 300 and "access_token" in (tok or {})
    if ok:
        c = Client("otp", token=tok["access_token"], uid=tok["user"]["id"], email=email)
        s3, me = c.rpc("get_my_profile")
        s4, saved = c.rpc("save_profile", p_profile=profile("Otp Tester", "EDITOR", "bengaluru_jayanagar"), p_min_rate=None)
        ok = s3 < 300 and me is None and s4 < 300 and saved["email_verified"] is True
    check("C113", "email OTP: /otp → 6-digit code from the inbox → /verify → JWT works (new user has no profile yet)", ok, (s, d, code, s2, tok))


def requests_quote(q):
    from urllib.parse import quote
    return quote(q)


def run_psql(sql):
    exe = shutil.which("psql") or ("/opt/homebrew/bin/psql" if os.path.exists("/opt/homebrew/bin/psql") else None)
    if not exe:
        return None
    r = subprocess.run([exe, DB_URL, "-At", "-c", sql], capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else None



def iso_days(n):
    return (TODAY + dt.timedelta(days=n)).isoformat()


def l1_suite(owner, crew, backup, r_gaffer, mid2):
    """L1: day sheets, attendance, reschedule, withdraw → backup, auto-wrap, wrap checklist, feedback, release, cancel, lazy jobs."""
    # P2 "Night Bus": one paid sound role. crew gets booked, backup sits on the bench.
    p2, r2 = str(uuid.uuid4()), str(uuid.uuid4())
    proj, _ = project(p2, logline="A night bus driver and a sound recordist chase one quiet take across Mumbai.",
                      content_flags=[], none_confirmed=True, roles=[])
    proj["title"] = "Night Bus"
    roles2 = [{"id": r2, "role_code": "SOUND_MIXER", "count": 1, "comp_type": "PAID_DAY", "amount_min": 3000, "amount_max": 3000,
               "currency": "INR", "payment_terms": "ON_WRAP", "gear_policy": "PRODUCTION_PROVIDES", "perks": ["FOOTAGE_BY_DATE"],
               "footage_due_weeks": 4}]
    owner.rpc("save_project_draft", p_project=proj, p_roles=roles2)
    s, pub = owner.rpc("publish_project", p_project=p2)
    _, a_crew = crew.rpc("apply_to_role", p_role=r2, p_note="Free both days.")
    _, a_back = backup.rpc("apply_to_role", p_role=r2, p_note="Happy to be backup.")
    owner.rpc("decide_application", p_app=a_back["id"], p_decision="BACKUP")
    _, m4 = owner.rpc("decide_application", p_app=a_crew["id"], p_decision="TALK")
    t = terms(3000, D2)
    t["deliverables"] = []            # server falls back to CREDIT + PAID + FOOTAGE (perk)
    _, c = owner.rpc("send_booking_card", p_match=m4["id"], p_terms=dict(t, footage_due_weeks=4, perks=["FOOTAGE_BY_DATE"]))
    s, mm = crew.rpc("respond_booking_card", p_card=c["id"], p_accept=True)
    _, bapp = backup.select("applications", f"id=eq.{a_back['id']}&select=status,backup_rank")
    _, wi = crew.select("wrap_items", f"match_id=eq.{m4['id']}&select=kind")
    check("L01", "second project booked; the backup stays on the bench; default wrap items (CREDIT, PAID, FOOTAGE)",
          s < 300 and mm["state"] == "BOOKED" and bapp[0]["status"] == "BACKUP" and bapp[0]["backup_rank"] == 1
          and sorted(w["kind"] for w in wi) == ["CREDIT", "FOOTAGE", "PAID"], (pub, mm, bapp, wi))

    # ── day sheets ──
    day = {"project_id": p2, "day": D1, "general_call": "07:00", "wrap_by": "17:00", "address": "Bandstand, Bandra West, Mumbai",
           "parking_note": "Street parking on Byramji Jeejeebhoy Rd", "hospital_note": "Lilavati Hospital, 2 km",
           "meals_plan": "Breakfast + lunch", "per_person_call": {crew.uid: "06:30"}}
    s, sd = owner.rpc("publish_shoot_day", p_day=day)
    check("L02", "owner publishes a day sheet (maps link built server-side)",
          s < 300 and sd["maps_url"].startswith("https://maps.google.com/?q=Bandstand%2C%20Bandra"), (s, sd))
    s, days = crew.rpc("get_shoot_days", p_project=p2)
    d0 = days[0] if s < 300 and days else {}
    check("L03", "booked crew sees the sheet: own call 06:30, Day 1 of 1, teammates by first name only",
          d0.get("my_call", {}).get("call_time") == "06:30" and d0.get("day_index") == 1 and d0.get("day_count") == 1
          and d0["calls"][0]["short_name"] == "Meera", (s, days))
    s, odays = owner.rpc("get_shoot_days", p_project=p2)
    check("L04", "owner sees full short names", s < 300 and odays[0]["calls"][0]["short_name"] == "Meera K.", odays)
    resp = owner.rpc("publish_shoot_day", p_day=dict(day, day=iso_days(30)))
    check("L05", "a day sheet must be on a shoot date", err_code(resp) == "DAY", resp)
    s, bdays = backup.rpc("get_shoot_days", p_project=p2)
    s2, rows = backup.select("shoot_days", f"project_id=eq.{p2}&select=id")
    check("L06", "people who aren't booked see no day sheet (RPC and table)", s < 300 and bdays == [] and rows == [], (bdays, rows))
    s, _ = owner.rpc("nudge_call", p_day=sd["id"], p_profile=crew.uid)
    s2, notes = crew.select("notifications", "kind=eq.DAY_SHEET&select=title")
    check("L07", "nudge reaches someone who hasn't said Got it", s < 300 and any(n["title"] == "Please confirm your call time" for n in notes), notes)
    crew.rpc("ack_shoot_day", p_day=sd["id"])
    resp = owner.rpc("nudge_call", p_day=sd["id"], p_profile=crew.uid)
    s, full = owner.rpc("get_project_full", p_project=p2)
    check("L08", "after Got it: no more nudges; Team shows the ack", err_code(resp) == "ALREADY_ACKED" and full["team"][0]["next_ack"] is True, (resp, full and full["team"]))
    s, mv = crew.rpc("get_match_view", p_match=m4["id"])
    s2, joined = crew.rpc("joined_projects")
    jrow = next((j for j in joined if j["project"]["id"] == p2), {})
    check("L09", "match view + Joined tab carry the next call time",
          mv["next_shoot_day"]["id"] == sd["id"] and mv["my_call_time"] == "06:30" and "06:30" in (jrow.get("my_next_call") or ""), (mv and mv.get("my_call_time"), jrow))
    s, _ = crew.rpc("set_attendance", p_day=sd["id"], p_status="CONFIRMED")
    resp = owner.rpc("set_attendance", p_day=sd["id"], p_status="ATTENDED", p_profile=crew.uid)
    check("L10", "Still coming? → CONFIRMED; attendance can't be marked before the day", s < 300 and err_code(resp) == "TOO_EARLY", resp)
    resp = owner.rpc("mark_wrapped", p_project=p2)
    check("L11", "mark_wrapped before the last shoot day is refused", err_code(resp) == "TOO_EARLY", resp)

    # ── reschedule ──
    n1, n2 = iso_days(21), iso_days(22)
    s, _ = owner.rpc("reschedule_project", p_project=p2, p_dates=[n2, n1])
    _, d = owner.rpc("get_project_draft", p_project=p2)
    _, cards = crew.select("booking_cards", f"match_id=eq.{m4['id']}&order=version&select=id,version,status,terms,changed_fields")
    _, mrow = crew.select("matches", f"id=eq.{m4['id']}&select=state,change_pending")
    _, days = crew.rpc("get_shoot_days", p_project=p2)
    check("L12", "reschedule: count++, booked crew gets v2 with the new dates (changed: dates), old sheet cancelled",
          s < 300 and d["project"]["reschedule_count"] == 1 and d["project"]["shoot_dates"] == [n1, n2]
          and cards[-1]["version"] == 2 and cards[-1]["status"] == "SENT" and cards[-1]["terms"]["dates"] == [n1, n2]
          and "dates" in cards[-1]["changed_fields"] and mrow[0]["change_pending"] is True and days == [], (d and d["project"], cards, mrow, days))
    s, _ = crew.rpc("respond_booking_card", p_card=cards[-1]["id"], p_accept=True)
    check("L13", "crew accepts the new dates (penalty-free)", s < 300)

    # ── withdraw → backup #1 ──
    s, w = crew.rpc("withdraw_match", p_match=m4["id"], p_reason="PAID_WORK", p_note="Got a paid feature")
    _, d = owner.rpc("get_project_draft", p_project=p2)
    _, notes = owner.select("notifications", "kind=eq.WITHDRAWAL&select=body")
    check("L14", "withdraw: WITHDRAWN with ~3 weeks' notice, slot reopens, owner told, backup #1 returned",
          s < 300 and w["match"]["state"] == "WITHDRAWN" and w["match"]["notice_hours"] >= 72 and w["backup_application_id"] == a_back["id"]
          and d["roles"][0]["filled_count"] == 0 and d["roles"][0]["status"] == "OPEN" and notes and "Offer the slot" in notes[0]["body"], (w, d and d["roles"], notes))
    s, m5 = owner.rpc("offer_slot_to_backup", p_role=r2)
    _, bcards = backup.select("booking_cards", f"match_id=eq.{m5['id']}&select=id,version,status,terms")
    check("L15", "offer slot to backup #1 → match with a booking card on the same terms (amount, new dates)",
          s < 300 and m5["state"] == "OFFERED" and bcards and bcards[0]["terms"]["amount"] == 3000 and bcards[0]["terms"]["dates"] == [n1, n2], (m5, bcards))
    s, mm = backup.rpc("respond_booking_card", p_card=bcards[0]["id"], p_accept=True)
    check("L16", "backup accepts → BOOKED", s < 300 and mm["state"] == "BOOKED", mm)

    # ── time passes: the shoot happened ──
    SERVICE_C.patch("projects", f"id=eq.{p2}&select=id", {"shoot_dates": [iso_days(-5), iso_days(-4)]})
    s, sd2 = owner.rpc("publish_shoot_day", p_day={"project_id": p2, "day": iso_days(-5), "general_call": "08:00", "wrap_by": "18:00",
                                                   "address": "Carter Road, Bandra West, Mumbai"})
    s2, _ = owner.rpc("set_attendance", p_day=sd2["id"], p_status="ATTENDED", p_profile=backup.uid)
    resp = backup.rpc("set_attendance", p_day=sd2["id"], p_status="ATTENDED")
    check("L17", "owner marks attendance after the day; people can't mark their own attendance", s2 < 300 and err_code(resp) == "BAD_STATUS", (sd2, resp))
    backup.rpc("get_inbox")                     # lazy job: on-set auto-wrap 3 days after the last shoot day
    _, mrow = backup.select("matches", f"id=eq.{m5['id']}&select=state")
    _, d = owner.rpc("get_project_draft", p_project=p2)
    _, notes = backup.select("notifications", "kind=eq.FEEDBACK_UNLOCKED&select=title")
    check("L18", "get_inbox auto-wraps: match WRAPPED, project WRAPPED, wrap + feedback open",
          mrow[0]["state"] == "WRAPPED" and d["project"]["status"] == "WRAPPED" and notes, (mrow, d and d["project"]["status"], notes))

    # ── wrap checklist ──
    _, items = backup.select("wrap_items", f"match_id=eq.{m5['id']}&select=id,kind,owed_by,status")
    by = {i["kind"]: i["id"] for i in items}
    resp = backup.rpc("mark_wrap_item", p_item=by["CREDIT"])
    check("L19", "only the side that owes an item can mark it", err_code(resp) == "NOT_OWED_BY", resp)
    s, _ = owner.rpc("mark_wrap_item", p_item=by["PAID"], p_note="paid via UPI")
    resp = owner.rpc("confirm_wrap_item", p_item=by["PAID"], p_received=True)
    s2, paid = backup.rpc("confirm_wrap_item", p_item=by["PAID"], p_received=True)
    check("L20", "PAID: owner marks, owner can't self-confirm, collaborator confirms",
          s < 300 and err_code(resp) == "NOT_OWED_TO" and s2 < 300 and paid["status"] == "CONFIRMED", (resp, paid))
    resp = owner.rpc("mark_wrap_item", p_item=by["FOOTAGE"])
    owner.rpc("mark_wrap_item", p_item=by["FOOTAGE"], p_link="https://drive.example.com/night-bus-clips")
    _, nr = backup.rpc("confirm_wrap_item", p_item=by["FOOTAGE"], p_received=False)
    owner.rpc("mark_wrap_item", p_item=by["FOOTAGE"], p_link="https://drive.example.com/night-bus-clips-v2")
    _, fo = backup.rpc("confirm_wrap_item", p_item=by["FOOTAGE"], p_received=True)
    check("L21", "FOOTAGE needs a link; Not received → re-mark → Got it", err_code(resp) == "LINK" and nr["status"] == "NOT_RECEIVED"
          and fo["status"] == "CONFIRMED", (resp, nr, fo))
    owner.rpc("mark_wrap_item", p_item=by["CREDIT"])
    backup.rpc("confirm_wrap_item", p_item=by["CREDIT"], p_received=True)
    _, rep_b = backup.rpc("get_reputation", p_profile=backup.uid)
    _, card = owner.rpc("get_profile", p_profile=backup.uid)
    check("L22", "a confirmed CREDIT becomes a confirmed credit on the profile",
          rep_b["confirmed_credits"] == 1 and card["confirmed_credits"][0]["project_title"] == "Night Bus", (rep_b, card and card["confirmed_credits"]))
    s, pw = owner.rpc("project_wrap", p_project=p2)
    check("L23", "project_wrap: one checklist for the booked person, all confirmed",
          s < 300 and len(pw) == 1 and all(i["status"] == "CONFIRMED" for i in pw[0]["items"]), pw)

    # ── feedback (double-blind) ──
    s, fs = backup.rpc("feedback_state", p_match=m5["id"])
    resp = crew.rpc("submit_feedback", p_match=m4["id"], p_answers={"work_again": True})
    check("L24", "feedback unlocks only on WRAPPED matches", fs["unlocked"] is True and fs["i_submitted"] is False and err_code(resp) == "NOT_WRAPPED", (fs, resp))
    s, fs = backup.rpc("submit_feedback", p_match=m5["id"], p_answers={"paid": "ON_TIME", "delivered": "YES", "felt_safe": True,
                                                                         "kept": "YES", "work_again": True})
    check("L25", "one side submits: not revealed yet", s < 300 and fs["i_submitted"] and not fs["other_submitted"] and not fs["revealed"], fs)
    s, fs = owner.rpc("submit_feedback", p_match=m5["id"], p_answers={"showed_up": "YES", "prepared": "YES", "work_again": True})
    resp = owner.rpc("submit_feedback", p_match=m5["id"], p_answers={"showed_up": "YES"})
    _, rows = owner.select("feedback", f"match_id=eq.{m5['id']}&select=from_id")
    check("L26", "both submitted → revealed; no second submission; each side reads only their own answers",
          fs["revealed"] is True and err_code(resp) == "DUPLICATE" and [r["from_id"] for r in rows] == [owner.uid], (fs, resp, rows))
    s, keep = owner.rpc("keep_list")
    check("L27", "'Work again' feeds the owner's Keep list", s < 300 and [k["profile"]["id"] for k in keep] == [backup.uid], keep)

    # ── release + cancel with short notice (P3) ──
    p3, r3 = str(uuid.uuid4()), str(uuid.uuid4())
    proj, _ = project(p3, logline="Two runners, one stolen camera and a long night in Lower Parel. A short.",
                      content_flags=[], none_confirmed=True, roles=[])
    proj.update(title="Short Notice", shoot_dates=[iso_days(2), iso_days(3)])
    owner.rpc("save_project_draft", p_project=proj, p_roles=[{"id": r3, "role_code": "PA", "count": 2, "comp_type": "PAID_DAY",
                                                              "amount_min": 2500, "amount_max": 2500, "currency": "INR", "payment_terms": "ON_WRAP"}])
    owner.rpc("publish_project", p_project=p3)
    booked = {}
    for who in (crew, backup):
        _, a = who.rpc("apply_to_role", p_role=r3)
        _, m = owner.rpc("decide_application", p_app=a["id"], p_decision="TALK")
        tt = dict(terms(2500, iso_days(3)), role_code="PA", credit_title="Production Assistant", dates=[iso_days(2), iso_days(3)])
        _, cc = owner.rpc("send_booking_card", p_match=m["id"], p_terms=tt)
        who.rpc("respond_booking_card", p_card=cc["id"], p_accept=True)
        booked[who.name] = m["id"]
    s, rel = owner.rpc("release_match", p_match=booked["backup"], p_reason="SCHEDULE_CHANGED")
    _, notes = backup.select("notifications", "kind=eq.RELEASE&select=body")
    check("L28", "release under 72h: RELEASED, released_by_owner, notice < 72, person told",
          s < 300 and rel["state"] == "RELEASED" and rel["released_by_owner"] and rel["notice_hours"] < 72 and notes, (rel, notes))
    s, _ = owner.rpc("cancel_project", p_project=p3, p_reason="Lost the location")
    _, mrow = crew.select("matches", f"id=eq.{booked['crew']}&select=state,notice_hours,released_by_owner")
    _, d = owner.rpc("get_project_draft", p_project=p3)
    _, notes = crew.select("notifications", "kind=eq.RELEASE&select=title")
    check("L29", "cancel_project: booked people RELEASED with notice, project CANCELLED, roles closed, everyone told",
          s < 300 and mrow[0]["state"] == "RELEASED" and mrow[0]["released_by_owner"] and d["project"]["status"] == "CANCELLED"
          and all(r["status"] == "CLOSED" for r in d["roles"]) and any(n["title"] == "Project cancelled" for n in notes), (mrow, d and d["project"]["status"], notes))
    s, rep_o = owner.rpc("get_reputation", p_profile=owner.uid)
    check("L30", "Kept bookings counts late releases/cancellations against the owner (1/3)",
          s < 300 and rep_o["kept_bookings"] == {"num": 1, "den": 3}, rep_o)

    # ── lazy jobs ──
    _, a_exp = backup.rpc("apply_to_role", p_role=r_gaffer, p_note="Can light too.")
    SERVICE_C.patch("applications", f"id=eq.{a_exp['id']}&select=id", {"created_at": (dt.datetime.utcnow() - dt.timedelta(days=15)).isoformat() + "Z"})
    backup.rpc("get_inbox")
    _, rows = backup.select("applications", f"id=eq.{a_exp['id']}&select=status")
    _, notes = backup.select("notifications", "title=eq.No%20answer&select=body")
    check("L31", "closure guarantee: an unanswered application expires after 14 days, with a message", rows[0]["status"] == "EXPIRED" and notes, (rows, notes))
    four = (dt.datetime.utcnow() - dt.timedelta(days=4)).isoformat() + "Z"
    SERVICE_C.patch("matches", f"id=eq.{mid2}&select=id", {"created_at": four})
    SERVICE_C.patch("messages", f"match_id=eq.{mid2}&select=id", {"created_at": four})
    owner.rpc("get_inbox")
    _, notes = owner.select("notifications", "title=eq.Send%20the%20booking%20card%3F&select=deep_link")
    check("L32", "in talks 72h with no booking card → one nudge to the owner", len(notes) == 1 and notes[0]["deep_link"] == f"calltime://m/{mid2}", notes)
    eight = (dt.datetime.utcnow() - dt.timedelta(days=8)).isoformat() + "Z"
    SERVICE_C.patch("matches", f"id=eq.{mid2}&select=id", {"created_at": eight})
    SERVICE_C.patch("messages", f"match_id=eq.{mid2}&select=id", {"created_at": eight})
    crew.rpc("get_inbox")
    owner.rpc("get_inbox")
    _, mrow = crew.select("matches", f"id=eq.{mid2}&select=state")
    _, notes = owner.select("notifications", "title=eq.Send%20the%20booking%20card%3F&select=id")
    check("L33", "7 days of silence → the match closes politely; the nudge isn't repeated", mrow[0]["state"] == "CLOSED" and len(notes) == 1, (mrow, notes))
    return [p2, p3]


# ───────────────────────── FIX1 ─────────────────────────
MASK = "•••• shared contact hidden"


def no_contact(text):
    """True when none of the raw contact strings used below survive in [text]."""
    t = text or ""
    return all(x not in t for x in ("98200 11223", "98765 01234", "asha.edits@example.com", "@asha.sets", "kabir.lights@example.com"))


def raw_rest_blocked(client):
    """The moderation copies live in schema ct: not exposed by PostgREST, no grants. Every way in must fail."""
    out = []
    for table in ("message_raw", "application_note_raw"):
        out.append(client.select(table, "select=*"))
        out.append(http("GET", f"/rest/v1/{table}?select=*", client.token, extra={"Accept-Profile": "ct"}))
    return all(is_error(r) for r in out), out


def fix1_suite(owner, crew, third, backup, anon, pid, mid, mid3, r_actor, r_gaffer):
    """FIX1: server-side contact masking, in-country remote roles, live projects and booked dates in discover_roles."""
    # P5 "Platform Five": a Mumbai PA role (unpaid, on set) + an India-wide remote editor role.
    p5, r5_pa, r5_ed = str(uuid.uuid4()), str(uuid.uuid4()), str(uuid.uuid4())
    proj, _ = project(p5, logline="Two station porters race the monsoon to deliver one lost suitcase before the last train.",
                      content_flags=[], none_confirmed=True, roles=[])
    proj["title"], proj["shoot_dates"] = "Platform Five", [iso_days(20), iso_days(21)]
    roles5 = [
        {"id": r5_pa, "role_code": "PA", "count": 1, "comp_type": "UNPAID", "currency": "INR", "perks": ["FINAL_FILM_BY_DATE"],
         "final_film_due_weeks": 8},
        {"id": r5_ed, "role_code": "EDITOR", "count": 1, "comp_type": "STIPEND", "amount_min": 4000, "amount_max": 4000, "currency": "INR",
         "payment_terms": "WITHIN_7_DAYS", "remote_ok": True, "post_deadline": iso_days(40)},
    ]
    owner.rpc("save_project_draft", p_project=proj, p_roles=roles5)
    s, pub = owner.rpc("publish_project", p_project=p5)
    check("F01", "FIX1 fixture: Platform Five (on-set PA + remote editor) publishes", s < 300, (s, pub))

    # ── Ask notes and applicants' notes are stored masked before booking ──
    raw_ask = "Call me on 98200 11223 or mail asha.edits@example.com"
    s, ask = owner.rpc("send_ask", p_role=r5_ed, p_profile=backup.uid, p_note=raw_ask)
    ask = ask if s < 300 and isinstance(ask, dict) else {}
    check("F02", "Ask note with a phone + email is stored masked; note_flags has CONTACT",
          MASK in (ask.get("note") or "") and no_contact(ask.get("note")) and "CONTACT" in (ask.get("note_flags") or []), (s, ask))
    s, rows = backup.select("applications", f"id=eq.{ask.get('id')}&select=note")
    s2, ib = backup.rpc("get_inbox")
    inbox_note = next((a["application"]["note"] for a in (ib or {}).get("asks", []) if a["application"]["id"] == ask.get("id")), None)
    check("F03", "…the recipient reads it masked over REST and in get_inbox",
          s < 300 and rows and no_contact(rows[0]["note"]) and MASK in rows[0]["note"] and s2 < 300 and inbox_note and no_contact(inbox_note),
          (rows, inbox_note))
    raw = run_psql(f"select note from ct.application_note_raw where application_id = '{ask.get('id')}'")
    check("F04", "…and the raw Ask note is kept server-side for moderation only (ct.application_note_raw)", raw == raw_ask, raw)
    s, app5 = backup.rpc("apply_to_role", p_role=r5_pa, p_note="WhatsApp 98200 11223 or find me @asha.sets")
    app5 = app5 if s < 300 and isinstance(app5, dict) else {}
    s2, apps = owner.rpc("get_applicants", p_role=r5_pa)
    seen = next((a["application"]["note"] for a in (apps or []) if a["application"]["id"] == app5.get("id")), None)
    check("F05", "an applicant's note with contact details is stored masked; the owner sees it masked in get_applicants",
          no_contact(app5.get("note")) and MASK in (app5.get("note") or "") and s2 < 300 and seen and no_contact(seen), (app5, seen))

    # ── chat: masked before booking, as written after ──
    raw_msg = "My number is 98765 01234, email kabir.lights@example.com"
    s, row = third.insert("messages", {"match_id": mid3, "sender_id": third.uid, "kind": "TEXT", "body": raw_msg})
    msg_id = row[0]["id"] if s < 300 and row else None
    check("F06", "pre-booking: a contact message is stored masked (CONTACT flag kept for banners)",
          s < 300 and MASK in row[0]["body"] and no_contact(row[0]["body"]) and "CONTACT" in row[0]["flags"], (s, row))
    s, rows = owner.select("messages", f"id=eq.{msg_id}&select=body,flags")
    s2, rows2 = third.select("messages", f"id=eq.{msg_id}&select=body")
    check("F07", "…the counterpart (and the sender) read only the masked body via REST",
          s < 300 and rows and no_contact(rows[0]["body"]) and s2 < 300 and rows2 and no_contact(rows2[0]["body"]), (rows, rows2))
    raw = run_psql(f"select body from ct.message_raw where message_id = '{msg_id}'")
    check("F08", "…the raw copy sits in ct.message_raw for moderation", raw == raw_msg, raw)
    ok, details = True, []
    for c in (owner, third, crew, anon):
        blocked, out = raw_rest_blocked(c)
        ok = ok and blocked
        details.append(out[:1])
    resp = SERVICE_C.select("message_raw", "select=*")
    check("F09", "the raw copies are not readable via REST (anon, participants, even with Accept-Profile: ct)", ok and is_error(resp), (details, resp))
    s, row = crew.insert("messages", {"match_id": mid, "sender_id": crew.uid, "kind": "TEXT",
                                      "body": "Booked. My number is 98765 01234 for the call sheet."})
    n_raw = run_psql(f"select count(*) from ct.message_raw where message_id = '{row[0]['id']}'") if s < 300 and row else None
    check("F10", "post-booking: a contact message is stored as written (no masking, no raw copy)",
          s < 300 and "98765 01234" in row[0]["body"] and "CONTACT" in row[0]["flags"] and n_raw == "0", (s, row, n_raw))
    resp = crew.insert("messages", {"match_id": mid, "sender_id": crew.uid, "kind": "TEXT", "body": "x" * 2001})
    check("F11", "message length still capped at 2,000 characters (on the raw text)", is_error(resp), resp)

    # ── remote roles stay inside the viewer's own country ──
    us_pid, r_us_remote, r_us_onset = str(uuid.uuid4()), str(uuid.uuid4()), str(uuid.uuid4())
    SERVICE_C.insert("projects", {"id": us_pid, "owner_id": owner.uid, "payer_id": owner.uid, "title": "Swap Meet", "city_id": "los_angeles",
                                  "area_id": "los_angeles_hollywood", "status": "DRAFT", "shoot_dates": [iso_days(25)],
                                  "logline": "A father and daughter disagree about the one thing they'll never sell."})
    SERVICE_C.insert("project_roles", [
        {"id": r_us_remote, "project_id": us_pid, "role_code": "EDITOR", "comp_type": "PAID_FLAT", "amount_min": 1500, "amount_max": 1500,
         "currency": "USD", "payment_terms": "WITHIN_7_DAYS", "remote_ok": True, "post_deadline": iso_days(50), "gear_policy": None},
        {"id": r_us_onset, "project_id": us_pid, "role_code": "GAFFER", "comp_type": "PAID_DAY", "amount_min": 350, "amount_max": 350,
         "currency": "USD", "payment_terms": "ON_WRAP", "remote_ok": False, "post_deadline": None, "gear_policy": "PRODUCTION_PROVIDES"}])
    SERVICE_C.patch("projects", f"id=eq.{us_pid}", {"status": "OPEN", "published_at": dt.datetime.utcnow().isoformat() + "Z"})
    s, deck = crew.rpc("discover_roles")
    check("F12", "an Indian viewer gets India-wide remote roles and never a US remote role",
          s < 300 and r5_ed in role_ids(deck) and r_us_remote not in role_ids(deck), role_ids(deck))
    s, deck = crew.rpc("discover_roles", p_city="los_angeles")
    check("F13", "…even while browsing Los Angeles (LA on-set roles show; remote roles are still India's, never US ones)",
          s < 300 and r_us_onset in role_ids(deck) and r_us_remote not in role_ids(deck) and r5_ed in role_ids(deck), role_ids(deck))
    s, gdeck = anon.rpc("discover_roles", p_city="los_angeles")
    check("F14", "a guest browsing Los Angeles gets the browsed country's remote roles",
          s < 300 and r_us_remote in role_ids(gdeck) and r5_ed not in role_ids(gdeck), role_ids(gdeck))
    us = make_user("us")
    up = profile("Dana Reyes", "EDITOR", "los_angeles_hollywood", langs=("en",))
    up["city_id"], up["remote_ok"] = "los_angeles", True
    us.rpc("save_profile", p_profile=up, p_min_rate=None)
    s, udeck = us.rpc("discover_roles")
    check("F15", "a US viewer gets the US remote role and never the Indian one",
          s < 300 and r_us_remote in role_ids(udeck) and r5_ed not in role_ids(udeck), role_ids(udeck))

    # ── projects with a live match / application are not offered again; booked dates are busy ──
    s, gdeck = anon.rpc("discover_roles", p_city="mumbai")
    s2, cdeck = crew.rpc("discover_roles")
    s3, tdeck = third.rpc("discover_roles")
    check("F16", "booked on a project → none of its other roles; in talks on it → same (guests still see them)",
          s < 300 and r_gaffer in role_ids(gdeck) and r_actor in role_ids(gdeck)
          and not ({r_gaffer, r_actor} & set(role_ids(cdeck))) and r_actor not in role_ids(tdeck), (role_ids(cdeck), role_ids(tdeck)))
    clash_pid, part_pid = str(uuid.uuid4()), str(uuid.uuid4())
    r_c_sound, r_c_actor, r_p_sound, r_p_actor = (str(uuid.uuid4()) for _ in range(4))
    for p_id, dates, rs in ((clash_pid, [D1], (r_c_sound, r_c_actor)), (part_pid, [D1, iso_days(30)], (r_p_sound, r_p_actor))):
        SERVICE_C.insert("projects", {"id": p_id, "owner_id": backup.uid, "payer_id": backup.uid, "title": "Clash test", "city_id": "mumbai",
                                      "area_id": "mumbai_juhu", "status": "DRAFT", "shoot_dates": dates,
                                      "logline": "Booked-dates fixture project for the e2e run."})
        SERVICE_C.insert("project_roles", [
            {"id": rs[0], "project_id": p_id, "role_code": "SOUND_MIXER", "comp_type": "PAID_DAY", "amount_min": 2500, "amount_max": 2500,
             "currency": "INR", "payment_terms": "ON_WRAP", "gear_policy": "PRODUCTION_PROVIDES"},
            {"id": rs[1], "project_id": p_id, "role_code": "ACTOR", "comp_type": "PAID_DAY", "amount_min": 2500, "amount_max": 2500,
             "currency": "INR", "payment_terms": "ON_WRAP", "gear_policy": None}])     # bulk insert: same keys on every row
        SERVICE_C.patch("projects", f"id=eq.{p_id}", {"status": "OPEN", "published_at": dt.datetime.utcnow().isoformat() + "Z"})
    s, gdeck = anon.rpc("discover_roles", p_city="mumbai")
    s2, cdeck = crew.rpc("discover_roles")
    got = set(role_ids(cdeck))
    check("F17", "booked dates are busy: a one-day shoot on a booked day is dropped (crew and cast); a shoot with a free day "
                 "keeps the crew role but not the cast role (cast need every day)",
          s < 300 and {r_c_sound, r_c_actor, r_p_sound, r_p_actor} <= set(role_ids(gdeck))
          and s2 < 300 and r_p_sound in got and not ({r_c_sound, r_c_actor, r_p_actor} & got), sorted(got))
    return r5_pa


# ───────────────────────── the run ─────────────────────────
def main():
    print(f"Calltime LIVE e2e against {URL}\n")
    removed = cleanup()
    if removed:
        print(f"(cleaned up {removed} users from an earlier run)\n")

    owner, crew, third, backup = make_user("owner"), make_user("crew"), make_user("third"), make_user("backup")
    anon = Client("anon")

    # ── profiles ──
    s, me = owner.rpc("save_profile", p_profile=profile("Rohan Kulkarni", "DIRECTOR", "mumbai_versova"), p_min_rate=None)
    check("C01", "owner saves a profile via save_profile", s < 300 and me and me["display_name"] == "Rohan Kulkarni", (s, me))
    crew_links = [{"url": "https://youtu.be/sound-reel", "kind": "REEL", "role_code": "SOUND_MIXER", "title": "Sound reel"},
                  {"url": "https://example.com/meera-headshot.jpg", "kind": "HEADSHOT"}]
    s, cme = crew.rpc("save_profile", p_profile=profile("Meera Kapoor", "SOUND_MIXER", "mumbai_andheri_west", comp="OPEN_TO_UNPAID",
                                                        secondary=["ACTOR"], links=crew_links), p_min_rate=3000)
    check("C02", "crew profile saved with roles, work links and private rate",
          s < 300 and len(cme["work_links"]) == 2 and cme["secondary_roles"][0]["code"] == "ACTOR", (s, cme))
    check("C03", "verified flags from the client are ignored (id_verified=false), email_verified comes from auth",
          cme and cme["id_verified"] is False and cme["email_verified"] is True, cme)
    s, tme = third.rpc("save_profile", p_profile=profile("Kabir Shah", "GAFFER", "mumbai_goregaon_film_city", comp="PAID_ONLY",
                                                         secondary=["SOUND_MIXER"], wont=["NIGHT_SHOOT"]), p_min_rate=5000)
    check("C04", "third profile saved (PAID_ONLY, min rate, rules out NIGHT_SHOOT)", s < 300 and tme["comp_mode"] == "PAID_ONLY", (s, tme))
    backup.rpc("save_profile", p_profile=profile("Asha Rao", "SOUND_MIXER", "mumbai_bandra", secondary=["PA"]), p_min_rate=None)
    s, rows = crew.patch("profiles", f"id=eq.{crew.uid}", {"id_verified": True})
    check("C05", "direct UPDATE of own id_verified is reset by the trigger", s < 300 and rows and rows[0]["id_verified"] is False, (s, rows))
    s, rows = SERVICE_C.patch("profiles", f"id=eq.{owner.uid}", {"id_verified": True})
    check("C06", "service_role can set id_verified (concierge check)", s < 300 and rows and rows[0]["id_verified"] is True, (s, rows))
    s, rate = crew.rpc("my_private_min_rate")
    check("C07", "my_private_min_rate returns only my own rate", s < 300 and rate == 3000, (s, rate))

    # ── publish: a failing draft is rejected, then the fixed one goes OPEN ──
    pid = str(uuid.uuid4())
    r_sound, r_actor, r_gaffer = str(uuid.uuid4()), str(uuid.uuid4()), str(uuid.uuid4())
    bad_roles = [{"id": r_sound, "role_code": "SOUND_MIXER", "count": 1, "comp_type": "PAID_DAY", "currency": "INR",
                  "gear_policy": "BYO_WITH_KIT_FEE"}]
    p, rl = project(pid, logline="Call 98765 43210 for details about this short film shoot", content_flags=[], none_confirmed=False, roles=bad_roles)
    s, got = owner.rpc("save_project_draft", p_project=p, p_roles=rl)
    check("C08", "owner saves a draft (client uuid kept)", s < 300 and got == pid, (s, got))
    s, ok = owner.rpc("listing_rules_ok", p_project=pid)
    check("C09", "listing_rules_ok() is false for the bad draft", s < 300 and ok is False, (s, ok))
    resp = owner.rpc("publish_project", p_project=pid)
    code = err_code(resp) or ""
    check("C10", "publish_project rejects it with R01 (no pay), R07 (content unanswered), R14 (contact in logline)",
          is_error(resp) and all(r in code for r in ("R01", "R07", "R14")), resp)
    s, rows = anon.select("project_cards", f"id=eq.{pid}&select=id")
    check("C11", "a DRAFT is invisible to guests", s < 300 and rows == [], (s, rows))

    good_roles = [
        {"id": r_sound, "role_code": "SOUND_MIXER", "count": 1, "comp_type": "PAID_DAY", "amount_min": 2500, "amount_max": 2500,
         "currency": "INR", "kit_fee_day": 1000, "payment_terms": "ON_WRAP", "gear_policy": "BYO_WITH_KIT_FEE", "application_cap": 25},
        {"id": r_actor, "role_code": "ACTOR", "count": 1, "comp_type": "UNPAID", "currency": "INR", "perks": ["FOOTAGE_BY_DATE"],
         "footage_due_weeks": 6, "screening_question": "Which moment in the sides speaks to you?", "role_details": {"character": "Runaway"}},
        {"id": r_gaffer, "role_code": "GAFFER", "count": 1, "comp_type": "PAID_DAY", "amount_min": 3000, "amount_max": 3000,
         "currency": "INR", "payment_terms": "ON_WRAP", "gear_policy": "PRODUCTION_PROVIDES"},
    ]
    p, rl = project(pid, logline="A night-shift motorman and a runaway share the last local train to Churchgate.",
                    content_flags=["NIGHT_SHOOT"], none_confirmed=False, roles=good_roles)
    s, got = owner.rpc("save_project_draft", p_project=p, p_roles=rl)
    s2, ok = owner.rpc("listing_rules_ok", p_project=pid)
    check("C12", "fixed draft passes listing_rules_ok()", s < 300 and ok is True, (s, got, s2, ok))
    s, pub = owner.rpc("publish_project", p_project=pid)
    s2, d = owner.rpc("get_project_draft", p_project=pid)
    check("C13", "publish_project → OPEN; shoot_dates sorted/deduped, start/end/days derived",
          s < 300 and d["project"]["status"] == "OPEN" and d["project"]["shoot_dates"] == [D1, D2]
          and d["project"]["shoot_start"] == D1 and d["project"]["shoot_days"] == 2, (s, pub, d and d["project"]))
    check("C14", "role ids from the client are kept; default caps applied (actor 20, gaffer 25)",
          sorted(ids(d["roles"])) == sorted([r_sound, r_actor, r_gaffer])
          and {r["role_code"]: r["application_cap"] for r in d["roles"]}.get("ACTOR") == 20
          and {r["role_code"]: r["application_cap"] for r in d["roles"]}.get("GAFFER") == 25, d and d["roles"])

    # ── guests ──
    s, deck = anon.rpc("discover_roles", p_city="mumbai")
    check("C15", "guest discover_roles(mumbai) lists the 3 OPEN roles", s < 300 and set([r_sound, r_actor, r_gaffer]) <= set(role_ids(deck)), (s, deck))
    check("C16", "guest payload hides exact_address", s < 300 and all(x["project"]["exact_address"] is None for x in deck), deck and deck[0]["project"])
    resp = anon.select("projects", f"id=eq.{pid}&select=exact_address")
    check("C17", "guest can't select projects.exact_address directly (column privilege)", is_error(resp), resp)
    resp = anon.select("profiles", "select=id")
    check("C18", "guest can't read profiles", is_error(resp) or resp[1] == [], resp)
    resp = anon.rpc("apply_to_role", p_role=r_sound)
    check("C19", "guest can't apply (no execute on apply_to_role)", is_error(resp), resp)
    s, dens = anon.rpc("city_density", p_city="mumbai")
    check("C20", "city_density counts people/projects/roles honestly",
          s < 300 and dens["people"] >= 3 and dens["open_projects"] >= 1 and dens["per_role"].get("SOUND_MIXER", 0) >= 1, (s, dens))

    # ── discover + apply ──
    s, deck = crew.rpc("discover_roles")
    check("C21", "applicant discover_roles (own city default) includes the sound role, owner mini-card, team",
          s < 300 and r_sound in role_ids(deck) and deck[0]["owner"]["id"] == owner.uid and deck[0]["team"][0]["is_owner"], (s, deck and deck[0]))
    s, odeck = owner.rpc("discover_roles")
    check("C22", "owner's own roles are excluded from their deck", s < 300 and not (set(role_ids(odeck)) & {r_sound, r_actor, r_gaffer}), (s, odeck))
    reel_id = next(l["id"] for l in cme["work_links"] if l["kind"] == "REEL")
    s, app = crew.rpc("apply_to_role", p_role=r_sound, p_note="Own kit, did 3 indie shorts in Andheri.", p_link=reel_id, p_answer=None, p_kit=True)
    check("C23", "crew applies → PENDING application", s < 300 and app["status"] == "PENDING" and app["work_link_id"] == reel_id, (s, app))
    app_sound = app["id"] if s < 300 else None
    resp = crew.rpc("apply_to_role", p_role=r_sound)
    check("C24", "duplicate application rejected", err_code(resp) == "DUPLICATE", resp)
    resp = crew.rpc("apply_to_role", p_role=r_actor)
    check("C25", "screening question must be answered", err_code(resp) == "ANSWER_REQUIRED", resp)
    s, app2 = crew.rpc("apply_to_role", p_role=r_actor, p_note="Theatre background.", p_answer="The platform goodbye.")
    app_actor = app2["id"] if s < 300 else None
    s3, app3 = third.rpc("apply_to_role", p_role=r_sound, p_note="Can cover sound too.")
    app_third_sound = app3["id"] if s3 < 300 else None
    check("C26", "second applicant (third) and a cast application succeed", s < 300 and s3 < 300, (app2, app3))
    resp = owner.rpc("apply_to_role", p_role=r_gaffer)
    check("C27", "owner can't apply to own role", err_code(resp) == "OWN_ROLE", resp)
    s, n = crew.rpc("likes_left_today")
    check("C28", "likes_left_today = 25 - applications today", s < 300 and n == 23, (s, n))
    s, notes = owner.select("notifications", "kind=eq.APPLICATION_RECEIVED&select=id")
    check("C29", "owner notified of each application", s < 300 and len(notes) == 3, (s, notes))
    s, d = owner.rpc("get_project_draft", p_project=pid)
    cnt = {r["id"]: r["applications_count"] for r in d["roles"]}
    check("C30", "applications_count maintained server-side", cnt.get(r_sound) == 2 and cnt.get(r_actor) == 1, cnt)

    # ── applicants: rate_fit, no private data, headshots only for cast ──
    s, apps = owner.rpc("get_applicants", p_role=r_sound)
    raw = json.dumps(apps)
    mine = next((a for a in (apps or []) if a["application"]["applicant_id"] == crew.uid), None)
    check("C31", "get_applicants returns both applicants with rate_fit (2500/3000 = 0.833)",
          s < 300 and len(apps) == 2 and mine and abs(mine["rate_fit"] - 0.833) < 0.001, (s, apps))
    check("C32", "applicant payload never contains min_day_rate / content_wont_do values",
          "min_day_rate" not in raw and "3000" not in raw and "NIGHT_SHOOT" not in raw
          and all(a["card"]["profile"]["content_wont_do"] == [] for a in apps), raw[:400])
    check("C33", "crew role: no HEADSHOT link, no headshot_url",
          mine and mine["headshot_url"] is None and all(l["kind"] != "HEADSHOT" for l in mine["card"]["profile"]["work_links"]), mine)
    s, capps = owner.rpc("get_applicants", p_role=r_actor)
    c0 = capps[0] if s < 300 and capps else {}
    check("C34", "cast role: HEADSHOT link + headshot_url for the actor who applied; rate_fit 1 for unpaid",
          c0.get("headshot_url") == "https://example.com/meera-headshot.jpg" and c0.get("rate_fit") == 1.0
          and any(l["kind"] == "HEADSHOT" for l in c0["card"]["profile"]["work_links"]), (s, capps))
    resp = third.rpc("get_applicants", p_role=r_sound)
    check("C35", "non-owner can't call get_applicants", err_code(resp) == "NOT_OWNER", resp)

    # ── talk → match + system message ──
    s, m1 = owner.rpc("decide_application", p_app=app_sound, p_decision="TALK")
    check("C36", "owner TALKs → match TALKING", s < 300 and m1 and m1["state"] == "TALKING", (s, m1))
    mid = m1["id"] if s < 300 else None
    s, msgs = crew.select("messages", f"match_id=eq.{mid}&order=created_at")
    check("C37", "system message with the terms opens the chat", s < 300 and msgs and msgs[0]["kind"] == "SYSTEM"
          and msgs[0]["sender_id"] is None and "₹2,500/day" in msgs[0]["body"], (s, msgs))
    s, notes = crew.select("notifications", "kind=eq.MATCH_CREATED&select=deep_link")
    check("C38", "collaborator notified with a calltime://m/ deep link", s < 300 and notes and notes[0]["deep_link"] == f"calltime://m/{mid}", (s, notes))

    # ── chat + RLS + flags ──
    s, row = crew.insert("messages", {"match_id": mid, "sender_id": crew.uid, "kind": "TEXT", "body": "Yes, I'm in. What are the call times?"})
    check("C39", "participant can insert a TEXT message", s < 300 and row and row[0]["flags"] == [], (s, row))
    resp = third.insert("messages", {"match_id": mid, "sender_id": third.uid, "kind": "TEXT", "body": "hi"})
    check("C40", "third user can't insert into someone else's chat (RLS)", is_error(resp), resp)
    resp = crew.insert("messages", {"match_id": mid, "sender_id": owner.uid, "kind": "TEXT", "body": "spoof"})
    check("C41", "can't spoof sender_id", is_error(resp), resp)
    resp = crew.insert("messages", {"match_id": mid, "sender_id": crew.uid, "kind": "SYSTEM", "body": "fake system"})
    check("C42", "can't insert SYSTEM / AUDITION kinds directly", is_error(resp), resp)
    s, rows = third.select("messages", f"match_id=eq.{mid}")
    check("C43", "third user reads no messages of that match", s < 300 and rows == [], (s, rows))
    s, row = owner.insert("messages", {"match_id": mid, "sender_id": owner.uid, "kind": "TEXT",
                                       "body": "Before we confirm, please pay the registration fee of Rs 2000 via UPI."})
    check("C44", "owner asking for money → MONEY_FROM_TALENT flag (trigger)", s < 300 and "MONEY_FROM_TALENT" in row[0]["flags"], (s, row))
    s, row = crew.insert("messages", {"match_id": mid, "sender_id": crew.uid, "kind": "TEXT",
                                      "body": "call me on 98765 43210 or whatsapp me, meera.k@gmail.com"})
    check("C45", "contact + off-platform → CONTACT, OFF_PLATFORM flags", s < 300 and {"CONTACT", "OFF_PLATFORM"} <= set(row[0]["flags"]), (s, row))
    s, row = crew.insert("messages", {"match_id": mid, "sender_id": crew.uid, "kind": "TEXT", "body": "Will you send ₹ via UPI on wrap?"})
    check("C46", "collaborator talking about being paid gets no MONEY flag (direction rule)", s < 300 and "MONEY_FROM_TALENT" not in row[0]["flags"], (s, row))
    s, row = owner.insert("messages", {"match_id": mid, "sender_id": owner.uid, "kind": "TEXT", "body": "Send bold photos and come alone to my flat"})
    check("C47", "body request + private meeting flags", s < 300 and {"BODY_REQUEST", "PRIVATE_MEETING"} <= set(row[0]["flags"]), (s, row))
    s, inbox = crew.rpc("get_inbox")
    conv = next((c for c in (inbox or {}).get("conversations", []) if c["match"]["id"] == mid), None)
    check("C48", "get_inbox: conversation row with counterpart short name and unread count",
          s < 300 and conv and conv["counterpart_name"] == "Rohan K." and conv["unread"] >= 2, (s, conv))
    crew.rpc("mark_read", p_match=mid)
    s, inbox = crew.rpc("get_inbox")
    conv = next((c for c in (inbox or {}).get("conversations", []) if c["match"]["id"] == mid), None)
    check("C49", "mark_read clears unread", s < 300 and conv and conv["unread"] == 0, (s, conv))

    # ── privacy ──
    s, card = third.rpc("get_profile", p_profile=crew.uid)
    check("C50", "third (no relationship) gets null from get_profile(crew)", s < 300 and card is None, (s, card))
    s, rows = third.select("profiles", f"id=eq.{crew.uid}&select=id")
    check("C51", "third can't select crew's profile row", s < 300 and rows == [], (s, rows))
    s, rows = third.select("profile_private", "select=*")
    check("C52", "third sees only their own private row", s < 300 and ids(rows) == [third.uid], (s, rows))
    s, rows = third.select("applications", f"applicant_id=eq.{crew.uid}")
    check("C53", "third can't read crew's applications", s < 300 and rows == [], (s, rows))
    s, card = owner.rpc("get_profile", p_profile=crew.uid)
    check("C54", "owner (relationship) reads crew's card; sanitised (no content_wont_do, no rate)",
          s < 300 and card and card["profile"]["display_name"] == "Meera Kapoor" and "min_day_rate" not in json.dumps(card)
          and card["profile"]["content_wont_do"] == [], (s, card))
    s, card = third.rpc("get_profile", p_profile=owner.uid)
    check("C55", "posters are public: anyone signed in can read the owner of an OPEN project", s < 300 and card and card["profile"]["id"] == owner.uid, (s, card))

    # ── booking card v1 → BOOKED, role FILLED, pending closed, wrap items ──
    resp = crew.rpc("send_booking_card", p_match=mid, p_terms=terms(2500, D2))
    check("C56", "only the payer can send a booking card", err_code(resp) == "NOT_PAYER", resp)
    s, c1 = owner.rpc("send_booking_card", p_match=mid, p_terms=terms(2500, D2))
    check("C57", "owner sends booking card v1 → SENT; payer stamped server-side",
          s < 300 and c1["version"] == 1 and c1["status"] == "SENT" and c1["terms"]["payer_id"] == owner.uid
          and c1["terms"]["payer_name"] == "Rohan K.", (s, c1))
    s, mrow = crew.select("matches", f"id=eq.{mid}&select=state")
    check("C58", "match → OFFERED", s < 300 and mrow[0]["state"] == "OFFERED", (s, mrow))
    s, rows = third.select("booking_cards", f"match_id=eq.{mid}")
    check("C59", "third can't read the booking card", s < 300 and rows == [], (s, rows))
    resp = third.rpc("respond_booking_card", p_card=c1["id"], p_accept=True)
    check("C60", "only the collaborator can accept", err_code(resp) == "NOT_COLLABORATOR", resp)
    s, mm = crew.rpc("respond_booking_card", p_card=c1["id"], p_accept=True)
    check("C61", "collaborator accepts → BOOKED", s < 300 and mm["state"] == "BOOKED" and mm["booked_at"], (s, mm))
    s, d = owner.rpc("get_project_draft", p_project=pid)
    sr = next(r for r in d["roles"] if r["id"] == r_sound)
    check("C62", "role filled_count = 1 and status FILLED", sr["filled_count"] == 1 and sr["status"] == "FILLED", sr)
    s, rows = third.select("applications", f"id=eq.{app_third_sound}&select=status")
    check("C63", "role filled closes the other PENDING application", s < 300 and rows and rows[0]["status"] == "CLOSED", (s, rows))
    s, notes = third.select("notifications", "kind=eq.APPLICATION_CLOSED&select=body")
    check("C64", "…and the closed applicant gets a closure notification", s < 300 and notes and "filled" in notes[0]["body"], (s, notes))
    s, wraps = crew.select("wrap_items", f"match_id=eq.{mid}&select=kind,owed_by,status,due_date")
    check("C65", "wrap_items upserted from deliverables (PAID, CREDIT owed by production)",
          s < 300 and sorted(w["kind"] for w in wraps) == ["CREDIT", "PAID"] and all(w["owed_by"] == "PRODUCTION" for w in wraps), (s, wraps))
    s, deck = crew.rpc("discover_roles")
    check("C66", "filled role leaves the deck", s < 300 and r_sound not in role_ids(deck), role_ids(deck))

    # ── v2 with diff, re-accept ──
    s, c2 = owner.rpc("send_booking_card", p_match=mid, p_terms=terms(2800, D2))
    s2, mrow = crew.select("matches", f"id=eq.{mid}&select=state,change_pending")
    check("C67", "v2 while BOOKED → change_pending, changed_fields = [amount]",
          s < 300 and c2["version"] == 2 and c2["changed_fields"] == ["amount"] and mrow[0]["state"] == "BOOKED"
          and mrow[0]["change_pending"] is True, (s, c2, mrow))
    resp = crew.rpc("respond_booking_card", p_card=c1["id"], p_accept=True)
    check("C68", "an old version can't be accepted", err_code(resp) == "CARD_STALE", resp)
    s, mm = crew.rpc("respond_booking_card", p_card=c2["id"], p_accept=True)
    s2, cards = crew.select("booking_cards", f"match_id=eq.{mid}&order=version&select=version,status")
    s3, d = owner.rpc("get_project_draft", p_project=pid)
    check("C69", "accept v2 → v1 SUPERSEDED, v2 ACCEPTED, filled_count still 1",
          [c["status"] for c in cards] == ["SUPERSEDED", "ACCEPTED"] and mm["change_pending"] is False
          and next(r for r in d["roles"] if r["id"] == r_sound)["filled_count"] == 1, (cards, mm))
    s, mv = crew.rpc("get_match_view", p_match=mid)
    check("C70", "get_match_view: booked collaborator sees exact_address; current = accepted = v2",
          s < 300 and mv["item"]["project"]["exact_address"].startswith("Platform 3") and mv["current_card"]["version"] == 2
          and mv["accepted_card"]["version"] == 2 and mv["is_owner"] is False, (s, mv and mv.get("current_card")))

    # ── contacts ──
    s, sc = owner.rpc("shared_contacts", p_match=mid)
    check("C71", "contacts hidden until the other side shares", s < 300 and sc is None, (s, sc))
    s, mm = crew.rpc("share_contacts", p_match=mid)
    s2, sc = owner.rpc("shared_contacts", p_match=mid)
    check("C72", "share_contacts (BOOKED only) reveals the sharer's email to the counterpart",
          s < 300 and mm["contacts_shared_by_collab"] is True and sc and sc["email"] == crew.email, (s, mm, sc))

    # ── auditions (cast) ──
    s, m2 = owner.rpc("decide_application", p_app=app_actor, p_decision="TALK")
    mid2 = m2["id"] if s < 300 else None
    resp = owner.rpc("send_audition_request", p_match=mid2, p_payload={"mode": "SELF_TAPE", "due_date": D1})
    check("C73", "audition mode must equal the listing's audition_mode", err_code(resp) == "AUDITION_MODE", resp)
    base = {"mode": "STUDIO_OR_PUBLIC_WITH_OTHERS", "date": D1, "venue": "Prithvi Cafe rehearsal room", "address": "Juhu Church Rd, Mumbai"}
    resp = owner.rpc("send_audition_request", p_match=mid2, p_payload=dict(base, time="22:00", also_present=["Casting assistant"]))
    check("C74", "in-person audition outside 08:00-21:00 rejected", err_code(resp) == "HOURS", resp)
    resp = owner.rpc("send_audition_request", p_match=mid2, p_payload=dict(base, time="11:00", also_present=[]))
    check("C75", "in-person audition needs ≥1 other person present", err_code(resp) == "ALSO_PRESENT", resp)
    resp = crew.rpc("send_audition_request", p_match=mid2, p_payload=dict(base, time="11:00", also_present=["DP"]))
    check("C76", "only the owner requests auditions", err_code(resp) == "NOT_OWNER", resp)
    s, am = owner.rpc("send_audition_request", p_match=mid2,
                      p_payload=dict(base, time="11:00", also_present=["Casting assistant", "DP"], meeting_link="https://meet.example/x"))
    check("C77", "valid studio audition → AUDITION message with fixed safety lines; meeting link dropped",
          s < 300 and am["kind"] == "AUDITION" and "No nudity at auditions" in am["body"]
          and am["payload"]["request"]["meeting_link"] is None and am["payload"]["request"]["time"] == "11:00", (s, am))
    resp = owner.rpc("send_audition_request", p_match=mid, p_payload=dict(base, time="11:00", also_present=["DP"]))
    check("C78", "auditions only on cast-role matches", err_code(resp) == "NOT_CAST", resp)
    resp = crew.rpc("respond_audition", p_message=am["id"], p_response="TAPE_SENT", p_tape_url="https://youtu.be/tape")
    check("C79", "TAPE_SENT only for self-tape requests", err_code(resp) == "NOT_SELF_TAPE", resp)
    s, am2 = crew.rpc("respond_audition", p_message=am["id"], p_response="WILL_DO")
    check("C80", "actor answers I'll do it", s < 300 and am2["payload"]["response"] == "WILL_DO", (s, am2))

    # ── scout + reverse gates + asks ──
    resp = owner.rpc("scout_candidates", p_role=r_actor)
    check("C81", "scout_candidates refuses cast roles", err_code(resp) == "CAST_SCOUT", resp)
    s, why = owner.rpc("ask_check", p_role=r_gaffer, p_profile=third.uid)
    s2, sc = owner.rpc("scout_candidates", p_role=r_gaffer)
    check("C82", "reverse G4: PAID_ONLY gaffer with min 5000 can't be asked onto a 3000/day role (reason, not in scout)",
          s < 300 and why and "below what Kabir takes" in why and third.uid not in [x["card"]["profile"]["id"] for x in sc], (why, sc))
    third.rpc("save_profile", p_profile=profile("Kabir Shah", "GAFFER", "mumbai_goregaon_film_city", comp="PAID_ONLY",
                                                secondary=["SOUND_MIXER"], wont=["NIGHT_SHOOT"]), p_min_rate=2000)
    s, why = owner.rpc("ask_check", p_role=r_gaffer, p_profile=third.uid)
    check("C83", "reverse G5: ruled-out content (NIGHT_SHOOT) blocks the ask", s < 300 and why and "ruled out" in why, (s, why))
    third.rpc("save_profile", p_profile=profile("Kabir Shah", "GAFFER", "mumbai_goregaon_film_city", comp="PAID_ONLY",
                                                secondary=["SOUND_MIXER"], wont=[]), p_min_rate=2000)
    s, sc = owner.rpc("scout_candidates", p_role=r_gaffer)
    hit = next((x for x in (sc or []) if x["card"]["profile"]["id"] == third.uid), None)
    check("C84", "gates pass → third appears in scout with rate_fit 1.0, never the rate",
          s < 300 and hit and hit["rate_fit"] == 1.0 and "min_day_rate" not in json.dumps(sc) and crew.uid not in json.dumps(sc), (s, sc))
    resp = owner.rpc("send_ask", p_role=r_actor, p_profile=third.uid, p_note="Would you read for this?")
    check("C85", "cast asks only for the Keep list", err_code(resp) == "ASK_BLOCKED", resp)
    s, ask = owner.rpc("send_ask", p_role=r_gaffer, p_profile=third.uid, p_note="Saw your work at Film City. Free those dates?")
    check("C86", "send_ask → INVITED application", s < 300 and ask["origin"] == "INVITED" and ask["status"] == "PENDING", (s, ask))
    s, left = owner.rpc("asks_left_today", p_project=pid)
    check("C87", "asks_left_today = 10 - asks sent", s < 300 and left == 9, (s, left))
    resp = owner.rpc("decide_application", p_app=ask["id"], p_decision="TALK")
    check("C88", "owner can't TALK on an ask the person hasn't answered", err_code(resp) == "ASK_PENDING", resp)
    s, ib = third.rpc("get_inbox")
    check("C89", "Ask shows in the recipient's inbox with the role card", s < 300 and ib["asks"] and ib["asks"][0]["item"]["role"]["id"] == r_gaffer, (s, ib))
    s, m3 = third.rpc("respond_ask", p_app=ask["id"], p_accept=True, p_reason=None)
    check("C90", "respond_ask accept → match TALKING", s < 300 and m3 and m3["state"] == "TALKING", (s, m3))
    mid3 = m3["id"] if s < 300 else None

    # ── direct-write protections ──
    s, rows = third.patch("projects", f"id=eq.{pid}&select=id", {"title": "Hacked"})
    check("C91", "non-owner UPDATE of a project touches 0 rows (RLS)", s < 300 and rows == [], (s, rows))
    resp = owner.patch("projects", f"id=eq.{pid}&select=id", {"status": "WRAPPED"})
    check("C92", "owner can't set status directly (only via RPC)", is_error(resp), resp)
    resp = owner.patch("projects", f"id=eq.{pid}&select=id", {"logline": "WhatsApp 98765 43210 to get the sides for this shoot"})
    check("C93", "editing an OPEN listing re-runs the rules (contact info rejected at commit)", is_error(resp) and "RULES" in (err_code(resp) or ""), resp)
    s, rows = owner.patch("projects", f"id=eq.{pid}&select=id,title", {"title": "Last Local (2026)"})
    check("C94", "a clean edit of an OPEN listing works", s < 300 and rows and rows[0]["title"] == "Last Local (2026)", (s, rows))
    resp = owner.patch("project_roles", f"id=eq.{r_gaffer}", {"filled_count": 5})
    check("C95", "system counters can't be written directly", is_error(resp), resp)
    resp = crew.insert("applications", {"role_id": r_gaffer, "applicant_id": crew.uid})
    check("C96", "applications only via RPC (no direct insert)", is_error(resp), resp)

    # ── L1 ──
    l1_projects = l1_suite(owner, crew, backup, r_gaffer, mid2)

    # ── FIX1: contact masking, in-country remote roles, live projects, booked dates ──
    r5_pa = fix1_suite(owner, crew, third, backup, anon, pid, mid, mid3, r_actor, r_gaffer)

    # ── block ──
    # (third is in talks on the Last Local gaffer role, so that project's roles are already out of their deck; use Platform Five)
    s, deck = third.rpc("discover_roles")
    before = r5_pa in role_ids(deck)
    s, _ = third.rpc("block_user", p_profile=owner.uid)
    s2, deck = third.rpc("discover_roles")
    check("C97", "block hides the blocked person's roles from the deck",
          before and s < 300 and r5_pa not in role_ids(deck) and not any(x["owner"]["id"] == owner.uid for x in deck), (before, role_ids(deck)))
    s, card = owner.rpc("get_profile", p_profile=third.uid)
    check("C98", "…and hides the blocker's profile from them", s < 300 and card is None, (s, card))
    resp = third.insert("messages", {"match_id": mid3, "sender_id": third.uid, "kind": "TEXT", "body": "hello?"})
    s, mrow = third.select("matches", f"id=eq.{mid3}&select=state")
    check("C99", "…ends their match (CLOSED) and blocks further messages", is_error(resp) and mrow and mrow[0]["state"] == "CLOSED", (resp, mrow))
    s, ib = owner.rpc("get_inbox")
    check("C100", "…and drops the conversation from the inbox", s < 300 and mid3 not in [c["match"]["id"] for c in ib["conversations"]], ib and ib["conversations"])
    s, bl = third.rpc("my_blocked")
    check("C101", "my_blocked lists the blocked person", s < 300 and [x["profile"]["id"] for x in bl] == [owner.uid], (s, bl))

    # ── reports, events, notifications read ──
    s, rep = crew.insert("reports", {"reporter_id": crew.uid, "target_kind": "MESSAGE", "target_id": mid, "reason": "MONEY_REQUEST", "details": "Asked for a fee"})
    check("C102", "report insert (own) → RECEIVED", s < 300 and rep[0]["status"] == "RECEIVED", (s, rep))
    resp = crew.patch("reports", f"id=eq.{rep[0]['id']}", {"status": "ACTION_TAKEN"})
    check("C103", "reporter can't change report status", is_error(resp) or resp[1] == [], resp)
    s, ev = crew.insert("events", {"user_id": crew.uid, "name": "e2e_event", "props": {"k": "v"}}, minimal=True)
    resp = crew.insert("events", {"user_id": owner.uid, "name": "spoof"}, minimal=True)
    s = s if is_error(resp) else 999
    check("C104", "events: own insert only", s < 300, (s, ev))
    s, nl = crew.select("notifications", "select=id&limit=1")
    s2, upd = crew.patch("notifications", f"id=eq.{nl[0]['id']}", {"read_at": dt.datetime.utcnow().isoformat() + "Z"})
    check("C105", "notifications: mark read (own)", s2 < 300 and upd and upd[0]["read_at"], (s2, upd))

    # ── projects lists ──
    s, mine = owner.rpc("my_projects")
    s2, joined = crew.rpc("joined_projects")
    s3, full = owner.rpc("get_project_full", p_project=pid)
    check("C106", "my_projects / joined_projects / get_project_full",
          s < 300 and any(x["project"]["id"] == pid and x["filled_slots"] == 1 and x["total_slots"] == 3 for x in mine)
          and any(j["my_match_id"] == mid for j in joined) and s3 < 300 and full["is_owner"] and len(full["team"]) == 1, (mine, joined, full and full["team"]))

    # ── export ──
    s, ex = crew.rpc("export_my_data")
    check("C107", "export_my_data returns everything the caller owns/authored",
          s < 300 and ex["profile"]["display_name"] == "Meera Kapoor" and len(ex["applications"]) >= 2
          and len(ex["messages_sent"]) >= 4 and ex["private"]["min_day_rate"] == 3000, (s, ex and list(ex)))

    # ── daily cap (25/day) ──
    cap_pid = str(uuid.uuid4())
    SERVICE_C.insert("projects", {"id": cap_pid, "owner_id": owner.uid, "payer_id": owner.uid, "title": "Cap test", "city_id": "mumbai",
                                  "area_id": "mumbai_bandra", "status": "DRAFT", "logline": "Daily cap fixture project for the e2e run."})
    cap_roles = [{"id": str(uuid.uuid4()), "project_id": cap_pid, "role_code": "PA", "comp_type": "UNPAID"} for _ in range(24)]
    SERVICE_C.insert("project_roles", cap_roles)
    SERVICE_C.patch("projects", f"id=eq.{cap_pid}", {"status": "OPEN", "published_at": dt.datetime.utcnow().isoformat() + "Z"})
    s, ins = SERVICE_C.insert("applications", [{"role_id": r["id"], "applicant_id": crew.uid} for r in cap_roles[:23]])
    resp = crew.rpc("apply_to_role", p_role=cap_roles[23]["id"])
    s2, n = crew.rpc("likes_left_today")
    check("C108", "25 applications per day cap enforced (26th → DAILY_CAP, likes_left 0)",
          s < 300 and err_code(resp) == "DAILY_CAP" and n == 0, (s, resp, n))

    # ── realtime publication ──
    pubs = run_psql("select string_agg(tablename, ',' order by tablename) from pg_publication_tables where pubname = 'supabase_realtime'")
    check("C109", "realtime publication has messages, notifications, booking_cards, applications",
          pubs is not None and all(t in pubs.split(",") for t in ("messages", "notifications", "booking_cards", "applications")), pubs)
    rls = run_psql("select count(*) from pg_tables where schemaname = 'public' and not rowsecurity")
    check("C110", "RLS enabled on every public table", rls == "0", rls)

    # ── delete account ──
    s, _ = third.rpc("delete_my_account")
    s2, u = admin("GET", f"/users/{third.uid}")
    s3, rows = SERVICE_C.select("profiles", f"id=eq.{third.uid}&select=id")
    check("C111", "delete_my_account removes the auth user and cascades the profile", s < 300 and s2 == 404 and rows == [], (s, s2, rows))

    # ── email OTP sign-in, as the app does it (needs the local mailpit inbox) ──
    otp_check()

    left_over = cleanup()
    s, rows = SERVICE_C.select("projects", f"id=in.({','.join([pid, cap_pid] + l1_projects)})&select=id")
    check("C112", "cleanup: test users deleted, their projects cascade", left_over >= 2 and rows == [], (left_over, rows))

    failed = [r for r in RESULTS if not r[2]]
    print(f"\n{len(RESULTS) - len(failed)}/{len(RESULTS)} checks passed" + ("" if not failed else f"; FAILED: {', '.join(f[0] for f in failed)}"))
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        code = main()
    finally:
        try:
            cleanup()
        except Exception:
            pass
    sys.exit(code)
