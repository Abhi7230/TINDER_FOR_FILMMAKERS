#!/usr/bin/env python3
"""
Calltime LIVE (Supabase) L0 end-to-end check.

Creates 3 throwaway users through the GoTrue admin API (service key, email_confirm), signs them in with a
password to get real JWTs, then drives the whole L0 loop through PostgREST / RPC exactly as the app does:
publish (with a draft rejected by listing_rules_ok), discover, apply, applicants with rate_fit, talk → match,
chat + RLS, safety flags, booking card v1/v2, role filled closing pending apps, auditions, scout + reverse
gates, asks, privacy, blocks, export and account deletion.

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


def run_psql(sql):
    exe = shutil.which("psql") or ("/opt/homebrew/bin/psql" if os.path.exists("/opt/homebrew/bin/psql") else None)
    if not exe:
        return None
    r = subprocess.run([exe, DB_URL, "-At", "-c", sql], capture_output=True, text=True)
    return r.stdout.strip() if r.returncode == 0 else None


# ───────────────────────── the run ─────────────────────────
def main():
    print(f"Calltime LIVE e2e against {URL}\n")
    removed = cleanup()
    if removed:
        print(f"(cleaned up {removed} users from an earlier run)\n")

    owner, crew, third = make_user("owner"), make_user("crew"), make_user("third")
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

    # ── block ──
    s, deck = third.rpc("discover_roles")
    before = r_actor in role_ids(deck)
    s, _ = third.rpc("block_user", p_profile=owner.uid)
    s2, deck = third.rpc("discover_roles")
    check("C97", "block hides the blocked person's roles from the deck", before and s < 300 and r_actor not in role_ids(deck), (before, role_ids(deck)))
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
          s < 300 and mine[0]["project"]["id"] == pid and mine[0]["filled_slots"] == 1 and mine[0]["total_slots"] == 3
          and joined and joined[0]["my_match_id"] == mid and s3 < 300 and full["is_owner"] and len(full["team"]) == 1, (mine, joined, full and full["team"]))

    # ── export ──
    s, ex = crew.rpc("export_my_data")
    check("C107", "export_my_data returns everything the caller owns/authored",
          s < 300 and ex["profile"]["display_name"] == "Meera Kapoor" and len(ex["applications"]) == 2
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

    left_over = cleanup()
    s, rows = SERVICE_C.select("projects", f"id=in.({pid},{cap_pid})&select=id")
    check("C112", "cleanup: test users deleted, their projects cascade", left_over == 2 and rows == [], (left_over, rows))

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
