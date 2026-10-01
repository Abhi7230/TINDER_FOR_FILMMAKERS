-- ============================================================================================================
-- FIX1 · server-side contact masking, in-country remote roles, live projects + booked dates in discover_roles.
--
--  1. Contact masking (PRD §5.2). Until a match is BOOKED (or WRAPPED) a phone number, email or @handle is never
--     stored readable: messages.body and applications.note / screening_answer are stored masked
--     ("•••• shared contact hidden", the same text as domain/MessageSafety.mask) and the raw text is copied to
--     ct.message_raw / ct.application_note_raw for moderation only. Schema ct is not exposed by PostgREST and the
--     tables have no grants for anon / authenticated, so no participant can read the raw copy over REST.
--  2. Remote roles stay inside the viewer's own country (India-wide remote roles for Indian users, US-wide for US
--     users), mirroring MatchEngine G2. Guests get the browsed city's country.
--  3. discover_roles never offers a role on a project where the viewer still has business (an active match or an
--     open application / Ask), nor an on-set role that clashes with dates they're booked elsewhere
--     (cast: any shoot day booked; crew: every shoot day booked). Mirrors DemoQueries.discoverCandidates.
--  4. Reverse G4 (ct.ask_block_reason) never compares a min day rate across currencies (mirrors MatchEngine.moneyOk).
--
-- scout_candidates needs no change: it already offers remote people only from the project's own country.
-- ============================================================================================================

-- ───────────────────────────── 1. Contact masking ─────────────────────────────

-- domain/MessageSafety.mask ported to Postgres ARE: emails, then @handles (not times like "@5pm" / "@10.30"),
-- then phone numbers (10-13 digits with optional spaces or dashes, not glued to other digits).
create or replace function ct.mask_contacts(p_text text) returns text language sql immutable parallel safe as $$
  select regexp_replace(
           regexp_replace(
             regexp_replace(p_text, '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}', '•••• shared contact hidden', 'g'),
             '(?<![[:alnum:]_@])@(?![0-9]{1,2}(?:[:.][0-9]{2})?(?:am|pm|hrs?)?(?![[:alnum:]_]))[A-Za-z0-9_.]{3,30}\y',
             '•••• shared contact hidden', 'gi'),
           '(?<![0-9])(?:\+?[0-9][[:space:]-]?){9,12}[0-9](?![0-9])', '•••• shared contact hidden', 'g') $$;

-- Raw copies, moderation only. Deferred FKs: the BEFORE INSERT trigger writes the copy before the row exists.
create table if not exists ct.message_raw (
  message_id uuid primary key references public.messages (id) on delete cascade deferrable initially deferred,
  body text not null,
  created_at timestamptz not null default now()
);
create table if not exists ct.application_note_raw (
  application_id uuid primary key references public.applications (id) on delete cascade deferrable initially deferred,
  note text,
  screening_answer text,
  created_at timestamptz not null default now()
);
alter table ct.message_raw enable row level security;           -- no policies: only the table owner / service_role
alter table ct.application_note_raw enable row level security;
revoke all on ct.message_raw, ct.application_note_raw from public, anon, authenticated;
grant select on ct.message_raw, ct.application_note_raw to service_role;

-- The masked text is longer than the raw one, so the column limits move to the triggers (on the raw text):
-- body ≤ 2,000, note ≤ 140, screening answer ≤ 300, exactly as before for what people type.
alter table public.messages drop constraint if exists messages_body_check;
alter table public.messages add constraint messages_body_check check (char_length(body) <= 6000);
alter table public.applications drop constraint if exists applications_note_check;
alter table public.applications add constraint applications_note_check check (char_length(note) <= 600);
alter table public.applications drop constraint if exists applications_screening_answer_check;
alter table public.applications add constraint applications_screening_answer_check check (char_length(screening_answer) <= 1200);

-- Messages: server clock + MessageSafety flags (MONEY only when the sender is the owner side or unknown), and
-- CONTACT masked before booking. Flags are computed on the raw text, so banners still show.
create or replace function ct.trg_message_flags() returns trigger language plpgsql security definer set search_path = public, ct, pg_temp as $$
declare v_owner uuid; v_state text; v_text text; v_masked text;
begin
  if tg_op = 'INSERT' then new.created_at := now(); end if;
  if new.sender_id is null or new.kind in ('SYSTEM', 'BOOKING') then new.flags := '{}'; return new; end if;
  if tg_op = 'UPDATE' and new.body is not distinct from old.body and new.payload is not distinct from old.payload then return new; end if;
  if char_length(coalesce(new.body, '')) > 2000 then
    raise exception using message = 'Keep messages under 2,000 characters.', errcode = '23514', detail = 'BODY_LENGTH';
  end if;
  select owner_id, state into v_owner, v_state from matches where id = new.match_id;
  v_text := coalesce(new.body, '');
  if new.kind = 'AUDITION' and new.payload is not null then
    v_text := concat_ws(E'\n', v_text, new.payload #>> '{request,note}', new.payload #>> '{request,venue}', new.payload #>> '{request,address}');
  end if;
  new.flags := ct.message_flags(v_text, v_owner is null or new.sender_id = v_owner);
  if 'CONTACT' = any (new.flags) and coalesce(v_state, '') not in ('BOOKED', 'WRAPPED') then
    v_masked := ct.mask_contacts(new.body);
    if v_masked is distinct from new.body then
      insert into ct.message_raw (message_id, body) values (new.id, new.body)
      on conflict (message_id) do update set body = excluded.body, created_at = now();
      new.body := v_masked;
    end if;
  end if;
  return new;
end $$;

-- Applications: the note (Ask notes and applicants' notes) and the screening answer are masked the same way until
-- the match that came from this application is BOOKED / WRAPPED.
create or replace function ct.trg_application_text() returns trigger language plpgsql security definer set search_path = public, ct, pg_temp as $$
declare v_note text := new.note; v_answer text := new.screening_answer; v_raw_note text; v_raw_answer text;
begin
  if tg_op = 'UPDATE' and new.note is not distinct from old.note and new.screening_answer is not distinct from old.screening_answer then
    return new;
  end if;
  if char_length(coalesce(new.note, '')) > 140 and (tg_op = 'INSERT' or new.note is distinct from old.note) then
    raise exception using message = 'Keep the note to 140 characters.', errcode = '23514', detail = 'NOTE_LENGTH';
  end if;
  if char_length(coalesce(new.screening_answer, '')) > 300 and (tg_op = 'INSERT' or new.screening_answer is distinct from old.screening_answer) then
    raise exception using message = 'Keep your answer to 300 characters.', errcode = '23514', detail = 'ANSWER_LENGTH';
  end if;
  if tg_op = 'UPDATE' and exists (select 1 from matches m where m.application_id = new.id and m.state in ('BOOKED', 'WRAPPED')) then
    return new;
  end if;
  if v_note is not null and 'CONTACT' = any (ct.message_flags(v_note, false)) then
    v_note := ct.mask_contacts(v_note);
    if v_note is distinct from new.note then v_raw_note := new.note; end if;
  end if;
  if v_answer is not null and 'CONTACT' = any (ct.message_flags(v_answer, false)) then
    v_answer := ct.mask_contacts(v_answer);
    if v_answer is distinct from new.screening_answer then v_raw_answer := new.screening_answer; end if;
  end if;
  if v_raw_note is not null or v_raw_answer is not null then
    insert into ct.application_note_raw (application_id, note, screening_answer) values (new.id, v_raw_note, v_raw_answer)
    on conflict (application_id) do update set note = coalesce(excluded.note, ct.application_note_raw.note),
      screening_answer = coalesce(excluded.screening_answer, ct.application_note_raw.screening_answer), created_at = now();
    new.note := v_note;
    new.screening_answer := v_answer;
    if v_raw_note is not null and not ('CONTACT' = any (new.note_flags)) then new.note_flags := new.note_flags || 'CONTACT'::text; end if;
  end if;
  return new;
end $$;
drop trigger if exists applications_mask_contacts on public.applications;
create trigger applications_mask_contacts before insert or update of note, screening_answer on public.applications
  for each row execute function ct.trg_application_text();

-- ───────────────────────────── 2-3. discover_roles ─────────────────────────────

-- Shoot dates [p_uid] is committed to from today on: BOOKED on-set matches + their own OPEN / CREWED projects.
create or replace function ct.booked_dates(p_uid uuid) returns date[] language sql stable security definer set search_path = public, ct, pg_temp as $$
  select coalesce(array_agg(distinct x.d order by x.d), '{}'::date[]) from (
    select unnest(p.shoot_dates) as d
      from matches m join projects p on p.id = m.project_id
     where m.collaborator_id = p_uid and m.state = 'BOOKED' and not m.is_post_role
    union all
    select unnest(p.shoot_dates) from projects p where p.owner_id = p_uid and p.status in ('OPEN', 'CREWED')
  ) x where x.d >= ct.user_today(p_uid) $$;

-- Projects where [p_uid] still has business: an active match, or an application / Ask that is PENDING, on BACKUP,
-- or MATCHED with a match that is still TALKING / OFFERED / BOOKED.
create or replace function ct.live_project_ids(p_uid uuid) returns uuid[] language sql stable security definer set search_path = public, ct, pg_temp as $$
  select coalesce(array_agg(distinct x.pid), '{}'::uuid[]) from (
    select m.project_id as pid from matches m
     where m.collaborator_id = p_uid and m.state in ('TALKING', 'OFFERED', 'BOOKED')
    union all
    select r.project_id from applications a join project_roles r on r.id = a.role_id
      left join matches m on m.application_id = a.id
     where a.applicant_id = p_uid
       and (a.status in ('PENDING', 'BACKUP') or (a.status = 'MATCHED' and coalesce(m.state, 'TALKING') in ('TALKING', 'OFFERED', 'BOOKED')))
  ) x $$;

-- Pre-gate OPEN roles for a city (+ remote roles in the VIEWER's own country; guests: the browsed city's country).
-- Excludes own, swiped, applied, blocked, auto-hidden owners, past-start on-set roles, projects the viewer still has
-- business on, and on-set roles that clash with their booked dates. Gates + scoring run on the device (MatchEngine).
create or replace function public.discover_roles(p_city text default null, p_include_remote boolean default true, p_limit int default 300)
returns jsonb language plpgsql stable security definer set search_path = public, ct, pg_temp as $$
declare v_uid uuid := auth.uid(); v_city text := p_city; v_home text; v_country text;
        v_booked date[] := '{}'; v_live uuid[] := '{}';
begin
  if v_uid is not null then select city_id into v_home from profiles where id = v_uid; end if;
  if v_city is null then v_city := v_home; end if;
  if v_city is null then perform ct.fail('Pick a city first.', 'NO_CITY'); end if;
  select country into v_country from cities where id = coalesce(v_home, v_city);
  if v_uid is not null then
    v_booked := ct.booked_dates(v_uid);
    v_live := ct.live_project_ids(v_uid);
  end if;
  return coalesce((select jsonb_agg(ct.rwp_json(s.id, v_uid) order by s.published_at desc nulls last, s.id) from (
    select r.id, p.published_at
    from project_roles r join projects p on p.id = r.project_id join cities c on c.id = p.city_id
    where r.status = 'OPEN' and p.status = 'OPEN'
      and case when r.remote_ok then c.country = v_country and (p.city_id = v_city or coalesce(p_include_remote, true))
               else p.city_id = v_city end
      and (ct.is_post_role(r) or p.shoot_start is null or p.shoot_start >= ct.city_today(p.city_id))
      and not ct.auto_hidden(p.owner_id)
      and (v_uid is null or (
            p.owner_id <> v_uid
        and not ct.blocked(v_uid, p.owner_id)
        and not (p.id = any (v_live))
        and not exists (select 1 from swipes s where s.actor_id = v_uid and s.target_kind = 'ROLE' and s.target_id = r.id)
        and not exists (select 1 from applications a where a.role_id = r.id and a.applicant_id = v_uid)
        and (ct.is_post_role(r) or (r.remote_ok and r.post_deadline is not null) or cardinality(v_booked) = 0
             or cardinality(p.shoot_dates) = 0
             or case when ct.role_dept(r.role_code) = 'CAST' then not (p.shoot_dates && v_booked)
                     else not (p.shoot_dates <@ v_booked) end)))
    order by p.published_at desc nulls last
    limit greatest(1, least(coalesce(p_limit, 300), 500))) s), '[]'::jsonb);
end $$;

-- ───────────────────────────── 4. Reverse G4 across currencies ─────────────────────────────

-- Reverse gates for Scout / Ask / Rebook (PRD §3.2, §7.4): null when the person may be asked, else the reason to show.
-- Runs server-side because it needs profile_private.min_day_rate and content_wont_do, which never leave the server.
-- A min day rate is only compared with a role paid in the same currency; another currency with a min set blocks.
create or replace function ct.ask_block_reason(p_role uuid, p_profile uuid) returns text language plpgsql stable security definer set search_path = public, ct, pg_temp as $$
declare vr project_roles; vp projects; vu profiles; v_min int; v_cur text; v_first text; v_paid boolean; v_rate int;
begin
  select * into vr from project_roles where id = p_role;
  select * into vp from projects where id = vr.project_id;
  select * into vu from profiles where id = p_profile;
  if vu.id is null or vp.id is null then return 'This person isn''t available.'; end if;
  v_first := ct.first_name(vu.display_name);
  if vu.id = vp.owner_id then return 'That''s you.'; end if;
  if ct.blocked(vp.owner_id, vu.id) or ct.auto_hidden(vu.id) then return 'This person isn''t available.'; end if;
  if ct.role_dept(vr.role_code) = 'CAST' and not ct.on_keep_list(vp.owner_id, vu.id) then
    return 'Actors apply to you. You can only invite actors from your Keep list.';
  end if;
  select min_day_rate, currency into v_min, v_cur from profile_private where id = vu.id;
  v_paid := vr.comp_type in ('PAID_DAY', 'PAID_FLAT');
  v_rate := ct.day_rate(vr, vp);
  if vu.comp_mode = 'PAID_ONLY' then                                         -- reverse G4
    if not v_paid then return v_first || ' only takes paid work right now.'; end if;
    if v_min is not null and coalesce(v_cur, '') <> '' and coalesce(vr.currency, '') <> '' and upper(v_cur) <> upper(vr.currency) then
      return 'This pay is in a different currency from what ' || v_first || ' takes.';
    end if;
    if v_min is not null and v_rate is not null and v_rate < v_min then return 'This pay is below what ' || v_first || ' takes.'; end if;
  elsif vu.comp_mode = 'PAID_AND_SELECTIVE' and not v_paid then
    if not (vu.unpaid_must_haves <@ ct.role_perks(vr, vp)) then
      return v_first || ' does unpaid work only with ' || lower(replace(array_to_string(vu.unpaid_must_haves, ', '), '_', ' ')) || '.';
    end if;
  end if;
  if vp.content_flags && vu.content_wont_do then                             -- reverse G5
    return v_first || ' has ruled out some of the content on this project.';
  end if;
  if ct.role_dept(vr.role_code) in ('CAST', 'DIRECTION', 'WRITING') and cardinality(vp.languages) > 0
     and not (vp.languages && vu.languages) then                             -- reverse G7
    return v_first || ' doesn''t list this project''s language.';
  end if;
  return null;
end $$;

-- New helper functions are internal: never callable over the API.
revoke execute on function ct.mask_contacts(text), ct.booked_dates(uuid), ct.live_project_ids(uuid),
  ct.trg_application_text() from public, anon, authenticated;
