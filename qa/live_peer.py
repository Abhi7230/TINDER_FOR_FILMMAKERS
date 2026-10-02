#!/usr/bin/env python3
"""
live_peer.py: play "the other person" against a LIVE (Supabase) Calltime stack while a real app user drives the
Android app. Every action goes through the same REST / RPC surface the app uses (PostgREST + GoTrue), acting with
the peer's own JWT. The service key is only used to create peer users (GoTrue admin) and to look up the app user.

  python3 qa/live_peer.py otp you@calltime.test               # latest 6-digit sign-in code from mailpit
  python3 qa/live_peer.py app-user you@calltime.test          # remember the app user (uid + profile summary)
  python3 qa/live_peer.py owner-setup                         # owner peer + published project + role(s) that fit the app user
  python3 qa/live_peer.py applicants                          # who applied to the owner peer's roles
  python3 qa/live_peer.py decide you@calltime.test TALK       # TALK / BACKUP / DECLINE → match id remembered
  python3 qa/live_peer.py say "Call me on 98765 43210"        # message in the current match (as the current peer)
  python3 qa/live_peer.py messages                            # what both sides see in the current match
  python3 qa/live_peer.py send-booking [--amount 4000]        # owner peer sends the booking card (terms from the role)
  python3 qa/live_peer.py publish-shoot-day [--call 06:30]    # owner peer publishes the first shoot day's sheet
  python3 qa/live_peer.py shoot-days                          # day sheets + acks
  python3 qa/live_peer.py --as crew applicant-apply <roleId>  # crew peer: profile that fits the role, then apply
  python3 qa/live_peer.py --as crew accept-booking            # crew peer accepts the current booking card
  python3 qa/live_peer.py app-roles                           # the app user's own published roles (for flow B)
  python3 qa/live_peer.py match | inbox | state | wrap | mark-wrap KIND | confirm-wrap KIND | mark-wrapped | feedback
  python3 qa/live_peer.py reset                               # delete every peer user (peer-*@calltime.test)

Peers are users peer-<tag>@calltime.test (tag via --as, default "owner"). `--as app` acts as the app user through a
second session (GoTrue admin generate_link → /verify): for setup shortcuts only, never for what the QA checks on device. State (app user, project, roles, matches)
is kept in qa/.live_peer_state.json so consecutive commands chain. Never point this at production.
"""
import argparse
import datetime as dt
import json
import os
import re
import sys
import time
import uuid
from urllib.parse import quote

import requests

URL = os.environ.get("SUPABASE_URL", "http://127.0.0.1:54321").rstrip("/")
ANON = os.environ.get("SUPABASE_ANON_KEY",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0")
SERVICE = os.environ.get("SUPABASE_SERVICE_ROLE_KEY",
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96RAZx35lJzdJsyH-qQwv8Hdp7fsn3W0YpN81IU")
MAILPIT = os.environ.get("MAILPIT_URL", "http://127.0.0.1:54324").rstrip("/")
STATE_FILE = os.environ.get("LIVE_PEER_STATE", os.path.join(os.path.dirname(os.path.abspath(__file__)), ".live_peer_state.json"))
PEER_PREFIX, DOMAIN, PASSWORD = "peer-", "@calltime.test", "Calltime-peer-pw-1"

NAMES = {"owner": "Anjali Rao", "crew": "Vikram Shah", "crew2": "Farah Khan", "backup": "Tenzin Dorje"}


# ───────────────────────── HTTP ─────────────────────────
class ApiError(SystemExit):
    pass


def http(method, path, token=None, key=None, body=None, extra=None, ok_codes=None):
    key = key or ANON
    h = {"apikey": key, "Authorization": f"Bearer {token or key}", "Content-Type": "application/json"}
    h.update(extra or {})
    r = requests.request(method, URL + path, headers=h, data=None if body is None else json.dumps(body), timeout=30)
    try:
        data = r.json() if r.text else None
    except ValueError:
        data = r.text
    if r.status_code >= 300 and not (ok_codes and r.status_code in ok_codes):
        raise ApiError(f"{method} {path} → {r.status_code}: {json.dumps(data)[:800]}")
    return data


def admin(method, path, body=None, ok_codes=None):
    return http(method, "/auth/v1/admin" + path, SERVICE, key=SERVICE, body=body, ok_codes=ok_codes)


def svc_select(table, query):
    return http("GET", f"/rest/v1/{table}?{query}", SERVICE, key=SERVICE)


class Peer:
    def __init__(self, tag, token, uid, email):
        self.tag, self.token, self.uid, self.email = tag, token, uid, email

    def rpc(self, fn, **args):
        return http("POST", f"/rest/v1/rpc/{fn}", self.token, body=args)

    def select(self, table, query):
        return http("GET", f"/rest/v1/{table}?{query}", self.token)

    def insert(self, table, row):
        return http("POST", f"/rest/v1/{table}", self.token, body=row, extra={"Prefer": "return=representation"})


# ───────────────────────── state ─────────────────────────
def load_state():
    try:
        with open(STATE_FILE) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def save_state(st):
    with open(STATE_FILE, "w") as f:
        json.dump(st, f, indent=2, sort_keys=True)


def out(obj):
    print(json.dumps(obj, indent=2, ensure_ascii=False, default=str))


# ───────────────────────── users ─────────────────────────
def find_user(email):
    page = 1
    while True:
        data = admin("GET", f"/users?page={page}&per_page=200")
        users = (data or {}).get("users", [])
        for u in users:
            if (u.get("email") or "").lower() == email.lower():
                return u
        if len(users) < 200:
            return None
        page += 1


def app_session(st):
    """A separate session for the app user (setup shortcuts only; the device keeps its own session).
    GoTrue admin generate_link returns the one-time code without sending an email (no rate limit)."""
    email = st.get("app_email") or sys.exit("No app user yet: run `app-user <email>` first.")
    link = admin("POST", "/generate_link", {"type": "magiclink", "email": email})
    otp = link.get("email_otp") or (link.get("properties") or {}).get("email_otp")
    tok = http("POST", "/auth/v1/verify", body={"type": "email", "email": email, "token": otp})
    return Peer("app", tok["access_token"], tok["user"]["id"], email)


def peer(tag):
    if tag == "app":
        return app_session(load_state())
    email = f"{PEER_PREFIX}{tag}{DOMAIN}"
    u = find_user(email)
    if u is None:
        u = admin("POST", "/users", {"email": email, "password": PASSWORD, "email_confirm": True})
    tok = http("POST", "/auth/v1/token?grant_type=password", body={"email": email, "password": PASSWORD})
    return Peer(tag, tok["access_token"], u["id"], email)


def app_user(st, email=None):
    email = email or st.get("app_email")
    if not email:
        raise SystemExit("No app user yet: run `app-user <email>` first.")
    u = find_user(email)
    if u is None:
        raise SystemExit(f"{email} has no account yet (sign in on the device first).")
    rows = svc_select("profiles", f"id=eq.{u['id']}&select=id,display_name,primary_role,level,city_id,area_id,languages,comp_mode,"
                                  "travel_radius_km,remote_ok,availability_pattern,content_wont_do")
    priv = svc_select("profile_private", f"id=eq.{u['id']}&select=min_day_rate,currency")
    prof = rows[0] if rows else None
    if prof is not None:
        prof["busy_dates"] = [b["day"] for b in svc_select("busy_dates", f"profile_id=eq.{u['id']}&select=day")]
    return u, prof, (priv[0] if priv else None)


def base_profile(name, role, area, *, comp="PAID_AND_SELECTIVE", level="CREDITED_INDIE", langs=("hi", "en")):
    city = area.split("_")[0] if "_" in area else area
    if area.startswith("los_angeles") or area.startswith("new_york"):
        city = "_".join(area.split("_")[:2])
    return {
        "display_name": name, "primary_role": role, "level": level, "city_id": city, "area_id": area,
        "travel_radius_km": 25, "remote_ok": False, "languages": list(langs), "availability_pattern": "ANY_DAY",
        "comp_mode": comp, "unpaid_must_haves": [], "goals": ["PAID_WORK"], "content_wont_do": [],
        "secondary_roles": [], "step_up_roles": [], "work_links": [{"url": "https://vimeo.com/76979871", "kind": "REEL", "title": "Reel"}],
        "busy_dates": [], "gear": [], "bio": f"{name}: QA peer for LIVE mode.", "adult_confirmed": True,
    }


def ensure_profile(p, role, area, langs=("hi", "en"), comp="PAID_AND_SELECTIVE"):
    me = p.rpc("get_my_profile")
    if me and me.get("primary_role") == role and me.get("area_id") == area:
        return me
    return p.rpc("save_profile", p_profile=base_profile(NAMES.get(p.tag, p.tag.title() + " Peer"), role, area, comp=comp, langs=langs),
                 p_min_rate=None)


# ───────────────────────── helpers ─────────────────────────
def iso(days):
    return (dt.date.today() + dt.timedelta(days=days)).isoformat()


def current_match(st, args):
    mid = getattr(args, "match", None) or st.get("match_id")
    if not mid:
        raise SystemExit("No match yet: `decide <email> TALK` (owner peer) or pass --match <id>.")
    return mid


def short_msgs(rows):
    return [{"at": m["created_at"][11:19], "kind": m["kind"], "from": m["sender_id"][:8] if m.get("sender_id") else "system",
             "body": m.get("body"), "flags": m.get("flags")} for m in rows]


# ───────────────────────── commands ─────────────────────────
def cmd_otp(st, a):
    """Latest 6-digit code mailed to EMAIL (waits up to --wait seconds)."""
    deadline = time.time() + a.wait
    while True:
        r = requests.get(MAILPIT + "/api/v1/search?query=" + quote(f'to:"{a.email}"'), timeout=10).json()
        msgs = r.get("messages", [])
        if msgs:
            msg = requests.get(MAILPIT + f"/api/v1/message/{msgs[0]['ID']}", timeout=10).json()
            m = re.search(r"\b(\d{6})\b", msg.get("Text", "") or msg.get("HTML", ""))
            if m:
                print(m.group(1))
                return
        if time.time() > deadline:
            raise SystemExit(f"no code for {a.email} in mailpit")
        time.sleep(1)


def cmd_app_user(st, a):
    u, prof, priv = app_user(st, a.email)
    st["app_email"], st["app_uid"] = a.email, u["id"]
    save_state(st)
    out({"uid": u["id"], "email": a.email, "profile": prof, "private": priv})


def cmd_user(st, a):
    p = peer(a.tag_name)
    out({"tag": p.tag, "uid": p.uid, "email": p.email, "profile": p.rpc("get_my_profile")})


def cmd_owner_setup(st, a):
    """Owner peer with a published project whose role(s) pass the app user's gates (role, place, time, money, language)."""
    owner = peer(a.as_)
    _, prof, priv = app_user(st)
    if prof is None:
        raise SystemExit("The app user has no profile yet: finish onboarding on the device first.")
    area, city = prof["area_id"], prof["city_id"]
    langs = prof.get("languages") or ["en"]
    ensure_profile(owner, "DIRECTOR", area, langs=langs)
    cities = owner.rpc("get_cities")
    cur = next((c["currency"] for c in cities if c["id"] == city), "INR")
    floor = next((c.get("paid_floor_day") for c in cities if c["id"] == city), 1500) or 1500
    min_rate = (priv or {}).get("min_day_rate") or 0
    amount = max(a.amount or 0, min_rate + 500, floor * 3, 4000 if cur == "INR" else 300)
    busy = set(prof.get("busy_dates") or [])
    days = [d for d in (iso(n) for n in range(14, 60)) if d not in busy][:2]
    role_code = a.role or prof["primary_role"]
    pid, rid = str(uuid.uuid4()), str(uuid.uuid4())
    project = {
        "id": pid, "title": a.title, "logline": "A night-shift nurse and a lost kid cross the city before the first local train.",
        "format": "SHORT", "runtime_min": 14, "genres": ["DRAMA"], "languages": langs[:2], "stage": "PRE", "script_status": "LOCKED",
        "ambition_tier": "FESTIVAL", "city_id": city, "area_id": area, "shoot_location_type": "OUTDOOR_PUBLIC",
        "exact_address": "Gate 2, the old mill compound (QA address)", "shoot_dates": days, "date_certainty": "LOCKED",
        "hours_cap": 10, "meals": "TWO_PLUS_MEALS", "transport": "MILEAGE_OR_TA_PAID", "travel_allowance_per_day": 300 if cur == "INR" else 20,
        "lodging": False, "content_flags": [], "content_none_confirmed": True, "audition_mode": "SELF_TAPE", "bring_friend_welcome": True,
    }
    roles = [{"id": rid, "role_code": role_code, "count": 1, "comp_type": "PAID_DAY", "amount_min": amount, "amount_max": amount,
              "currency": cur, "payment_terms": "ON_WRAP", "gear_policy": "PRODUCTION_PROVIDES", "perks": ["FOOTAGE_BY_DATE"],
              "footage_due_weeks": 4, "application_cap": 25}]
    if role_code in ("ACTOR", "ACTOR_LEAD", "ACTOR_SUPPORTING"):
        roles[0]["role_details"] = {"character": "Meher, 30s, a nurse on her last shift"}
    owner.rpc("save_project_draft", p_project=project, p_roles=roles)
    owner.rpc("publish_project", p_project=pid)
    st.update({"project_id": pid, "role_id": rid, "owner_tag": owner.tag, "match_id": None})
    save_state(st)
    out({"owner": owner.email, "owner_uid": owner.uid, "project_id": pid, "role_id": rid, "role_code": role_code,
         "amount": amount, "currency": cur, "dates": days, "city": city, "area": area})


def owner_role_ids(owner, st, a):
    if getattr(a, "role_id", None):
        return [a.role_id]
    rows = owner.rpc("my_projects")
    return [r["id"] for p in rows for r in p["roles"]]


def cmd_applicants(st, a):
    owner = peer(a.as_)
    res = []
    for rid in owner_role_ids(owner, st, a):
        for row in owner.rpc("get_applicants", p_role=rid):
            app = row["application"]
            res.append({"role_id": rid, "application_id": app["id"], "applicant": row["card"]["profile"]["display_name"],
                        "applicant_id": app["applicant_id"], "status": app["status"], "note": app.get("note"), "rate_fit": row.get("rate_fit")})
    out(res)


def cmd_decide(st, a):
    owner = peer(a.as_)
    u = find_user(a.email)
    if u is None:
        raise SystemExit(f"no user {a.email}")
    for rid in owner_role_ids(owner, st, a):
        for row in owner.rpc("get_applicants", p_role=rid):
            app = row["application"]
            if app["applicant_id"] == u["id"] and app["status"] in ("PENDING", "BACKUP"):
                m = owner.rpc("decide_application", p_app=app["id"], p_decision=a.decision, p_reason=a.reason)
                if m:
                    st["match_id"] = m["id"]
                    save_state(st)
                out({"decided": a.decision, "application_id": app["id"], "match": m})
                return
    raise SystemExit(f"no PENDING application from {a.email} on {owner.email}'s roles")


def cmd_say(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    row = p.insert("messages", {"match_id": mid, "sender_id": p.uid, "kind": "TEXT", "body": a.text})
    out(short_msgs(row))


def cmd_messages(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    out(short_msgs(p.select("messages", f"match_id=eq.{mid}&select=id,created_at,kind,sender_id,body,flags&order=created_at")))


def cmd_match(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    mv = p.rpc("get_match_view", p_match=mid)
    if not mv:
        raise SystemExit("not visible to this peer")
    out({"state": mv["match"]["state"], "is_owner": mv["is_owner"], "counterpart": mv["counterpart"]["profile"]["display_name"],
         "role": mv["item"]["role"]["role_code"], "project": mv["item"]["project"]["title"],
         "current_card": mv["current_card"] and {k: mv["current_card"][k] for k in ("id", "version", "status")},
         "next_shoot_day": mv["next_shoot_day"] and mv["next_shoot_day"].get("day"), "my_call_time": mv["my_call_time"]})


def cmd_send_booking(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    mv = p.rpc("get_match_view", p_match=mid)
    r, pr = mv["item"]["role"], mv["item"]["project"]
    dates = sorted(pr["shoot_dates"])
    amount = a.amount or r.get("amount_max") or r.get("amount_min") or 3000
    terms = {
        "role_code": r["role_code"], "credit_title": r.get("credit_title") or "", "project_title": pr["title"], "is_post_role": False,
        "dates": dates, "call_window": "~07:00-17:00", "comp_type": r["comp_type"], "amount": amount, "currency": r["currency"],
        "kit_fee_day": r.get("kit_fee_day"), "payment_terms": r.get("payment_terms") or "ON_WRAP", "pay_by_date": dates[-1],
        "perks": r.get("perks") or [], "meals": pr.get("meals"), "transport": pr.get("transport"),
        "travel_allowance_per_day": pr.get("travel_allowance_per_day"), "footage_due_weeks": r.get("footage_due_weeks"),
        "deliverables": [], "payer_id": p.uid, "payer_name": "",
        "notice_pledge": "If you can't make it, tell the production at least 72 hours before.",
    }
    card = p.rpc("send_booking_card", p_match=mid, p_terms=terms)
    out({"card_id": card["id"], "version": card["version"], "status": card["status"], "amount": amount})


def cmd_accept_booking(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    mv = p.rpc("get_match_view", p_match=mid)
    card = mv.get("current_card")
    if not card or card["status"] != "SENT":
        raise SystemExit(f"no SENT booking card (current: {card and card['status']})")
    m = p.rpc("respond_booking_card", p_card=card["id"], p_accept=not a.decline)
    out({"card": card["id"], "match_state": m["state"] if isinstance(m, dict) and "state" in m else m})


def cmd_publish_shoot_day(st, a):
    p = peer(a.as_)
    pid = a.project or st.get("project_id")
    full = p.rpc("get_project_full", p_project=pid)
    days = sorted(full["project"]["shoot_dates"])
    day = a.day or days[0]
    calls = {}
    if a.call:
        for t in full.get("team", []):
            if not t["match"]["owner_id"] == t["person"]["profile"]["id"]:
                calls[t["person"]["profile"]["id"]] = a.call
    sd = p.rpc("publish_shoot_day", p_day={
        "project_id": pid, "day": day, "general_call": a.general, "wrap_by": "18:00",
        "address": a.address, "parking_note": "Two-wheeler parking at gate 2", "hospital_note": "City hospital, 2 km",
        "meals_plan": "Breakfast + lunch", "notes": "Bring a warm layer for the night exteriors.", "per_person_call": calls})
    out({"shoot_day": sd})


def cmd_shoot_days(st, a):
    p = peer(a.as_)
    pid = a.project or st.get("project_id")
    out([{"day": d["day"]["day"], "id": d["day"]["id"], "version": d["day"]["version"],
          "calls": [{"name": c["short_name"], "call": c["call"]["call_time"], "ack_at": c["call"]["ack_at"],
                     "attendance": c["call"]["attendance"]} for c in d["calls"]],
          "my_call": d.get("my_call")} for d in p.rpc("get_shoot_days", p_project=pid)])


def cmd_ack_day(st, a):
    """Collaborator peer taps "Got it" on a day sheet (default: the first one with their call)."""
    p = peer(a.as_)
    pid = a.project or st.get("project_id_b") or st.get("project_id")
    days = p.rpc("get_shoot_days", p_project=pid)
    target = next((d for d in days if d.get("my_call") and (a.day is None or d["day"]["day"] == a.day)), None)
    if not target:
        raise SystemExit("no day sheet with a call for this peer")
    p.rpc("ack_shoot_day", p_day=target["day"]["id"])
    out({"acked": target["day"]["day"], "shoot_day": target["day"]["id"]})


def cmd_app_roles(st, a):
    """The app user's published roles (service read): what a crew peer can apply to in flow B."""
    uid = st.get("app_uid") or app_user(st)[0]["id"]
    projs = svc_select("projects", f"owner_id=eq.{uid}&select=id,title,status,city_id,area_id,shoot_dates&order=created_at.desc")
    res = []
    for pr in projs:
        roles = svc_select("project_roles", f"project_id=eq.{pr['id']}&select=id,role_code,comp_type,amount_min,status,applications_count")
        res.append({**pr, "roles": roles})
    out(res)


def cmd_applicant_apply(st, a):
    p = peer(a.as_)
    rwp = p.rpc("get_role", p_role=a.role_id)
    if not rwp:
        raise SystemExit("role not visible (not OPEN?)")
    r, pr = rwp["role"], rwp["project"]
    ensure_profile(p, r["role_code"], pr["area_id"], langs=pr.get("languages") or ["en"])
    app = p.rpc("apply_to_role", p_role=a.role_id, p_note=a.note, p_link=None,
                p_answer=(a.answer or ("The quiet moments, and I'm free on the dates." if r.get("screening_question") else None)),
                p_kit=False)
    st.setdefault("applied", {})[p.tag] = {"role_id": a.role_id, "application_id": app["id"], "project_id": pr["id"]}
    st["project_id_b"] = pr["id"]
    save_state(st)
    out({"applied_as": p.email, "application": app["id"], "status": app["status"], "role": r["role_code"], "project": pr["title"]})


def cmd_find_match(st, a):
    """Latest match the current peer is in (e.g. the crew peer after the app user said Let's talk)."""
    p = peer(a.as_)
    rows = p.select("matches", f"or=(owner_id.eq.{p.uid},collaborator_id.eq.{p.uid})&select=id,state,role_id,created_at&order=created_at.desc&limit=5")
    if rows:
        st["match_id"] = rows[0]["id"]
        save_state(st)
    out(rows)


def cmd_inbox(st, a):
    p = peer(a.as_)
    ib = p.rpc("get_inbox")
    out({"conversations": [{"match": c["match"]["id"], "state": c["match"]["state"], "with": c["counterpart_name"],
                            "last": c.get("last_message"), "unread": c.get("unread")} for c in ib.get("conversations", [])],
         "updates": [{"kind": n["kind"], "title": n["title"], "body": n["body"]} for n in ib.get("updates", [])][:10]})


def cmd_wrap(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    out(p.select("wrap_items", f"match_id=eq.{mid}&select=id,kind,owed_by,status,due_date,link&order=due_date"))


def wrap_item(p, mid, kind):
    rows = p.select("wrap_items", f"match_id=eq.{mid}&kind=eq.{kind}&select=id,status")
    if not rows:
        raise SystemExit(f"no {kind} wrap item")
    return rows[0]["id"]


def cmd_mark_wrap(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    p.rpc("mark_wrap_item", p_item=wrap_item(p, mid, a.kind), p_link=a.link, p_note=None)
    cmd_wrap(st, a)


def cmd_confirm_wrap(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    p.rpc("confirm_wrap_item", p_item=wrap_item(p, mid, a.kind), p_received=not a.not_received)
    cmd_wrap(st, a)


def cmd_mark_wrapped(st, a):
    """Owner peer marks the project wrapped. --force moves the shoot dates into the past first (service, QA only)."""
    p = peer(a.as_)
    pid = a.project or st.get("project_id")
    if a.force:
        past = [iso(-3), iso(-2)]
        http("PATCH", f"/rest/v1/projects?id=eq.{pid}", SERVICE, key=SERVICE, body={"shoot_dates": past},
             extra={"Prefer": "return=minimal"})
    p.rpc("mark_wrapped", p_project=pid)
    out(p.rpc("get_project_full", p_project=pid)["project"]["status"])


def cmd_feedback(st, a):
    p = peer(a.as_)
    mid = current_match(st, a)
    mv = p.rpc("get_match_view", p_match=mid)
    ans = ({"showed_up": "YES", "prepared": "YES", "work_again": True} if mv["is_owner"]
           else {"paid": "YES", "delivered": "YES", "felt_safe": "YES", "kept": "YES", "work_again": True})
    p.rpc("submit_feedback", p_match=mid, p_answers=ans, p_note=None)
    out(p.rpc("feedback_state", p_match=mid))


def cmd_state(st, a):
    out(st)


def cmd_reset(st, a):
    n = 0
    page = 1
    while True:
        data = admin("GET", f"/users?page={page}&per_page=200")
        users = (data or {}).get("users", [])
        for u in users:
            e = (u.get("email") or "")
            if e.startswith(PEER_PREFIX) and e.endswith(DOMAIN) or (a.app and e == st.get("app_email")):
                admin("DELETE", f"/users/{u['id']}")
                n += 1
        if len(users) < 200:
            break
        page += 1
    if os.path.exists(STATE_FILE):
        os.remove(STATE_FILE)
    print(f"deleted {n} users")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--as", dest="as_", default="owner", help="peer tag: owner (default), crew, crew2, backup…")
    ap.add_argument("--match", help="match id (default: the last one remembered)")
    sub = ap.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("otp"); s.add_argument("email"); s.add_argument("--wait", type=int, default=20); s.set_defaults(f=cmd_otp)
    s = sub.add_parser("app-user"); s.add_argument("email"); s.set_defaults(f=cmd_app_user)
    s = sub.add_parser("user"); s.add_argument("tag_name"); s.set_defaults(f=cmd_user)
    s = sub.add_parser("owner-setup"); s.add_argument("--role"); s.add_argument("--amount", type=int)
    s.add_argument("--title", default="Last Local"); s.set_defaults(f=cmd_owner_setup)
    s = sub.add_parser("applicants"); s.add_argument("--role-id"); s.set_defaults(f=cmd_applicants)
    s = sub.add_parser("decide"); s.add_argument("email"); s.add_argument("decision", choices=["TALK", "BACKUP", "DECLINE"])
    s.add_argument("--reason"); s.add_argument("--role-id"); s.set_defaults(f=cmd_decide)
    s = sub.add_parser("say"); s.add_argument("text"); s.set_defaults(f=cmd_say)
    s = sub.add_parser("messages"); s.set_defaults(f=cmd_messages)
    s = sub.add_parser("match"); s.set_defaults(f=cmd_match)
    s = sub.add_parser("find-match"); s.set_defaults(f=cmd_find_match)
    s = sub.add_parser("send-booking"); s.add_argument("--amount", type=int); s.set_defaults(f=cmd_send_booking)
    s = sub.add_parser("accept-booking"); s.add_argument("--decline", action="store_true"); s.set_defaults(f=cmd_accept_booking)
    s = sub.add_parser("publish-shoot-day"); s.add_argument("--project"); s.add_argument("--day"); s.add_argument("--general", default="07:00")
    s.add_argument("--call", help="per-person call for booked people (HH:MM)")
    s.add_argument("--address", default="Gate 2, the old mill compound, Lower Parel"); s.set_defaults(f=cmd_publish_shoot_day)
    s = sub.add_parser("shoot-days"); s.add_argument("--project"); s.set_defaults(f=cmd_shoot_days)
    s = sub.add_parser("ack-day"); s.add_argument("--project"); s.add_argument("--day"); s.set_defaults(f=cmd_ack_day)
    s = sub.add_parser("app-roles"); s.set_defaults(f=cmd_app_roles)
    s = sub.add_parser("applicant-apply"); s.add_argument("role_id"); s.add_argument("--note", default="Free on both dates. Reel linked.")
    s.add_argument("--answer"); s.set_defaults(f=cmd_applicant_apply)
    s = sub.add_parser("inbox"); s.set_defaults(f=cmd_inbox)
    s = sub.add_parser("wrap"); s.set_defaults(f=cmd_wrap)
    s = sub.add_parser("mark-wrap"); s.add_argument("kind"); s.add_argument("--link"); s.set_defaults(f=cmd_mark_wrap)
    s = sub.add_parser("confirm-wrap"); s.add_argument("kind"); s.add_argument("--not-received", action="store_true"); s.set_defaults(f=cmd_confirm_wrap)
    s = sub.add_parser("mark-wrapped"); s.add_argument("--project"); s.add_argument("--force", action="store_true"); s.set_defaults(f=cmd_mark_wrapped)
    s = sub.add_parser("feedback"); s.set_defaults(f=cmd_feedback)
    s = sub.add_parser("state"); s.set_defaults(f=cmd_state)
    s = sub.add_parser("reset"); s.add_argument("--app", action="store_true", help="also delete the app user"); s.set_defaults(f=cmd_reset)

    a = ap.parse_args()
    st = load_state()
    a.f(st, a)


if __name__ == "__main__":
    main()
