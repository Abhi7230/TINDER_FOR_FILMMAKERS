-- ============================================================================================================
-- QALIVE · fixes found by driving the real Android app in LIVE mode against a second person (qa/live_peer.py).
--
--  1. "Day N of M" counts the project's shoot dates, not the day sheets published so far. A two-day shoot with
--     only the first sheet out said "Day 1 of 1" (day sheet header, owner dashboard, DAY_SHEET notification).
--     get_shoot_days and publish_shoot_day now use ct.day_of(project, day). Mirrors PRD §8 S16V ("Day 1 of 2").
--  2. submit_feedback notifies like DemoEngine.submitFeedback: the other side gets "<name> left private feedback"
--     and, once both have answered, both get "Feedback is in". Without a notification nothing reached the first
--     submitter over Realtime, so their wrap screen kept saying "Answers unlock when … submits too".
--  3. get_match_view: for the owner, my_call_time is the next day sheet's general call (DemoQueries.matchView).
--     Owners have no call_times row of their own, so the owner's chat banner kept saying "Publish a day sheet"
--     after one was published, and never offered "Open the day sheet".
--  4. A chat message notifies the other side (kind MESSAGE), like DemoEngine.sendMessage, so it reaches the phone
--     while Calltime runs in the background. LIVE created no notification for chat messages at all.
-- ============================================================================================================

-- ───────────────────────────── 1. Day N of M ─────────────────────────────

-- {index, count} of a shoot day among the project's shoot dates. A sheet for a date no longer in shoot_dates
-- (rare: a reschedule cancels them) falls back to its place among the live sheets.
create or replace function ct.day_of(p_project uuid, p_day date) returns int[]
language sql stable security definer set search_path = public, pg_temp as $$
  with d as (select array(select distinct x from unnest(p.shoot_dates) x order by x) as days from projects p where p.id = p_project),
       s as (select count(*) filter (where x.day <= p_day)::int as before_me, count(*)::int as total from shoot_days x
              where x.project_id = p_project and x.published_at is not null and x.cancelled_at is null)
  select case when p_day = any (d.days) then array[array_position(d.days, p_day), cardinality(d.days)]
              else array[greatest(s.before_me, 1), greatest(cardinality(d.days), s.total, 1)] end
  from d, s $$;

create or replace function public.get_shoot_days(p_project uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct, pg_temp as $$
declare v_uid uuid := ct.require_uid(); v_owner boolean;
begin
  v_owner := exists (select 1 from projects where id = p_project and owner_id = v_uid);
  if not v_owner and not exists (select 1 from matches where project_id = p_project and collaborator_id = v_uid
                                 and state in ('BOOKED', 'WRAPPED') and not is_post_role) then
    return '[]'::jsonb;
  end if;
  return coalesce((select jsonb_agg(jsonb_build_object(
      'day', ct.shoot_day_json(sd), 'day_index', (ct.day_of(sd.project_id, sd.day))[1], 'day_count', (ct.day_of(sd.project_id, sd.day))[2],
      'calls', coalesce((select jsonb_agg(jsonb_build_object('call', ct.call_json(c),
                  -- collaborators see role + first name only
                  'short_name', case when v_owner then ct.short_name(u.display_name) else ct.first_name(u.display_name) end,
                  'role_code', coalesce((select r.role_code from matches m join project_roles r on r.id = m.role_id
                                         where m.project_id = sd.project_id and m.collaborator_id = c.profile_id
                                         order by m.created_at desc limit 1), u.primary_role))
                  order by c.call_time, u.display_name)
               from call_times c join profiles u on u.id = c.profile_id where c.shoot_day_id = sd.id), '[]'::jsonb),
      'my_call', (select ct.call_json(c) from call_times c where c.shoot_day_id = sd.id and c.profile_id = v_uid)) order by sd.day)
    from shoot_days sd
    where sd.project_id = p_project and sd.published_at is not null and sd.cancelled_at is null), '[]'::jsonb);
end $$;

-- Publish or update one day sheet; call times for every booked on-set person (default: general call). Notifies the team.
create or replace function public.publish_shoot_day(p_day jsonb) returns jsonb language plpgsql security definer set search_path = public, ct, pg_temp as $$
declare
  v_uid uuid := ct.require_uid(); j jsonb := coalesce(p_day, '{}'); vp projects; vd shoot_days; v_id uuid := ct.try_uuid(j ->> 'shoot_day_id');
  v_date date := (j ->> 'day')::date; v_gc time := (j ->> 'general_call')::time; v_wb time := (j ->> 'wrap_by')::time;
  v_calls jsonb := case when jsonb_typeof(j -> 'per_person_call') = 'object' then j -> 'per_person_call' else '{}'::jsonb end;
  v_new boolean; r record; v_ct time; v_idx int; v_cnt int; v_addr text := btrim(coalesce(j ->> 'address', ''));
begin
  select * into vp from projects where id = ct.try_uuid(j ->> 'project_id');
  if vp.id is null or vp.owner_id <> v_uid then perform ct.fail('Only the project owner can publish day sheets.', 'NOT_OWNER'); end if;
  if vp.status not in ('OPEN', 'CREWED') then perform ct.fail('This project isn''t active.', 'BAD_STATE'); end if;
  if v_date is null or not (v_date = any (vp.shoot_dates)) then perform ct.fail('Pick one of the shoot dates.', 'DAY'); end if;
  if v_gc is null or v_wb is null then perform ct.fail('Set the general call and wrap-by times.', 'TIMES'); end if;
  if v_addr = '' then perform ct.fail('Add the full address. Booked people need it.', 'ADDRESS'); end if;
  if char_length(coalesce(j ->> 'notes', '')) > 500 then perform ct.fail('Keep notes under 500 characters.', 'NOTES'); end if;
  if v_id is not null then select * into vd from shoot_days where id = v_id and project_id = vp.id for update; end if;
  if vd.id is null then select * into vd from shoot_days where project_id = vp.id and day = v_date and cancelled_at is null for update; end if;
  v_new := vd.id is null;
  if v_new then
    insert into shoot_days (project_id, day, general_call, wrap_by, address, maps_url, parking_note, hospital_note, meals_plan, notes, version, published_at)
    values (vp.id, v_date, v_gc, v_wb, v_addr, coalesce(nullif(j ->> 'maps_url', ''), 'https://maps.google.com/?q=' || ct.urlencode(v_addr)),
            nullif(j ->> 'parking_note', ''), nullif(j ->> 'hospital_note', ''), nullif(j ->> 'meals_plan', ''), nullif(j ->> 'notes', ''), 1, now())
    returning * into vd;
  else
    update shoot_days set day = v_date, general_call = v_gc, wrap_by = v_wb, address = v_addr,
           maps_url = coalesce(nullif(j ->> 'maps_url', ''), 'https://maps.google.com/?q=' || ct.urlencode(v_addr)),
           parking_note = nullif(j ->> 'parking_note', ''), hospital_note = nullif(j ->> 'hospital_note', ''),
           meals_plan = nullif(j ->> 'meals_plan', ''), notes = nullif(j ->> 'notes', ''), version = version + 1,
           published_at = now(), cancelled_at = null
     where id = vd.id returning * into vd;
  end if;
  select a[1], a[2] into v_idx, v_cnt from (select ct.day_of(vp.id, vd.day) as a) z;
  for r in select distinct m.collaborator_id from matches m where m.project_id = vp.id and m.state = 'BOOKED' and not m.is_post_role loop
    v_ct := coalesce((v_calls ->> r.collaborator_id::text)::time, v_gc);
    insert into call_times (shoot_day_id, profile_id, call_time) values (vd.id, r.collaborator_id, v_ct)
    on conflict (shoot_day_id, profile_id) do update set call_time = excluded.call_time, ack_at = null;      -- an update needs a new "Got it"
    perform ct.notify(r.collaborator_id, 'DAY_SHEET', case when v_new then 'Day sheet' else 'Day sheet updated' end,
      '"' || vp.title || '" · Day ' || v_idx || ' of ' || v_cnt || ' · ' || to_char(v_date, 'Dy FMDD Mon') || ' · your call ' || left(v_ct::text, 5),
      'calltime://p/' || vp.id, jsonb_build_object('project_id', vp.id, 'shoot_day_id', vd.id));
  end loop;
  return ct.shoot_day_json(vd);
end $$;

-- ───────────────────────────── 2. Feedback notifications ─────────────────────────────

create or replace function public.submit_feedback(p_match uuid, p_answers jsonb, p_note text default null) returns jsonb
language plpgsql security definer set search_path = public, ct, pg_temp as $$
declare v_uid uuid := ct.require_uid(); vm matches; v_to uuid; v_title text; v_me text;
begin
  select * into vm from matches where id = p_match;
  if vm.id is null or v_uid not in (vm.owner_id, vm.collaborator_id) then perform ct.fail('Conversation not found.', 'NOT_FOUND'); end if;
  if vm.state <> 'WRAPPED' then perform ct.fail('Feedback opens after wrap.', 'NOT_WRAPPED'); end if;
  if jsonb_typeof(p_answers) <> 'object' then perform ct.fail('Answer the questions first.', 'ANSWERS'); end if;
  if exists (select 1 from feedback where match_id = vm.id and from_id = v_uid) then perform ct.fail('You already sent feedback.', 'DUPLICATE'); end if;
  v_to := case when vm.owner_id = v_uid then vm.collaborator_id else vm.owner_id end;
  insert into feedback (match_id, from_id, to_id, answers, private_note) values (vm.id, v_uid, v_to, p_answers, left(p_note, 500));
  select title into v_title from projects where id = vm.project_id;
  if exists (select 1 from feedback where match_id = vm.id and from_id = v_to) then
    update feedback set revealed_at = now() where match_id = vm.id;
    perform ct.notify(x, 'FEEDBACK_UNLOCKED', 'Feedback is in · ' || v_title,
                      'You both answered. Track records now include this booking.', 'calltime://m/' || vm.id,
                      jsonb_build_object('match_id', vm.id))
       from unnest(array[v_uid, v_to]) x;
  else
    select ct.short_name(display_name) into v_me from profiles where id = v_uid;
    perform ct.notify(v_to, 'FEEDBACK_UNLOCKED', coalesce(v_me, 'Your counterpart') || ' left private feedback',
                      v_title || ' · answers unlock when you both submit, or in 14 days.', 'calltime://m/' || vm.id,
                      jsonb_build_object('match_id', vm.id));
  end if;
  return public.feedback_state(vm.id);
end $$;

-- ───────────────────────────── 3. Owner's next call in the match view ─────────────────────────────

create or replace function public.get_match_view(p_match uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct, pg_temp as $$
declare v_uid uuid := auth.uid(); vm matches; v_other uuid; v_lat float8; v_lng float8; v_next shoot_days;
begin
  select * into vm from matches where id = p_match;
  if not found or v_uid is null or v_uid not in (vm.owner_id, vm.collaborator_id) then return null; end if;
  v_other := case when vm.owner_id = v_uid then vm.collaborator_id else vm.owner_id end;
  select a.lat, a.lng into v_lat, v_lng from profiles me join areas a on a.id = me.area_id where me.id = v_uid;
  if vm.owner_id = v_uid or vm.state in ('BOOKED', 'WRAPPED') then
    select d.* into v_next from shoot_days d join projects p on p.id = d.project_id
     where d.project_id = vm.project_id and d.published_at is not null and d.cancelled_at is null and d.day >= ct.city_today(p.city_id)
     order by d.day limit 1;
  end if;
  return jsonb_build_object(
    'match', ct.match_json(vm), 'item', ct.rwp_json(vm.role_id, v_uid),
    'counterpart', ct.card_json(v_other, true, vm.owner_id = v_uid, v_lat, v_lng),
    'is_owner', vm.owner_id = v_uid,
    'current_card', (select ct.card_row_json(b) from booking_cards b where b.match_id = vm.id order by b.version desc limit 1),
    'accepted_card', (select ct.card_row_json(b) from booking_cards b where b.match_id = vm.id and b.status = 'ACCEPTED' order by b.version desc limit 1),
    'next_shoot_day', case when v_next.id is not null then ct.shoot_day_json(v_next) end,
    'my_call_time', case
        when vm.owner_id = v_uid then left(v_next.general_call::text, 5)          -- the owner runs the day: the general call
        else (select left(c.call_time::text, 5) from call_times c join shoot_days d on d.id = c.shoot_day_id join projects p on p.id = d.project_id
              where d.project_id = vm.project_id and c.profile_id = v_uid and d.cancelled_at is null and d.day >= ct.city_today(p.city_id)
              order by d.day limit 1) end);
end $$;

-- New helper: internal, never callable over the API.
revoke execute on function ct.day_of(uuid, date) from public, anon, authenticated;

-- ───────────────────────────── 4. Chat messages notify the other side ─────────────────────────────

-- DemoEngine.sendMessage: every text message notifies the counterpart (kind MESSAGE: kept out of the inbox's
-- Updates list by get_inbox, posted as a phone notification by SystemNotifier while Calltime is in the background).
-- LIVE inserted the message only, so a chat message never reached the phone.
create or replace function ct.trg_message_notify() returns trigger language plpgsql security definer set search_path = public, ct, pg_temp as $$
declare vm matches; v_title text; v_name text;
begin
  select * into vm from matches where id = new.match_id;
  if vm.id is null or new.sender_id is null or new.sender_id not in (vm.owner_id, vm.collaborator_id) then return null; end if;
  select title into v_title from projects where id = vm.project_id;
  select ct.short_name(display_name) into v_name from profiles where id = new.sender_id;
  perform ct.notify(case when new.sender_id = vm.owner_id then vm.collaborator_id else vm.owner_id end, 'MESSAGE',
                    coalesce(v_name, 'Someone'), 'New message about ' || coalesce(v_title, 'your project') || '.',
                    'calltime://m/' || vm.id, jsonb_build_object('match_id', vm.id, 'message_id', new.id));
  return null;
end $$;

drop trigger if exists messages_notify on public.messages;
create trigger messages_notify after insert on public.messages for each row when (new.kind = 'TEXT')
  execute function ct.trg_message_notify();

revoke execute on function ct.trg_message_notify() from public, anon, authenticated;
