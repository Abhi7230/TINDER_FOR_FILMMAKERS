-- ============================================================================================================
-- Calltime · Supabase schema (PRD §9). One migration creates EVERY table now so the schema is frozen; the
-- L0 / L1 tiers (PRD §4.2) only decide which RPCs the app wires up.
--
-- Conventions (PRD §9.1)
--  * Enums are text + check constraints. Values == Kotlin enum names (model/Enums.kt, model/Taxonomy.kt).
--  * uuid ids (gen_random_uuid), text ids for reference tables (cities, areas).
--  * RLS is enabled on every table. State transitions happen only through SECURITY DEFINER RPCs in `public`
--    that check auth.uid(). Direct writes are limited to the caller's own simple rows (+ column grants).
--  * Internal helpers live in schema `ct` (not exposed by PostgREST; only `public` is).
--  * RPC JSON uses snake_case keys == column names; timestamps are epoch millis; dates are 'YYYY-MM-DD'.
--  * Errors raised for users: SQLSTATE P0001, message = text fit to show the user, detail = machine code.
-- ============================================================================================================

set check_function_bodies = on;

create extension if not exists fuzzystrmatch with schema extensions;

create schema if not exists ct;
grant usage on schema ct to anon, authenticated, service_role;

-- ───────────────────────────────────────── 1. Enum value lists ─────────────────────────────────────────

create function ct.e_role() returns text[] language sql immutable parallel safe as $$ select array[
  'DIRECTOR','WRITER','PRODUCER','FIRST_AD','SECOND_AD','SCRIPT_SUPERVISOR','PA',
  'DP','CAMERA_OP','FIRST_AC','GAFFER','GRIP','SOUND_MIXER','BOOM_OP',
  'PRODUCTION_DESIGNER','ART_ASSISTANT','COSTUME','HMU','ACTOR','VOICE_ACTOR',
  'EDITOR','COLORIST','VFX_ARTIST','SOUND_DESIGNER','COMPOSER','BTS_STILLS',
  'INTIMACY_COORDINATOR','STUNT_COORDINATOR','LOCATION_MANAGER']::text[] $$;
create function ct.e_level() returns text[] language sql immutable parallel safe as $$ select '{FIRST_PROJECT,BUILDING_REEL,CREDITED_INDIE,WORKING_PRO}'::text[] $$;
create function ct.e_comp_mode() returns text[] language sql immutable parallel safe as $$ select '{PAID_ONLY,PAID_AND_SELECTIVE,OPEN_TO_UNPAID}'::text[] $$;
create function ct.e_comp_type() returns text[] language sql immutable parallel safe as $$ select '{PAID_DAY,PAID_FLAT,STIPEND,UNPAID}'::text[] $$;
create function ct.e_terms() returns text[] language sql immutable parallel safe as $$ select '{ON_WRAP,HALF_UPFRONT,WITHIN_7_DAYS,WITHIN_15_DAYS,WITHIN_30_DAYS}'::text[] $$;
create function ct.e_perk() returns text[] language sql immutable parallel safe as $$ select '{TRAVEL_COVERED,LODGING,FOOTAGE_BY_DATE,FINAL_FILM_BY_DATE,STEP_UP_CREDIT,MEALS,SHORT_COMMITMENT,SCRIPT_READY}'::text[] $$;
create function ct.e_goal() returns text[] language sql immutable parallel safe as $$ select '{PAID_WORK,REEL_FOOTAGE,STEP_UP,FESTIVAL_CREDIT,BUILD_TEAM,FUN_COMMUNITY}'::text[] $$;
create function ct.e_avail() returns text[] language sql immutable parallel safe as $$ select '{ANY_DAY,WEEKENDS_ONLY,EVENINGS_AND_WEEKENDS,WEEKDAYS_ONLY}'::text[] $$;
create function ct.e_format() returns text[] language sql immutable parallel safe as $$ select '{SHORT,FEATURE,SERIES_PILOT,MUSIC_VIDEO,SPEC_AD,STUDENT_THESIS,CHALLENGE_48H,VERTICAL,DOCUMENTARY}'::text[] $$;
create function ct.e_stage() returns text[] language sql immutable parallel safe as $$ select '{DEVELOPMENT,PRE,PRODUCTION,POST}'::text[] $$;
create function ct.e_script() returns text[] language sql immutable parallel safe as $$ select '{IDEA,DRAFT,LOCKED}'::text[] $$;
create function ct.e_ambition() returns text[] language sql immutable parallel safe as $$ select '{FOR_FUN,PORTFOLIO,FESTIVAL,DISTRIBUTION,CLIENT_PAID}'::text[] $$;
create function ct.e_location() returns text[] language sql immutable parallel safe as $$ select '{STUDIO,OUTDOOR_PUBLIC,PRIVATE_HOME,OFFICE_COMMERCIAL,MIXED}'::text[] $$;
create function ct.e_certainty() returns text[] language sql immutable parallel safe as $$ select '{LOCKED,TENTATIVE}'::text[] $$;
create function ct.e_meals() returns text[] language sql immutable parallel safe as $$ select '{NONE,CRAFTY_ONLY,ONE_MEAL,TWO_PLUS_MEALS,CATERED}'::text[] $$;
create function ct.e_transport() returns text[] language sql immutable parallel safe as $$ select '{NONE,CARPOOL_COORDINATED,MILEAGE_OR_TA_PAID,PRODUCTION_VAN}'::text[] $$;
create function ct.e_content() returns text[] language sql immutable parallel safe as $$ select '{NUDITY,SIMULATED_SEX,INTIMACY_SCENE,STUNTS_FIGHTS,FIREARMS_PROPS,MINORS_ON_SET,NIGHT_SHOOT,WATER,ANIMALS,GORE}'::text[] $$;
create function ct.e_audition() returns text[] language sql immutable parallel safe as $$ select '{SELF_TAPE,VIDEO_CALL,STUDIO_OR_PUBLIC_WITH_OTHERS}'::text[] $$;
create function ct.e_gear() returns text[] language sql immutable parallel safe as $$ select '{PRODUCTION_PROVIDES,BYO_WITH_KIT_FEE,BYO_NO_FEE,NA}'::text[] $$;
create function ct.e_yna() returns text[] language sql immutable parallel safe as $$ select '{YES,NO,NOT_YET,NA}'::text[] $$;
create function ct.e_project_status() returns text[] language sql immutable parallel safe as $$ select '{DRAFT,OPEN,CREWED,WRAPPED,CANCELLED}'::text[] $$;
create function ct.e_role_status() returns text[] language sql immutable parallel safe as $$ select '{OPEN,FILLED,CLOSED}'::text[] $$;
create function ct.e_link() returns text[] language sql immutable parallel safe as $$ select '{REEL,CREDIT,HEADSHOT,PORTFOLIO,IMDB,OTHER}'::text[] $$;
create function ct.e_swipe() returns text[] language sql immutable parallel safe as $$ select '{RIGHT,LEFT,SAVE,BACKUP}'::text[] $$;
create function ct.e_origin() returns text[] language sql immutable parallel safe as $$ select '{APPLIED,INVITED}'::text[] $$;
create function ct.e_app_status() returns text[] language sql immutable parallel safe as $$ select '{PENDING,MATCHED,BACKUP,DECLINED,WITHDRAWN,CLOSED,EXPIRED}'::text[] $$;
create function ct.e_match_state() returns text[] language sql immutable parallel safe as $$ select '{TALKING,OFFERED,BOOKED,WRAPPED,WITHDRAWN,RELEASED,NO_SHOW,CLOSED}'::text[] $$;
create function ct.e_booking_status() returns text[] language sql immutable parallel safe as $$ select '{SENT,ACCEPTED,DECLINED,SUPERSEDED,CANCELLED}'::text[] $$;
create function ct.e_msg_kind() returns text[] language sql immutable parallel safe as $$ select '{TEXT,SYSTEM,BOOKING,AUDITION}'::text[] $$;
create function ct.e_msg_flag() returns text[] language sql immutable parallel safe as $$ select '{CONTACT,OFF_PLATFORM,MONEY_FROM_TALENT,BODY_REQUEST,PRIVATE_MEETING}'::text[] $$;
create function ct.e_aud_response() returns text[] language sql immutable parallel safe as $$ select '{WILL_DO,CANT_MAKE_IT,TAPE_SENT}'::text[] $$;
create function ct.e_wrap_kind() returns text[] language sql immutable parallel safe as $$ select '{PAID,FOOTAGE,FINAL_FILM,CREDIT,DELIVERY}'::text[] $$;
create function ct.e_owed_by() returns text[] language sql immutable parallel safe as $$ select '{PRODUCTION,COLLABORATOR}'::text[] $$;
create function ct.e_wrap_status() returns text[] language sql immutable parallel safe as $$ select '{OPEN,DONE,CONFIRMED,NOT_RECEIVED}'::text[] $$;
create function ct.e_attendance() returns text[] language sql immutable parallel safe as $$ select '{CONFIRMED,CANT_MAKE_IT,ATTENDED,NO_SHOW,DISPUTED}'::text[] $$;
create function ct.e_withdraw() returns text[] language sql immutable parallel safe as $$ select '{DATES_CHANGED,PAID_WORK,HEALTH_FAMILY,TERMS_CHANGED,SAFETY,OTHER}'::text[] $$;
create function ct.e_release() returns text[] language sql immutable parallel safe as $$ select '{ROLE_CUT,SCHEDULE_CHANGED,FOUND_SOMEONE_ELSE,PROJECT_CANCELLED,CONDUCT,OTHER}'::text[] $$;
create function ct.e_ask_decline() returns text[] language sql immutable parallel safe as $$ select '{DATES_DONT_WORK,TOO_FAR,NOT_MY_KIND,PAID_ONLY_NOW}'::text[] $$;
create function ct.e_report_target() returns text[] language sql immutable parallel safe as $$ select '{PROFILE,PROJECT,ROLE,MESSAGE,BOOKING}'::text[] $$;
create function ct.e_report_reason() returns text[] language sql immutable parallel safe as $$ select '{MONEY_REQUEST,SEXUAL_INAPPROPRIATE,FAKE_IMPERSONATION,UNSAFE_SET,HARASSMENT,NOT_PAID,SPAM,OTHER}'::text[] $$;
create function ct.e_report_status() returns text[] language sql immutable parallel safe as $$ select '{RECEIVED,REVIEWING,ACTION_TAKEN,NO_ACTION}'::text[] $$;
create function ct.e_notif_kind() returns text[] language sql immutable parallel safe as $$ select array[
  'APPLICATION_RECEIVED','APPLICATION_CLOSED','APPLICATION_BACKUP','ASK_RECEIVED','MATCH_CREATED','MESSAGE',
  'BOOKING_CARD','BOOKING_ACCEPTED','BOOKING_CHANGED','DAY_SHEET','STILL_COMING','WRAP_ITEM','WRAP_OVERDUE',
  'FEEDBACK_UNLOCKED','WITHDRAWAL','RELEASE','PROJECT_CHANGED','REPORT_UPDATE','SYSTEM']::text[] $$;

-- ───────────────────────────────────────── 2. Pure helpers ─────────────────────────────────────────

-- Taxonomy (PRD Appendix A). Mirrors model/Taxonomy.kt.
create function ct.role_dept(p_code text) returns text language sql immutable parallel safe as $$
  select case p_code
    when 'DIRECTOR' then 'DIRECTION' when 'WRITER' then 'WRITING' when 'PRODUCER' then 'PRODUCTION'
    when 'FIRST_AD' then 'DIRECTION' when 'SECOND_AD' then 'DIRECTION' when 'SCRIPT_SUPERVISOR' then 'DIRECTION'
    when 'PA' then 'PRODUCTION' when 'DP' then 'CAMERA' when 'CAMERA_OP' then 'CAMERA' when 'FIRST_AC' then 'CAMERA'
    when 'GAFFER' then 'GE' when 'GRIP' then 'GE' when 'SOUND_MIXER' then 'SOUND' when 'BOOM_OP' then 'SOUND'
    when 'PRODUCTION_DESIGNER' then 'ART' when 'ART_ASSISTANT' then 'ART' when 'COSTUME' then 'ART' when 'HMU' then 'ART'
    when 'ACTOR' then 'CAST' when 'VOICE_ACTOR' then 'CAST'
    when 'EDITOR' then 'POST' when 'COLORIST' then 'POST' when 'VFX_ARTIST' then 'POST' when 'SOUND_DESIGNER' then 'POST'
    when 'COMPOSER' then 'MUSIC' when 'BTS_STILLS' then 'OTHER'
    when 'INTIMACY_COORDINATOR' then 'SAFETY' when 'STUNT_COORDINATOR' then 'SAFETY' when 'LOCATION_MANAGER' then 'PRODUCTION'
  end $$;

create function ct.role_scarcity(p_code text) returns text language sql immutable parallel safe as $$
  select case
    when p_code in ('PRODUCER','FIRST_AD','GAFFER','SOUND_MIXER','BOOM_OP','INTIMACY_COORDINATOR','STUNT_COORDINATOR','LOCATION_MANAGER') then 'SCARCE'
    when p_code in ('DIRECTOR','WRITER','PA','ACTOR','COMPOSER','BTS_STILLS') then 'OVERSUPPLIED'
    else 'BALANCED' end $$;

create function ct.default_cap(p_code text) returns int language sql immutable parallel safe as $$
  select case when p_code = 'ACTOR' then 20
    else case ct.role_scarcity(p_code) when 'SCARCE' then 25 when 'BALANCED' then 15 else 12 end end $$;

create function ct.role_label(p_code text) returns text language sql immutable parallel safe as $$
  select case p_code
    when 'DIRECTOR' then 'Director' when 'WRITER' then 'Writer' when 'PRODUCER' then 'Producer'
    when 'FIRST_AD' then '1st Assistant Director' when 'SECOND_AD' then '2nd Assistant Director'
    when 'SCRIPT_SUPERVISOR' then 'Script supervisor' when 'PA' then 'Production assistant'
    when 'DP' then 'Director of photography' when 'CAMERA_OP' then 'Camera operator' when 'FIRST_AC' then '1st AC / focus puller'
    when 'GAFFER' then 'Gaffer' when 'GRIP' then 'Grip' when 'SOUND_MIXER' then 'Production sound mixer' when 'BOOM_OP' then 'Boom operator'
    when 'PRODUCTION_DESIGNER' then 'Production designer' when 'ART_ASSISTANT' then 'Art assistant'
    when 'COSTUME' then 'Costume / wardrobe' when 'HMU' then 'Hair & makeup' when 'ACTOR' then 'Actor' when 'VOICE_ACTOR' then 'Voice actor'
    when 'EDITOR' then 'Editor' when 'COLORIST' then 'Colorist' when 'VFX_ARTIST' then 'VFX artist'
    when 'SOUND_DESIGNER' then 'Sound designer' when 'COMPOSER' then 'Composer' when 'BTS_STILLS' then 'BTS / stills'
    when 'INTIMACY_COORDINATOR' then 'Intimacy coordinator' when 'STUNT_COORDINATOR' then 'Fight / stunt coordinator'
    when 'LOCATION_MANAGER' then 'Locations' else coalesce(p_code, '') end $$;

create function ct.role_credit(p_code text) returns text language sql immutable parallel safe as $$
  select case p_code
    when 'WRITER' then 'Written by' when 'DP' then 'Director of Photography' when 'CAMERA_OP' then 'Camera Operator'
    when 'FIRST_AC' then '1st Assistant Camera' when 'SOUND_MIXER' then 'Sound Recordist' when 'BOOM_OP' then 'Boom Operator'
    when 'PA' then 'Production Assistant' when 'SCRIPT_SUPERVISOR' then 'Script Supervisor' when 'ACTOR' then 'Cast'
    when 'VOICE_ACTOR' then 'Voice' when 'VFX_ARTIST' then 'Visual Effects' when 'SOUND_DESIGNER' then 'Sound Design'
    when 'COMPOSER' then 'Music by' when 'BTS_STILLS' then 'Stills' when 'PRODUCTION_DESIGNER' then 'Production Designer'
    when 'ART_ASSISTANT' then 'Art Assistant' when 'INTIMACY_COORDINATOR' then 'Intimacy Coordinator'
    when 'STUNT_COORDINATOR' then 'Stunt Coordinator' when 'HMU' then 'Hair & Makeup'
    else ct.role_label(p_code) end $$;

-- NONE < CRAFTY_ONLY < ONE_MEAL < TWO_PLUS_MEALS < CATERED
create function ct.meals_rank(p_meals text) returns int language sql immutable parallel safe as $$
  select coalesce(array_position(ct.e_meals(), p_meals) - 1, -1) $$;

create function ct.ms(p_ts timestamptz) returns bigint language sql immutable parallel safe as $$
  select (extract(epoch from p_ts) * 1000)::bigint $$;

create function ct.km(a1 float8, o1 float8, a2 float8, o2 float8) returns float8 language sql immutable parallel safe as $$
  select 2 * 6371 * asin(least(1.0, sqrt(power(sin(radians(a2 - a1) / 2), 2)
         + cos(radians(a1)) * cos(radians(a2)) * power(sin(radians(o2 - o1) / 2), 2)))) $$;

create function ct.jarr(p_j jsonb, p_default text[] default '{}') returns text[] language sql immutable parallel safe as $$
  select case when p_j is null or jsonb_typeof(p_j) <> 'array' then p_default
              else array(select x from jsonb_array_elements_text(p_j) x where x is not null and btrim(x) <> '') end $$;

create function ct.jdates(p_j jsonb) returns date[] language sql immutable parallel safe as $$
  select case when p_j is null or jsonb_typeof(p_j) <> 'array' then '{}'::date[]
              else array(select x::date from jsonb_array_elements_text(p_j) x where x is not null and x <> '') end $$;

create function ct.try_uuid(p_t text) returns uuid language plpgsql immutable parallel safe as $$
begin return p_t::uuid; exception when others then return null; end $$;

create function ct.short_name(p_name text) returns text language sql immutable parallel safe as $$
  select case when cardinality(w) >= 2 then w[1] || ' ' || left(w[cardinality(w)], 1) || '.' else btrim(coalesce(p_name, '')) end
  from (select regexp_split_to_array(btrim(coalesce(p_name, '')), '\s+') w) s $$;

create function ct.first_name(p_name text) returns text language sql immutable parallel safe as $$
  select split_part(btrim(coalesce(p_name, '')), ' ', 1) $$;

create function ct.initials(p_name text) returns text language sql immutable parallel safe as $$
  select upper(left(coalesce(w[1], ''), 1) || left(coalesce(w[2], ''), 1))
  from (select regexp_split_to_array(btrim(coalesce(p_name, '')), '\s+') w) s $$;

create function ct.fail(p_msg text, p_code text default 'ERROR') returns void language plpgsql as $$
begin raise exception using message = p_msg, errcode = 'P0001', detail = p_code; end $$;

-- True for the service_role key, or for an admin session (psql as postgres) with no JWT at all.
create function ct.is_service() returns boolean language sql stable as $$
  select coalesce(auth.role(), '') = 'service_role'
      or (auth.role() is null and session_user in ('postgres', 'supabase_admin')) $$;

-- MessageSafety (PRD §5.2, Appendix C) ported to Postgres ARE (\y = word boundary). Case-insensitive.
-- p_money = false suppresses MONEY_FROM_TALENT (a collaborator asking "paid via UPI?" is not a scam).
create function ct.message_flags(p_text text, p_money boolean default true) returns text[] language plpgsql immutable parallel safe as $$
declare t text := coalesce(p_text, ''); f text[] := '{}';
begin
  if btrim(t) = '' then return f; end if;
  if t ~ '(?:\+?\d[\s-]?){10,13}'
     or t ~ '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
     or t ~ '(?<![\w@])@[A-Za-z0-9_.]{3,30}\y' then f := f || 'CONTACT'::text; end if;
  if t ~* '\y(whats\s?app|wa\.me|telegram|t\.me|signal app|insta(gram)?\s*(dm|id)|dm me on|text me|call me|email me|gmail|my number|number (do|bhejo|send)|call karo|whatsapp karo)\y'
     then f := f || 'OFF_PLATFORM'::text; end if;
  if p_money and (
        t ~* '\y(registration|security|portfolio|audition|processing|joining|membership|profile)\s*(fee|fees|charges?|deposit|amount)\y'
     or t ~* '\y(pay|send|transfer|deposit|bharna|bharni|jama)\y.{0,24}(₹|\yrs\.?|\yinr\y|\$|usd|£|\yupi\y|zelle|paypal|venmo|gpay|paytm|phonepe)'
     or t ~* '\ypaise\s*(bhejo|do|dena)\y'
     or t ~* '\yfees?\s*(bharni|bharna|jama)\y'
     or t ~* '\ypay\s+to\s+audition\y') then f := f || 'MONEY_FROM_TALENT'::text; end if;
  if t ~* '\y(bikini|swimsuit|lingerie|bold\s*(photos?|pics?|shoot)|measurements?|bra\s*size|body\s*(pics?|photos?)|photos?\s*bhejo)\y'
     then f := f || 'BODY_REQUEST'::text; end if;
  if t ~* '\y(my\s*(place|flat|apartment|room)|hotel\s*room|come\s*alone|ghar\s*aa\s*jao)\y'
     then f := f || 'PRIVATE_MEETING'::text; end if;
  return f;
end $$;

create function ct.money(p_n int, p_cur text) returns text language sql immutable parallel safe as $$
  select case upper(coalesce(p_cur, '')) when 'INR' then '₹' when 'USD' then '$' when 'GBP' then '£' else upper(coalesce(p_cur, '')) || ' ' end
         || to_char(coalesce(p_n, 0), 'FM999,999,999') $$;

create function ct.terms_label(p_t text) returns text language sql immutable parallel safe as $$
  select case p_t when 'ON_WRAP' then 'paid on wrap' when 'HALF_UPFRONT' then 'half upfront, rest on wrap'
    when 'WITHIN_7_DAYS' then 'paid within 7 days' when 'WITHIN_15_DAYS' then 'paid within 15 days'
    when 'WITHIN_30_DAYS' then 'paid within 30 days' else '' end $$;

create function ct.audition_label(p_m text) returns text language sql immutable parallel safe as $$
  select case p_m when 'SELF_TAPE' then 'Self-tape' when 'VIDEO_CALL' then 'Video call'
    when 'STUDIO_OR_PUBLIC_WITH_OTHERS' then 'Studio or public space, with others present' else coalesce(p_m, '') end $$;

-- ───────────────────────────────────────── 3. Tables ─────────────────────────────────────────

-- Reference
create table public.cities (
  id text primary key check (id ~ '^[a-z0-9_]+$'),
  name text not null,
  country text not null check (country in ('IN', 'US', 'GB')),
  currency text not null check (currency in ('INR', 'USD', 'GBP')),
  lat float8 not null,
  lng float8 not null,
  paid_floor_day int not null default 0 check (paid_floor_day >= 0),
  emergency_number text not null,
  tz text not null default 'UTC',
  is_live boolean not null default false,
  is_early boolean not null default false,
  sort int not null default 0
);

create table public.areas (
  id text primary key check (id ~ '^[a-z0-9_]+$'),
  city_id text not null references public.cities (id) on delete cascade,
  name text not null,
  lat float8 not null,
  lng float8 not null,
  sort int not null default 0,
  unique (id, city_id)
);

-- People
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (char_length(btrim(display_name)) between 2 and 40),
  pronouns text check (char_length(pronouns) <= 30),
  primary_role text not null check (primary_role = any (ct.e_role())),
  level text not null check (level = any (ct.e_level())),
  city_id text not null references public.cities (id),
  area_id text not null,
  travel_radius_km int not null default 15 check (travel_radius_km between 2 and 150),
  travel_if_covered boolean not null default false,
  remote_ok boolean not null default false,
  languages text[] not null default '{en}' check (cardinality(languages) >= 1),
  availability_pattern text not null default 'ANY_DAY' check (availability_pattern = any (ct.e_avail())),
  comp_mode text not null default 'PAID_AND_SELECTIVE' check (comp_mode = any (ct.e_comp_mode())),
  unpaid_must_haves text[] not null default '{}' check (unpaid_must_haves <@ ct.e_perk()),
  goals text[] not null default '{}' check (goals <@ ct.e_goal()),
  content_wont_do text[] not null default '{}' check (content_wont_do <@ ct.e_content()),
  gear text[] not null default '{}',
  kit_fee_day int check (kit_fee_day >= 0),
  tools text[] not null default '{}',
  taste_films text[] not null default '{}' check (cardinality(taste_films) <= 5),
  genres_pref text[] not null default '{}',
  formats_pref text[] not null default '{}' check (formats_pref <@ ct.e_format()),
  bio text check (char_length(bio) <= 280),
  affiliations text[] not null default '{}',
  email_verified boolean not null default false,
  phone_verified boolean not null default false,
  id_verified boolean not null default false,
  adult_confirmed boolean not null default false,
  created_at timestamptz not null default now(),
  last_active_at timestamptz not null default now(),
  foreign key (area_id, city_id) references public.areas (id, city_id)
  -- no avatar column in the MVP
);

create table public.profile_private (            -- owner-only; min_day_rate never leaves the server except to its owner
  id uuid primary key references public.profiles (id) on delete cascade,
  min_day_rate int check (min_day_rate >= 0),
  currency text check (currency in ('INR', 'USD', 'GBP'))
);

create table public.profile_roles (
  profile_id uuid not null references public.profiles (id) on delete cascade,
  role_code text not null check (role_code = any (ct.e_role())),
  kind text not null check (kind in ('PRIMARY', 'SECONDARY', 'STEP_UP')),
  proficiency int check (proficiency between 1 and 3),
  primary key (profile_id, role_code, kind)
);

create table public.work_links (
  id uuid primary key default gen_random_uuid(),
  profile_id uuid not null references public.profiles (id) on delete cascade,
  role_code text check (role_code = any (ct.e_role())),
  kind text not null check (kind = any (ct.e_link())),
  url text not null check (url ~* '^https?://' and char_length(url) <= 500),
  title text check (char_length(title) <= 120),
  year int check (year between 1900 and 2100),
  sort int not null default 0
);

create table public.busy_dates (
  profile_id uuid not null references public.profiles (id) on delete cascade,
  day date not null,
  primary key (profile_id, day)
);

-- Projects
create table public.projects (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id) on delete cascade,
  payer_id uuid not null references public.profiles (id) on delete cascade,    -- MVP: = owner_id
  title text not null default '' check (char_length(title) <= 60),           -- 2..60 enforced at publish (R18)
  logline text not null default '' check (char_length(logline) <= 200),      -- 20..200 enforced at publish (R18)
  format text not null default 'SHORT' check (format = any (ct.e_format())),
  runtime_min int not null default 10 check (runtime_min between 1 and 240),
  page_count int check (page_count between 0 and 1000),
  genres text[] not null default '{}' check (cardinality(genres) <= 3),
  languages text[] not null default '{en}',
  stage text not null default 'PRE' check (stage = any (ct.e_stage())),
  script_status text not null default 'DRAFT' check (script_status = any (ct.e_script())),
  script_readable boolean not null default false,
  lookbook_url text check (char_length(lookbook_url) <= 500),
  ambition_tier text not null default 'PORTFOLIO' check (ambition_tier = any (ct.e_ambition())),
  honest_zero_budget boolean not null default false,
  city_id text not null references public.cities (id),
  area_id text not null,
  shoot_location_type text check (shoot_location_type = any (ct.e_location())),
  exact_address text check (char_length(exact_address) <= 300),              -- owner + BOOKED people only (via RPC)
  story_setting text check (char_length(story_setting) <= 80),
  shoot_dates date[] not null default '{}',                                   -- AUTHORITATIVE exact shoot dates
  shoot_start date,                                                           -- derived from shoot_dates by trigger
  shoot_end date,                                                             -- derived
  shoot_days int,                                                             -- derived (null on post-only projects)
  picture_lock_date date,
  date_certainty text check (date_certainty = any (ct.e_certainty())),
  hours_cap int check (hours_cap between 1 and 24),                           -- 2..16 enforced at publish (R05)
  meals text check (meals = any (ct.e_meals())),
  transport text check (transport = any (ct.e_transport())),
  travel_allowance_per_day int check (travel_allowance_per_day >= 0),
  lodging boolean,
  content_flags text[] not null default '{}' check (content_flags <@ ct.e_content()),
  content_none_confirmed boolean not null default false,
  intimacy_coordinator boolean,
  stunt_or_armourer boolean,
  guardian_present boolean,
  safety_measures text check (char_length(safety_measures) <= 400),
  audition_mode text check (audition_mode = any (ct.e_audition())),
  bring_friend_welcome boolean not null default false,
  insurance text check (insurance = any (ct.e_yna())),
  permits text check (permits = any (ct.e_yna())),
  status text not null default 'DRAFT' check (status = any (ct.e_project_status())),
  reschedule_count int not null default 0,
  created_at timestamptz not null default now(),
  published_at timestamptz,
  updated_at timestamptz not null default now(),
  foreign key (area_id, city_id) references public.areas (id, city_id)
);

create table public.project_roles (
  id uuid primary key default gen_random_uuid(),
  project_id uuid not null references public.projects (id) on delete cascade,
  role_code text not null check (role_code = any (ct.e_role())),
  count int not null default 1 check (count between 1 and 10),
  credit_title text not null default '' check (char_length(credit_title) <= 60),
  scope_includes text[] not null default '{}',
  scope_excludes text[] not null default '{}',
  step_up_friendly boolean not null default false,
  level_wanted text check (level_wanted = any (ct.e_level())),
  comp_type text not null check (comp_type = any (ct.e_comp_type())),
  amount_min int check (amount_min >= 0),
  amount_max int check (amount_max >= 0),
  currency text not null default 'INR' check (currency in ('INR', 'USD', 'GBP')),
  kit_fee_day int check (kit_fee_day >= 0),
  payment_terms text check (payment_terms = any (ct.e_terms())),
  perks text[] not null default '{}' check (perks <@ ct.e_perk()),
  footage_due_weeks int check (footage_due_weeks between 1 and 52),
  final_film_due_weeks int check (final_film_due_weeks between 1 and 52),
  gear_policy text check (gear_policy = any (ct.e_gear())),
  remote_ok boolean not null default false,
  post_deadline date,
  role_details jsonb not null default '{}' check (jsonb_typeof(role_details) = 'object'),
  screening_question text check (char_length(screening_question) <= 140),
  application_cap int not null default 15 check (application_cap between 5 and 40),
  applications_count int not null default 0,
  filled_count int not null default 0,
  status text not null default 'OPEN' check (status = any (ct.e_role_status())),
  sort int not null default 0,
  created_at timestamptz not null default now()
);

-- Matching
create table public.swipes (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid not null references public.profiles (id) on delete cascade,
  target_kind text not null check (target_kind in ('ROLE', 'PROFILE')),
  target_id uuid not null,
  context_role_id uuid,
  direction text not null check (direction = any (ct.e_swipe())),
  created_at timestamptz not null default now()
);
create unique index swipes_unique on public.swipes (actor_id, target_kind, target_id, coalesce(context_role_id, '00000000-0000-0000-0000-000000000000'::uuid));

create table public.applications (
  id uuid primary key default gen_random_uuid(),
  role_id uuid not null references public.project_roles (id) on delete cascade,
  applicant_id uuid not null references public.profiles (id) on delete cascade,
  origin text not null default 'APPLIED' check (origin = any (ct.e_origin())),
  note text check (char_length(note) <= 140),
  note_flags text[] not null default '{}' check (note_flags <@ ct.e_msg_flag()),   -- MessageSafety on Ask notes
  work_link_id uuid references public.work_links (id) on delete set null,
  screening_answer text check (char_length(screening_answer) <= 300),
  kit_offered boolean not null default false,
  is_rebook boolean not null default false,
  status text not null default 'PENDING' check (status = any (ct.e_app_status())),
  backup_rank int,
  decline_reason text check (char_length(decline_reason) <= 200),
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  unique (role_id, applicant_id)
);

create table public.matches (
  id uuid primary key default gen_random_uuid(),
  application_id uuid not null unique references public.applications (id) on delete cascade,
  project_id uuid not null references public.projects (id) on delete cascade,
  role_id uuid not null references public.project_roles (id) on delete cascade,
  owner_id uuid not null references public.profiles (id) on delete cascade,
  collaborator_id uuid not null references public.profiles (id) on delete cascade,
  state text not null default 'TALKING' check (state = any (ct.e_match_state())),
  is_post_role boolean not null default false,
  change_pending boolean not null default false,
  contacts_shared_by_owner boolean not null default false,
  contacts_shared_by_collab boolean not null default false,
  withdraw_reason text,
  notice_hours int,
  released_by_owner boolean not null default false,
  owner_read_at timestamptz,
  collab_read_at timestamptz,
  created_at timestamptz not null default now(),
  booked_at timestamptz,
  ended_at timestamptz
);

create table public.booking_cards (
  id uuid primary key default gen_random_uuid(),
  match_id uuid not null references public.matches (id) on delete cascade,
  version int not null,
  terms jsonb not null,
  status text not null default 'SENT' check (status = any (ct.e_booking_status())),
  changed_fields text[] not null default '{}',             -- BookingDiff keys vs the previous version
  sent_by uuid references public.profiles (id) on delete cascade,
  sent_at timestamptz not null default now(),
  responded_at timestamptz,
  unique (match_id, version)
);

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  match_id uuid not null references public.matches (id) on delete cascade,
  sender_id uuid references public.profiles (id) on delete cascade,      -- null = system
  kind text not null default 'TEXT' check (kind = any (ct.e_msg_kind())),
  body text not null default '' check (char_length(body) <= 2000),
  payload jsonb,              -- AUDITION: {request:{mode,sides_url,due_date,date,time,meeting_link,venue,address,also_present[],note}, response, tape_url}
  booking_card_id uuid references public.booking_cards (id) on delete set null,
  flags text[] not null default '{}' check (flags <@ ct.e_msg_flag()),   -- computed by trigger
  safety_example boolean not null default false,
  created_at timestamptz not null default now()
);

-- Shoot & wrap (L1 RPCs; tables exist now so the schema is frozen)
create table public.shoot_days (
  id uuid primary key default gen_random_uuid(),
  project_id uuid not null references public.projects (id) on delete cascade,
  day date not null,
  general_call time not null,
  wrap_by time not null,
  address text not null default '',
  maps_url text not null default '',
  parking_note text,
  hospital_note text,
  meals_plan text,
  notes text check (char_length(notes) <= 500),
  version int not null default 1,
  published_at timestamptz,
  cancelled_at timestamptz
);

create table public.call_times (
  shoot_day_id uuid not null references public.shoot_days (id) on delete cascade,
  profile_id uuid not null references public.profiles (id) on delete cascade,
  call_time time not null,
  ack_at timestamptz,
  attendance text check (attendance = any (ct.e_attendance())),
  primary key (shoot_day_id, profile_id)
);

create table public.wrap_items (
  id uuid primary key default gen_random_uuid(),
  match_id uuid not null references public.matches (id) on delete cascade,
  kind text not null check (kind = any (ct.e_wrap_kind())),
  due_date date,
  owed_by text not null check (owed_by = any (ct.e_owed_by())),    -- DELIVERY: COLLABORATOR; all others: PRODUCTION
  status text not null default 'OPEN' check (status = any (ct.e_wrap_status())),
  link text,
  note text,
  marked_at timestamptz,
  confirmed_at timestamptz,
  auto_confirmed boolean not null default false,
  unique (match_id, kind)
);

create table public.feedback (
  id uuid primary key default gen_random_uuid(),
  match_id uuid not null references public.matches (id) on delete cascade,
  from_id uuid not null references public.profiles (id) on delete cascade,
  to_id uuid not null references public.profiles (id) on delete cascade,
  answers jsonb not null,
  private_note text,
  submitted_at timestamptz not null default now(),
  revealed_at timestamptz,
  unique (match_id, from_id)
);

-- Safety & system
create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  target_kind text not null check (target_kind = any (ct.e_report_target())),
  target_id uuid not null,
  reason text not null check (reason = any (ct.e_report_reason())),
  details text check (char_length(details) <= 500),
  status text not null default 'RECEIVED' check (status = any (ct.e_report_status())),
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);

create table public.blocks (
  blocker_id uuid not null references public.profiles (id) on delete cascade,
  blocked_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  kind text not null check (kind = any (ct.e_notif_kind())),
  title text not null default '',
  body text not null default '',
  deep_link text,                    -- calltime://m/{matchId} | calltime://r/{roleId} | calltime://p/{projectId}
  payload jsonb not null default '{}',
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create table public.events (           -- analytics, insert-only
  id bigserial primary key,
  user_id uuid,
  name text not null check (char_length(name) <= 80),
  props jsonb not null default '{}',
  created_at timestamptz not null default now()
);

-- Indexes (PRD §9.2 + the joins the RPCs use)
create index on public.areas (city_id);
create index on public.profiles (city_id, primary_role);
create index on public.profile_roles (role_code, kind);
create index on public.work_links (profile_id);
create index on public.project_roles (status, project_id);
create index on public.projects (status, city_id, shoot_start);
create index on public.projects (owner_id);
create index on public.applications (role_id, status);
create index on public.applications (applicant_id, created_at);
create index on public.matches (owner_id);
create index on public.matches (collaborator_id);
create index on public.matches (project_id, state);
create index on public.messages (match_id, created_at);
create index on public.booking_cards (match_id, version);
create index on public.notifications (user_id, read_at);
create index on public.notifications (user_id, created_at desc);
create index on public.swipes (actor_id, target_kind);
create index on public.blocks (blocked_id);
create index on public.reports (target_kind, target_id);
create index on public.shoot_days (project_id, day);
create index on public.wrap_items (match_id);

-- ───────────────────────────────────────── 4. Row helpers (need the tables) ─────────────────────────────────────────

create function ct.is_post_role(x public.project_roles) returns boolean language sql immutable as $$
  select coalesce(x.remote_ok, false) and x.post_deadline is not null and ct.role_dept(x.role_code) in ('POST', 'MUSIC', 'WRITING') $$;

-- perks(R, P) (PRD §7.1): role perks + derived MEALS / TRAVEL_COVERED / LODGING / SHORT_COMMITMENT / SCRIPT_READY
create function ct.role_perks(x public.project_roles, y public.projects) returns text[] language sql immutable as $$
  select array(select distinct e from unnest(
       coalesce(x.perks, '{}')
    || case when ct.meals_rank(y.meals) >= 3 then array['MEALS'] else '{}'::text[] end
    || case when y.transport in ('MILEAGE_OR_TA_PAID', 'PRODUCTION_VAN') then array['TRAVEL_COVERED'] else '{}'::text[] end
    || case when coalesce(y.lodging, false) then array['LODGING'] else '{}'::text[] end
    || case when coalesce(y.shoot_days, 0) <= 3 then array['SHORT_COMMITMENT'] else '{}'::text[] end
    || case when y.script_status = 'LOCKED' then array['SCRIPT_READY'] else '{}'::text[] end) e order by e) $$;

-- dayRate(R): amount_max for PAID_DAY / STIPEND; amount_max / shoot_days for on-set PAID_FLAT; null otherwise.
create function ct.day_rate(x public.project_roles, y public.projects) returns int language sql immutable as $$
  select case
    when x.comp_type in ('PAID_DAY', 'STIPEND') then coalesce(x.amount_max, x.amount_min)
    when x.comp_type = 'PAID_FLAT' and not ct.is_post_role(x) and coalesce(y.shoot_days, 0) > 0
      then coalesce(x.amount_max, x.amount_min) / y.shoot_days
    else null end $$;

-- rate_fit (PRD §7.4): min(1, dayRate / min_day_rate); 1 when no private rate, unpaid, or no day rate (post flat fee).
create function ct.rate_fit(x public.project_roles, y public.projects, p_min int) returns float8 language sql immutable as $$
  select case
    when x.comp_type = 'UNPAID' or p_min is null or p_min <= 0 then 1.0
    when ct.day_rate(x, y) is null then 1.0
    else least(1.0, ct.day_rate(x, y)::float8 / p_min) end $$;

create function ct.perk_label(p_perk text, x public.project_roles, y public.projects) returns text language sql immutable as $$
  select case p_perk
    when 'TRAVEL_COVERED' then case when y.travel_allowance_per_day is not null then 'TA ' || ct.money(y.travel_allowance_per_day, x.currency) || '/day' else 'travel covered' end
    when 'LODGING' then 'lodging'
    when 'FOOTAGE_BY_DATE' then 'your scenes in ' || coalesce(x.footage_due_weeks::text, '?') || ' wks'
    when 'FINAL_FILM_BY_DATE' then 'final film in ' || coalesce(x.final_film_due_weeks::text, '?') || ' wks'
    when 'STEP_UP_CREDIT' then 'step-up credit'
    when 'MEALS' then case y.meals when 'CATERED' then 'catered' else '2 meals' end
    else null end $$;

create function ct.pay_line(x public.project_roles, y public.projects) returns text language sql immutable as $$
  select case x.comp_type
      when 'PAID_DAY' then ct.money(coalesce(x.amount_max, x.amount_min), x.currency) || '/day'
      when 'PAID_FLAT' then ct.money(coalesce(x.amount_max, x.amount_min), x.currency) || ' flat'
      when 'STIPEND' then 'Stipend ' || ct.money(coalesce(x.amount_max, x.amount_min), x.currency) || '/day'
      else 'Unpaid' end
    || case when coalesce(x.kit_fee_day, 0) > 0 then ' + ' || ct.money(x.kit_fee_day, x.currency) || ' kit' else '' end
    || case when x.comp_type <> 'UNPAID' and x.payment_terms is not null then ' · ' || ct.terms_label(x.payment_terms) else '' end
    || coalesce(' · ' || nullif(array_to_string(array(
         select ct.perk_label(e, x, y) from unnest(ct.role_perks(x, y)) e where e not in ('SHORT_COMMITMENT', 'SCRIPT_READY')), ' · '), ''), '') $$;

create function ct.when_line(x public.project_roles, y public.projects) returns text language sql immutable as $$
  select case
    when ct.is_post_role(x) then 'Remote · deliver by ' || to_char(x.post_deadline, 'FMDD Mon YYYY')
    when cardinality(y.shoot_dates) = 0 then 'Dates to be set'
    else array_to_string(array(select to_char(d, 'Dy FMDD Mon') from unnest(y.shoot_dates) d order by d), ', ')
         || ' (' || cardinality(y.shoot_dates) || case when cardinality(y.shoot_dates) = 1 then ' day' else ' days' end
         || coalesce(', ≤' || y.hours_cap || 'h', '') || ')' end $$;

create function ct.city_tz(p_city text) returns text language sql stable security definer set search_path = public as $$
  select coalesce((select tz from public.cities where id = p_city), 'UTC') $$;

create function ct.city_today(p_city text) returns date language sql stable security definer set search_path = public as $$
  select (now() at time zone ct.city_tz(p_city))::date $$;

create function ct.user_tz(p_uid uuid) returns text language sql stable security definer set search_path = public as $$
  select coalesce((select c.tz from public.profiles p join public.cities c on c.id = p.city_id where p.id = p_uid), 'UTC') $$;

create function ct.user_today(p_uid uuid) returns date language sql stable security definer set search_path = public as $$
  select (now() at time zone ct.user_tz(p_uid))::date $$;

create function ct.require_uid() returns uuid language plpgsql stable as $$
declare v uuid := auth.uid();
begin
  if v is null then perform ct.fail('Sign in first.', 'AUTH'); end if;
  return v;
end $$;

create function ct.require_profile(p_uid uuid) returns public.profiles language plpgsql stable security definer set search_path = public, ct as $$
declare v public.profiles;
begin
  select * into v from public.profiles where id = p_uid;
  if not found then perform ct.fail('Finish your profile first.', 'NO_PROFILE'); end if;
  return v;
end $$;

-- Blocked in either direction. SECURITY DEFINER so RLS policies can see the other person's block row too.
create function ct.blocked(a uuid, b uuid) returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.blocks k
                 where (k.blocker_id = a and k.blocked_id = b) or (k.blocker_id = b and k.blocked_id = a)) $$;

-- ≥3 independent open reports → auto-hidden from decks pending review (PRD §5.5).
create function ct.auto_hidden(p_profile uuid) returns boolean language sql stable security definer set search_path = public as $$
  select (select count(distinct reporter_id) from public.reports
          where target_kind = 'PROFILE' and target_id = p_profile and status in ('RECEIVED', 'REVIEWING')) >= 3 $$;

create function ct.notify(p_user uuid, p_kind text, p_title text, p_body text, p_link text, p_payload jsonb default '{}')
returns void language sql security definer set search_path = public as $$
  insert into public.notifications (user_id, kind, title, body, deep_link, payload)
  values (p_user, p_kind, coalesce(p_title, ''), coalesce(p_body, ''), p_link, coalesce(p_payload, '{}'::jsonb)) $$;

create function ct.on_keep_list(p_owner uuid, p_person uuid) returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.feedback f
                 where f.from_id = p_owner and f.to_id = p_person
                   and coalesce((f.answers ->> 'work_again')::boolean, (f.answers ->> 'workAgain')::boolean, false)) $$;

create function ct.can_see_address(p_project uuid, p_uid uuid) returns boolean language sql stable security definer set search_path = public as $$
  select p_uid is not null and (
       exists (select 1 from public.projects where id = p_project and owner_id = p_uid)
    or exists (select 1 from public.matches where project_id = p_project and collaborator_id = p_uid and state in ('BOOKED', 'WRAPPED'))) $$;

-- applications_count = live APPLIED applications (PENDING / BACKUP / MATCHED): declines bring a capped role back.
-- filled_count = BOOKED + WRAPPED matches. A FILLED role that drops below its count reopens.
create function ct.recount_role(p_role uuid) returns void language sql security definer set search_path = public as $$
  update public.project_roles x set
    applications_count = (select count(*) from public.applications a
                          where a.role_id = x.id and a.origin = 'APPLIED' and a.status in ('PENDING', 'BACKUP', 'MATCHED')),
    filled_count = (select count(*) from public.matches m where m.role_id = x.id and m.state in ('BOOKED', 'WRAPPED')),
    status = case when x.status = 'FILLED'
                   and (select count(*) from public.matches m where m.role_id = x.id and m.state in ('BOOKED', 'WRAPPED')) < x.count
                  then 'OPEN' else x.status end
  where x.id = p_role $$;

-- Normalised Levenshtein similarity in 0..1 (ApplicantRanker answer check; computed here so other answers never leave the server).
create function ct.similarity(a text, b text) returns float8 language sql immutable as $$
  select case when greatest(char_length(s.x), char_length(s.y)) = 0 then 1.0
              else 1.0 - extensions.levenshtein(s.x, s.y)::float8 / greatest(char_length(s.x), char_length(s.y)) end
  from (select left(lower(regexp_replace(btrim(coalesce(a, '')), '\s+', ' ', 'g')), 255) as x,
               left(lower(regexp_replace(btrim(coalesce(b, '')), '\s+', ' ', 'g')), 255) as y) s $$;

-- ───────────────────────────────────────── 5. JSON builders (RPC payloads) ─────────────────────────────────────────

create function ct.ratio(n bigint, d bigint) returns jsonb language sql immutable as $$
  select case when d >= 3 then jsonb_build_object('num', n, 'den', d) else null end $$;

-- Reputation (F14): facts only; ratio fields null when n < 3. Shared by get_reputation / reputation_stats.
create function ct.reputation_json(p_id uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare
  v_shoots int; su_n int; su_d int; dot_n int; dot_d int; pot_n int; pot_d int; fd_n int; fd_d int;
  kb_n int; kb_d int; cl_n int; cl_d int; v_credits int; v_reply int; v_reply_n int;
begin
  select count(distinct m.project_id) into v_shoots from matches m
   where m.state = 'WRAPPED' and (m.collaborator_id = p_id or m.owner_id = p_id);

  select count(*) filter (where c.attendance = 'ATTENDED'), count(*) filter (where c.attendance in ('ATTENDED', 'NO_SHOW'))
    into su_n, su_d from call_times c where c.profile_id = p_id;

  select count(*) filter (where w.status = 'CONFIRMED' and (w.due_date is null or w.marked_at::date <= w.due_date)),
         count(*) filter (where w.status in ('CONFIRMED', 'NOT_RECEIVED') or (w.status in ('OPEN', 'DONE') and w.due_date < current_date))
    into dot_n, dot_d from wrap_items w join matches m on m.id = w.match_id
   where m.collaborator_id = p_id and w.kind = 'DELIVERY';

  select count(*) filter (where w.status = 'CONFIRMED' and (w.due_date is null or w.marked_at::date <= w.due_date)),
         count(*) filter (where w.status in ('CONFIRMED', 'NOT_RECEIVED') or (w.status in ('OPEN', 'DONE') and w.due_date < current_date))
    into pot_n, pot_d from wrap_items w join matches m on m.id = w.match_id
   where m.owner_id = p_id and w.kind = 'PAID';

  select count(*) filter (where w.status = 'CONFIRMED'),
         count(*) filter (where w.status in ('CONFIRMED', 'NOT_RECEIVED') or (w.status in ('OPEN', 'DONE') and w.due_date < current_date))
    into fd_n, fd_d from wrap_items w join matches m on m.id = w.match_id
   where m.owner_id = p_id and w.kind = 'FOOTAGE';

  -- Kept bookings: resolved bookings minus releases/cancellations with < 72h notice.
  -- TODO(L1): also count "cut late" versions (BookingDiff.isCut within 72h of day 1) once reschedule ships.
  select count(*) filter (where not (m.released_by_owner and coalesce(m.notice_hours, 0) < 72)), count(*)
    into kb_n, kb_d from matches m
   where m.owner_id = p_id and m.booked_at is not null and m.state in ('WRAPPED', 'RELEASED', 'NO_SHOW');

  select count(*) filter (where a.decided_at is not null and a.decided_at <= a.created_at + interval '14 days'),
         count(*) filter (where a.decided_at is not null or a.created_at < now() - interval '14 days')
    into cl_n, cl_d from applications a join project_roles r on r.id = a.role_id join projects p on p.id = r.project_id
   where p.owner_id = p_id and a.origin = 'APPLIED';

  select percentile_cont(0.5) within group (order by extract(epoch from a.decided_at - a.created_at) / 3600.0)::int, count(*)
    into v_reply, v_reply_n from applications a join project_roles r on r.id = a.role_id join projects p on p.id = r.project_id
   where p.owner_id = p_id and a.origin = 'APPLIED' and a.decided_at is not null;

  select count(*) into v_credits from wrap_items w join matches m on m.id = w.match_id
   where m.collaborator_id = p_id and w.kind = 'CREDIT' and w.status = 'CONFIRMED';

  return jsonb_build_object(
    'profile_id', p_id, 'shoots_completed', coalesce(v_shoots, 0),
    'showed_up', ct.ratio(su_n, su_d), 'delivered_on_time', ct.ratio(dot_n, dot_d),
    'paid_on_time', ct.ratio(pot_n, pot_d), 'footage_delivered', ct.ratio(fd_n, fd_d),
    'kept_bookings', ct.ratio(kb_n, kb_d), 'closes_loop', ct.ratio(cl_n, cl_d),
    'reply_hours_median', case when v_reply_n >= 3 then v_reply end,
    'confirmed_credits', coalesce(v_credits, 0),
    -- m_resp input (PRD §7.3): closure rate over 14 days, only when n >= 5
    'closure_rate_14d', case when cl_d >= 5 then round(cl_n::numeric / cl_d, 3) end);
end $$;

create function ct.credits_json(p_id uuid) returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'project_title', p.title, 'role_code', r.role_code,
      'credit_title', coalesce((select bc.terms ->> 'credit_title' from booking_cards bc
                                where bc.match_id = m.id and bc.status = 'ACCEPTED' order by bc.version desc limit 1), r.credit_title),
      'year', extract(year from coalesce(w.confirmed_at, w.marked_at, now()))::int) order by w.confirmed_at desc), '[]'::jsonb)
  from wrap_items w join matches m on m.id = w.match_id join projects p on p.id = m.project_id join project_roles r on r.id = m.role_id
  where m.collaborator_id = p_id and w.kind = 'CREDIT' and w.status = 'CONFIRMED' $$;

-- A profile as JSON. p_private: content_wont_do + all busy dates (owner's own view only).
-- p_headshots: include HEADSHOT links. p_busy: include (future) busy dates for avail() on the owner's device.
create function ct.profile_json(p_id uuid, p_private boolean, p_headshots boolean, p_busy boolean) returns jsonb
language sql stable security definer set search_path = public, ct as $$
  select (to_jsonb(p) - 'content_wont_do' - 'created_at' - 'last_active_at') || jsonb_build_object(
    'content_wont_do', case when p_private then to_jsonb(p.content_wont_do) else '[]'::jsonb end,
    'secondary_roles', coalesce((select jsonb_agg(jsonb_build_object('code', r.role_code, 'proficiency', coalesce(r.proficiency, 2)) order by r.role_code)
                                 from profile_roles r where r.profile_id = p.id and r.kind = 'SECONDARY'), '[]'::jsonb),
    'step_up_roles', coalesce((select jsonb_agg(r.role_code order by r.role_code)
                               from profile_roles r where r.profile_id = p.id and r.kind = 'STEP_UP'), '[]'::jsonb),
    'busy_dates', case when p_busy or p_private then coalesce((select jsonb_agg(b.day order by b.day) from busy_dates b
                       where b.profile_id = p.id and (p_private or b.day >= current_date - 1)), '[]'::jsonb) else '[]'::jsonb end,
    'work_links', coalesce((select jsonb_agg(jsonb_build_object('id', w.id, 'url', w.url, 'kind', w.kind, 'role_code', w.role_code,
                                                                'title', w.title, 'year', w.year) order by w.sort, w.id)
                            from work_links w where w.profile_id = p.id and (p_headshots or w.kind <> 'HEADSHOT')), '[]'::jsonb),
    'created_at', ct.ms(p.created_at), 'last_active_at', ct.ms(p.last_active_at))
  from profiles p where p.id = p_id $$;

-- ProfileCard: sanitised profile + reputation + confirmed credits + distance from (p_lat, p_lng).
create function ct.card_json(p_id uuid, p_headshots boolean, p_busy boolean, p_lat float8, p_lng float8) returns jsonb
language sql stable security definer set search_path = public, ct as $$
  select jsonb_build_object(
    'profile', ct.profile_json(p.id, false, p_headshots, p_busy),
    'reputation', ct.reputation_json(p.id),
    'confirmed_credits', ct.credits_json(p.id),
    'distance_km', case when p_lat is null or p_lng is null then null else round(ct.km(p_lat, p_lng, a.lat, a.lng)::numeric, 1) end,
    'area_name', a.name, 'city_name', c.name)
  from profiles p join areas a on a.id = p.area_id join cities c on c.id = p.city_id where p.id = p_id $$;

create function ct.owner_json(p_id uuid) returns jsonb language sql stable security definer set search_path = public, ct as $$
  select jsonb_build_object('id', p.id, 'display_name', p.display_name, 'level', p.level, 'id_verified', p.id_verified,
           'reputation', rep, 'last_active_at', ct.ms(p.last_active_at), 'closure_rate_14d', rep -> 'closure_rate_14d')
  from profiles p, lateral (select ct.reputation_json(p.id) as rep) r where p.id = p_id $$;

create function ct.project_json(y public.projects, p_address boolean) returns jsonb language sql immutable as $$
  select (to_jsonb(y) - 'exact_address' - 'created_at' - 'published_at' - 'updated_at') || jsonb_build_object(
    'exact_address', case when p_address then y.exact_address end,
    'created_at', ct.ms(y.created_at), 'published_at', ct.ms(y.published_at), 'updated_at', ct.ms(y.updated_at)) $$;

create function ct.role_json(x public.project_roles) returns jsonb language sql immutable as $$
  select (to_jsonb(x) - 'created_at') || jsonb_build_object('created_at', ct.ms(x.created_at)) $$;

create function ct.app_json(x public.applications) returns jsonb language sql immutable as $$
  select (to_jsonb(x) - 'created_at' - 'decided_at') || jsonb_build_object('created_at', ct.ms(x.created_at), 'decided_at', ct.ms(x.decided_at)) $$;

create function ct.match_json(x public.matches) returns jsonb language sql immutable as $$
  select (to_jsonb(x) - 'created_at' - 'booked_at' - 'ended_at' - 'owner_read_at' - 'collab_read_at')
      || jsonb_build_object('created_at', ct.ms(x.created_at), 'booked_at', ct.ms(x.booked_at), 'ended_at', ct.ms(x.ended_at)) $$;

create function ct.card_row_json(x public.booking_cards) returns jsonb language sql immutable as $$
  select (to_jsonb(x) - 'sent_at' - 'responded_at') || jsonb_build_object('sent_at', ct.ms(x.sent_at), 'responded_at', ct.ms(x.responded_at)) $$;

create function ct.message_json(x public.messages) returns jsonb language sql immutable as $$
  select (to_jsonb(x) - 'created_at') || jsonb_build_object('created_at', ct.ms(x.created_at)) $$;

-- Team attached: owner + BOOKED/WRAPPED people. Short names + initials only (never full names of crew to guests).
create function ct.team_json(p_project uuid) returns jsonb language sql stable security definer set search_path = public, ct as $$
  select coalesce(jsonb_agg(s.t order by s.ord, s.n), '[]'::jsonb) from (
    select 0 as ord, o.display_name as n, jsonb_build_object('profile_id', o.id, 'short_name', ct.short_name(o.display_name),
             'initials', ct.initials(o.display_name), 'role_code', o.primary_role, 'is_owner', true) as t
      from projects p join profiles o on o.id = p.owner_id where p.id = p_project
    union all
    select 1, u.display_name, jsonb_build_object('profile_id', u.id, 'short_name', ct.short_name(u.display_name),
             'initials', ct.initials(u.display_name), 'role_code', r.role_code, 'is_owner', false)
      from matches m join profiles u on u.id = m.collaborator_id join project_roles r on r.id = m.role_id
     where m.project_id = p_project and m.state in ('BOOKED', 'WRAPPED')) s $$;

-- RoleWithProject (one Gigs-deck candidate).
create function ct.rwp_json(p_role uuid, p_uid uuid) returns jsonb language sql stable security definer set search_path = public, ct as $$
  select jsonb_build_object(
    'role', ct.role_json(r), 'project', ct.project_json(p, ct.can_see_address(p.id, p_uid)),
    'owner', ct.owner_json(p.owner_id), 'city_name', c.name, 'area_name', a.name, 'area_lat', a.lat, 'area_lng', a.lng,
    'team', ct.team_json(p.id),
    'paid_roles', (select count(*) from project_roles x where x.project_id = p.id and x.comp_type in ('PAID_DAY', 'PAID_FLAT') and x.status <> 'CLOSED'),
    'total_roles', (select count(*) from project_roles x where x.project_id = p.id and x.status <> 'CLOSED'))
  from project_roles r join projects p on p.id = r.project_id join cities c on c.id = p.city_id join areas a on a.id = p.area_id
  where r.id = p_role $$;

create function ct.terms_summary(x public.project_roles, y public.projects) returns text language sql stable security definer set search_path = public, ct as $$
  select 'In talks: ' || coalesce(nullif(x.credit_title, ''), ct.role_label(x.role_code)) || ' on "' || y.title || '". '
      || 'Pay: ' || ct.pay_line(x, y) || '. '
      || 'When: ' || ct.when_line(x, y) || '. '
      || case when ct.is_post_role(x) then 'Remote role. '
              else 'Where: ' || (select a.name || ', ' || c.name from areas a join cities c on c.id = a.city_id where a.id = y.area_id) || '. ' end
      || 'Next step: the booking card. Contact details stay hidden until you''re booked.' $$;

-- Reverse gates for Scout / Ask / Rebook (PRD §3.2, §7.4): null when the person may be asked, else the reason to show.
-- Runs server-side because it needs profile_private.min_day_rate and content_wont_do, which never leave the server.
create function ct.ask_block_reason(p_role uuid, p_profile uuid) returns text language plpgsql stable security definer set search_path = public, ct as $$
declare vr project_roles; vp projects; vu profiles; v_min int; v_first text; v_paid boolean; v_rate int;
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
  select min_day_rate into v_min from profile_private where id = vu.id;
  v_paid := vr.comp_type in ('PAID_DAY', 'PAID_FLAT');
  v_rate := ct.day_rate(vr, vp);
  if vu.comp_mode = 'PAID_ONLY' then                                         -- reverse G4
    if not v_paid then return v_first || ' only takes paid work right now.'; end if;
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

create function ct.rv(p_rule text, p_msg text, p_role uuid default null, p_field text default null) returns jsonb language sql immutable as $$
  select jsonb_build_array(jsonb_build_object('rule', p_rule, 'severity', 'BLOCK', 'message', p_msg, 'role_id', p_role, 'field', p_field)) $$;

-- ListingRules BLOCK rules (PRD §5.3: R01–R14 except AUTO R04 / WARN R06, plus R18–R20), mirrored from domain/ListingRules.kt.
-- p_check_dates = false skips "not in the past" checks (used when an already-OPEN listing is edited).
create function ct.listing_violations(p_project uuid, p_check_dates boolean default true) returns jsonb
language plpgsql stable security definer set search_path = public, ct as $$
declare
  vp projects; vo profiles; vr project_roles; v jsonb := '[]'; v_today date; v_n int := 0;
  v_cast boolean := false; v_post_only boolean; v_intim boolean; v_texts text[]; v_perks text[];
begin
  select * into vp from projects where id = p_project;
  if not found then return ct.rv('R18', 'Project not found.'); end if;
  select * into vo from profiles where id = vp.owner_id;
  v_today := ct.city_today(vp.city_id);
  select count(*) into v_n from project_roles where project_id = vp.id and status <> 'CLOSED';
  v_post_only := v_n > 0 and not exists (select 1 from project_roles x where x.project_id = vp.id and x.status <> 'CLOSED'
                                         and not (x.remote_ok and ct.role_dept(x.role_code) in ('POST', 'MUSIC', 'WRITING')));
  -- R18
  if char_length(btrim(vp.title)) not between 2 and 60 then v := v || ct.rv('R18', 'Give the project a title (2-60 characters).', null, 'title'); end if;
  if char_length(btrim(vp.logline)) not between 20 and 200 then v := v || ct.rv('R18', 'Write a logline of 20-200 characters.', null, 'logline'); end if;
  if cardinality(vp.languages) < 1 then v := v || ct.rv('R18', 'Pick at least one language.', null, 'languages'); end if;
  if v_n < 1 then v := v || ct.rv('R18', 'Add at least one role.', null, 'roles'); end if;
  -- R07
  if cardinality(vp.content_flags) = 0 and not vp.content_none_confirmed then
    v := v || ct.rv('R07', 'Answer the content question. Tick "None of these" if none apply.', null, 'contentFlags');
  end if;
  -- R08
  v_intim := vp.content_flags && array['NUDITY', 'SIMULATED_SEX', 'INTIMACY_SCENE'];
  if v_intim and (vp.intimacy_coordinator is null or char_length(btrim(coalesce(vp.safety_measures, ''))) < 20) then
    v := v || ct.rv('R08', 'Intimate scenes need an intimacy coordinator answer and safety measures (20+ characters). No nudity at auditions.', null, 'safetyMeasures');
  end if;
  -- R09
  if vp.content_flags && array['STUNTS_FIGHTS', 'FIREARMS_PROPS'] and (vp.stunt_or_armourer is null or btrim(coalesce(vp.safety_measures, '')) = '') then
    v := v || ct.rv('R09', 'Stunts, fights or firearms need a coordinator or armourer answer and safety measures.', null, 'safetyMeasures');
  end if;
  -- R10
  if 'MINORS_ON_SET' = any (vp.content_flags) and vp.guardian_present is not true then
    v := v || ct.rv('R10', 'Minors on set need a guardian present. Cast roles are 18+ only.', null, 'guardianPresent');
  end if;
  v_texts := array[vp.title, vp.logline, vp.story_setting, vp.safety_measures];
  for vr in select * from project_roles where project_id = vp.id and status <> 'CLOSED' order by sort, created_at loop
    if ct.role_dept(vr.role_code) = 'CAST' then v_cast := true; end if;
    -- R01
    if vr.comp_type in ('PAID_DAY', 'PAID_FLAT', 'STIPEND') and (coalesce(vr.amount_max, vr.amount_min, 0) <= 0 or vr.payment_terms is null) then
      v := v || ct.rv('R01', 'Add pay and payment terms, or mark it unpaid and say what people get.', vr.id, 'amount');
    end if;
    -- R02 (on-set unpaid: >= 2 perks incl. derived, and project meals >= ONE_MEAL)
    if vr.comp_type = 'UNPAID' and not ct.is_post_role(vr) then
      v_perks := ct.role_perks(vr, vp);
      if cardinality(array(select unnest(v_perks) intersect select unnest(array['TRAVEL_COVERED', 'LODGING', 'FOOTAGE_BY_DATE', 'FINAL_FILM_BY_DATE', 'STEP_UP_CREDIT', 'MEALS']))) < 2
         or ct.meals_rank(vp.meals) < 2 then
        v := v || ct.rv('R02', 'Unpaid on-set roles need at least 2 perks and at least one meal. If you can''t pay people, feed people.', vr.id, 'perks');
      end if;
    end if;
    -- R03
    if vr.comp_type = 'UNPAID' and vr.role_code = 'ACTOR'
       and not ('FOOTAGE_BY_DATE' = any (vr.perks) and vr.footage_due_weeks is not null and vr.footage_due_weeks <= 26) then
      v := v || ct.rv('R03', 'Unpaid actors need their footage by a date, 26 weeks at most.', vr.id, 'footageDueWeeks');
    end if;
    -- R13
    if vr.comp_type = 'UNPAID' and coalesce(vr.screening_question, '') ~* '(demo|test\s*edit|pre-?\s*screen|custom|sample\s*cut)' then
      v := v || ct.rv('R13', 'Don''t ask for spec work (demos, test edits, samples) on an unpaid role.', vr.id, 'screeningQuestion');
    end if;
    -- R20
    if vr.role_code in ('COMPOSER', 'SOUND_DESIGNER') and vp.picture_lock_date is null then
      v := v || ct.rv('R20', 'Composers and sound designers need a picture-lock date to judge the job.', vr.id, 'pictureLockDate');
    end if;
    -- R05 (post-only projects: every role needs a delivery date >= today + 7)
    if v_post_only and (vr.post_deadline is null or (p_check_dates and vr.post_deadline < v_today + 7)) then
      v := v || ct.rv('R05', 'Set a delivery date at least 7 days from today.', vr.id, 'postDeadline');
    end if;
    v_texts := v_texts || vr.credit_title || vr.screening_question || vr.scope_includes || vr.scope_excludes
               || array(select e.value from jsonb_each_text(vr.role_details) e);
  end loop;
  -- R05 (any on-set role)
  if v_n > 0 and not v_post_only then
    if cardinality(vp.shoot_dates) = 0 then
      v := v || ct.rv('R05', 'Add your shoot dates.', null, 'shootDates');
    elsif p_check_dates and vp.shoot_start < v_today then
      v := v || ct.rv('R05', 'Shoot dates can''t be in the past.', null, 'shootDates');
    end if;
    if vp.hours_cap is null or vp.hours_cap not between 2 and 16 then v := v || ct.rv('R05', 'Set an hours cap between 2 and 16.', null, 'hoursCap'); end if;
    if vp.meals is null then v := v || ct.rv('R05', 'Say what meals there are.', null, 'meals'); end if;
    if vp.transport is null then v := v || ct.rv('R05', 'Say how people get there.', null, 'transport'); end if;
    if vp.shoot_location_type is null then v := v || ct.rv('R05', 'Say what kind of location it is.', null, 'shootLocationType'); end if;
  end if;
  -- R11
  if v_cast and vp.audition_mode is null then
    v := v || ct.rv('R11', 'Pick how auditions happen: self-tape, video call, or a studio or public space with others present.', null, 'auditionMode');
  end if;
  -- R19
  if v_cast and v_intim and not coalesce(vo.id_verified, false) then
    v := v || ct.rv('R19', 'Projects with intimate scenes need an ID-verified owner. Book a 5-minute check.', null, 'contentFlags');
  end if;
  -- R12 / R14 on every public text field
  if exists (select 1 from unnest(v_texts) t where t is not null and 'MONEY_FROM_TALENT' = any (ct.message_flags(t, true))) then
    v := v || ct.rv('R12', 'Calltime never allows charging cast or crew.', null, 'text');
  end if;
  if exists (select 1 from unnest(v_texts) t where t is not null and 'CONTACT' = any (ct.message_flags(t, false))) then
    v := v || ct.rv('R14', 'Keep contact in Calltime until you''re booked.', null, 'text');
  end if;
  return v;
end $$;

-- ───────────────────────────────────────── 6. Triggers ─────────────────────────────────────────

-- *_verified flags can only be set by service_role (concierge ID check). email/phone come from auth.users.
create function ct.trg_profile_protect() returns trigger language plpgsql security definer set search_path = public, auth, ct as $$
begin
  if ct.is_service() then return new; end if;
  new.id_verified := case when tg_op = 'UPDATE' then old.id_verified else false end;
  select coalesce(u.email_confirmed_at is not null, false), coalesce(u.phone_confirmed_at is not null, false)
    into new.email_verified, new.phone_verified from auth.users u where u.id = new.id;
  new.email_verified := coalesce(new.email_verified, false);
  new.phone_verified := coalesce(new.phone_verified, false);
  if tg_op = 'UPDATE' then new.created_at := old.created_at; end if;
  return new;
end $$;
create trigger profiles_protect before insert or update on public.profiles for each row execute function ct.trg_profile_protect();

-- shoot_dates is authoritative: sort + dedupe, derive start/end/days. Private home → bring a friend forced on.
create function ct.trg_project_sync() returns trigger language plpgsql as $$
begin
  new.shoot_dates := coalesce((select array_agg(distinct d order by d) from unnest(new.shoot_dates) d where d is not null), '{}');
  new.shoot_start := new.shoot_dates[1];
  new.shoot_end := new.shoot_dates[cardinality(new.shoot_dates)];
  new.shoot_days := nullif(cardinality(new.shoot_dates), 0);
  new.updated_at := now();
  if new.shoot_location_type = 'PRIVATE_HOME' then new.bring_friend_welcome := true; end if;
  if tg_op = 'UPDATE' then new.created_at := old.created_at; end if;
  return new;
end $$;
create trigger projects_sync before insert or update on public.projects for each row execute function ct.trg_project_sync();

-- Messages: server clock + MessageSafety flags (MONEY only when the sender is the owner side or unknown).
create function ct.trg_message_flags() returns trigger language plpgsql security definer set search_path = public, ct as $$
declare v_owner uuid; v_text text;
begin
  if tg_op = 'INSERT' then new.created_at := now(); end if;
  if new.sender_id is null or new.kind in ('SYSTEM', 'BOOKING') then new.flags := '{}'; return new; end if;
  select owner_id into v_owner from matches where id = new.match_id;
  v_text := coalesce(new.body, '');
  if new.kind = 'AUDITION' and new.payload is not null then
    v_text := concat_ws(E'\n', v_text, new.payload #>> '{request,note}', new.payload #>> '{request,venue}', new.payload #>> '{request,address}');
  end if;
  new.flags := ct.message_flags(v_text, v_owner is null or new.sender_id = v_owner);
  return new;
end $$;
create trigger messages_flags before insert or update of body, payload on public.messages for each row execute function ct.trg_message_flags();

-- Editing an OPEN listing must keep it within the rules (checked at commit, so multi-row saves are fine).
create function ct.trg_recheck_open() returns trigger language plpgsql security definer set search_path = public, ct as $$
declare v_pid uuid; v jsonb;
begin
  if tg_table_name = 'projects' then v_pid := new.id; else v_pid := new.project_id; end if;
  if exists (select 1 from projects where id = v_pid and status = 'OPEN') then
    v := ct.listing_violations(v_pid, false);
    if jsonb_array_length(v) > 0 then
      perform ct.fail('Can''t save: ' || (v -> 0 ->> 'message'), 'RULES:' || (select string_agg(distinct e ->> 'rule', ',') from jsonb_array_elements(v) e));
    end if;
  end if;
  return null;
end $$;
create constraint trigger projects_recheck_open
  after update of title, logline, languages, story_setting, safety_measures, content_flags, content_none_confirmed, intimacy_coordinator,
                  stunt_or_armourer, guardian_present, audition_mode, shoot_dates, hours_cap, meals, transport, shoot_location_type, picture_lock_date
  on public.projects deferrable initially deferred for each row execute function ct.trg_recheck_open();
create constraint trigger roles_recheck_open
  after insert or update of role_code, comp_type, amount_min, amount_max, payment_terms, perks, footage_due_weeks, remote_ok, post_deadline,
                            screening_question, credit_title, scope_includes, scope_excludes, role_details
  on public.project_roles deferrable initially deferred for each row execute function ct.trg_recheck_open();

-- ───────────────────────────────────────── 7. Views ─────────────────────────────────────────

-- Public project card columns (no exact_address). RLS of `projects` applies (security_invoker).
create view public.project_cards with (security_invoker = true) as
  select id, owner_id, payer_id, title, logline, format, runtime_min, page_count, genres, languages, stage, script_status,
         script_readable, lookbook_url, ambition_tier, honest_zero_budget, city_id, area_id, shoot_location_type, story_setting,
         shoot_dates, shoot_start, shoot_end, shoot_days, picture_lock_date, date_certainty, hours_cap, meals, transport,
         travel_allowance_per_day, lodging, content_flags, content_none_confirmed, intimacy_coordinator, stunt_or_armourer,
         guardian_present, safety_measures, audition_mode, bring_friend_welcome, insurance, permits, status, reschedule_count,
         created_at, published_at, updated_at
  from public.projects;

-- Safe profile columns for moderation / admin. NOT granted to anon or authenticated (no scrape list); RPCs use ct.card_json.
create view public.profile_cards as
  select p.id, p.display_name, p.pronouns, p.primary_role, p.level, p.city_id, p.area_id, p.remote_ok, p.languages,
         p.availability_pattern, p.comp_mode, p.kit_fee_day, p.gear, p.tools, p.taste_films, p.bio, p.affiliations,
         p.email_verified, p.phone_verified, p.id_verified, p.created_at, p.last_active_at,
         array(select r.role_code from public.profile_roles r where r.profile_id = p.id and r.kind = 'SECONDARY') as secondary_roles,
         array(select r.role_code from public.profile_roles r where r.profile_id = p.id and r.kind = 'STEP_UP') as step_up_roles,
         ct.reputation_json(p.id) as reputation
  from public.profiles p;

-- ───────────────────────────────────────── 8. RPCs: account & profile ─────────────────────────────────────────

create function public.get_my_profile() returns jsonb language sql stable security definer set search_path = public, ct as $$
  select ct.profile_json(auth.uid(), true, true, true) $$;

create function public.my_private_min_rate() returns int language sql stable security definer set search_path = public as $$
  select min_day_rate from public.profile_private where id = auth.uid() $$;

create function public.touch_active() returns void language sql security definer set search_path = public as $$
  update public.profiles set last_active_at = now() where id = auth.uid() $$;

-- Upsert the caller's whole profile atomically (profile row, roles, work links, busy dates, private rate).
create function public.save_profile(p_profile jsonb, p_min_rate int default null) returns jsonb
language plpgsql security definer set search_path = public, ct as $$
declare
  v_uid uuid := ct.require_uid(); j jsonb := coalesce(p_profile, '{}'); v_cur text; v_link jsonb; v_keep uuid[] := '{}';
  v_lid uuid; v_i int := 0; v_primary text := j ->> 'primary_role';
begin
  if v_primary is null or not (v_primary = any (ct.e_role())) then perform ct.fail('Pick your main role.', 'PRIMARY_ROLE'); end if;
  if char_length(btrim(coalesce(j ->> 'display_name', ''))) not between 2 and 40 then perform ct.fail('Your name needs 2-40 characters.', 'NAME'); end if;
  if not exists (select 1 from areas where id = j ->> 'area_id' and city_id = j ->> 'city_id') then perform ct.fail('Pick your city and area.', 'AREA'); end if;
  if char_length(coalesce(j ->> 'bio', '')) > 280 then perform ct.fail('Keep your bio under 280 characters.', 'BIO'); end if;

  insert into profiles as p (id, display_name, pronouns, primary_role, level, city_id, area_id, travel_radius_km, travel_if_covered,
      remote_ok, languages, availability_pattern, comp_mode, unpaid_must_haves, goals, content_wont_do, gear, kit_fee_day, tools,
      taste_films, genres_pref, formats_pref, bio, affiliations, adult_confirmed, last_active_at)
  values (v_uid, btrim(j ->> 'display_name'), nullif(btrim(coalesce(j ->> 'pronouns', '')), ''), v_primary,
      coalesce(j ->> 'level', 'BUILDING_REEL'), j ->> 'city_id', j ->> 'area_id',
      least(150, greatest(2, coalesce((j ->> 'travel_radius_km')::int, 15))), coalesce((j ->> 'travel_if_covered')::boolean, false),
      coalesce((j ->> 'remote_ok')::boolean, false), ct.jarr(j -> 'languages', '{en}'), coalesce(j ->> 'availability_pattern', 'ANY_DAY'),
      coalesce(j ->> 'comp_mode', 'PAID_AND_SELECTIVE'), ct.jarr(j -> 'unpaid_must_haves'), ct.jarr(j -> 'goals'),
      ct.jarr(j -> 'content_wont_do'), ct.jarr(j -> 'gear'), (j ->> 'kit_fee_day')::int, ct.jarr(j -> 'tools'),
      (ct.jarr(j -> 'taste_films'))[1:5], ct.jarr(j -> 'genres_pref'), ct.jarr(j -> 'formats_pref'), nullif(btrim(coalesce(j ->> 'bio', '')), ''),
      ct.jarr(j -> 'affiliations'), coalesce((j ->> 'adult_confirmed')::boolean, false), now())
  on conflict (id) do update set
      display_name = excluded.display_name, pronouns = excluded.pronouns, primary_role = excluded.primary_role, level = excluded.level,
      city_id = excluded.city_id, area_id = excluded.area_id, travel_radius_km = excluded.travel_radius_km,
      travel_if_covered = excluded.travel_if_covered, remote_ok = excluded.remote_ok, languages = excluded.languages,
      availability_pattern = excluded.availability_pattern, comp_mode = excluded.comp_mode, unpaid_must_haves = excluded.unpaid_must_haves,
      goals = excluded.goals, content_wont_do = excluded.content_wont_do, gear = excluded.gear, kit_fee_day = excluded.kit_fee_day,
      tools = excluded.tools, taste_films = excluded.taste_films, genres_pref = excluded.genres_pref, formats_pref = excluded.formats_pref,
      bio = excluded.bio, affiliations = excluded.affiliations, adult_confirmed = (p.adult_confirmed or excluded.adult_confirmed),
      last_active_at = now();

  -- roles
  delete from profile_roles where profile_id = v_uid;
  insert into profile_roles (profile_id, role_code, kind, proficiency) values (v_uid, v_primary, 'PRIMARY', 3);
  insert into profile_roles (profile_id, role_code, kind, proficiency)
    select v_uid, s.code, 'SECONDARY', least(3, greatest(1, s.prof)) from (
      select distinct on (e ->> 'code') e ->> 'code' as code, coalesce((e ->> 'proficiency')::int, 2) as prof
      from jsonb_array_elements(case when jsonb_typeof(j -> 'secondary_roles') = 'array' then j -> 'secondary_roles' else '[]' end) e
      where e ->> 'code' = any (ct.e_role()) and e ->> 'code' <> v_primary) s limit 4;
  insert into profile_roles (profile_id, role_code, kind)
    select distinct v_uid, c, 'STEP_UP' from unnest((ct.jarr(j -> 'step_up_roles'))[1:2]) c where c = any (ct.e_role()) and c <> v_primary;

  -- work links: upsert by id (keeps application references), delete the rest
  for v_link in select * from jsonb_array_elements(case when jsonb_typeof(j -> 'work_links') = 'array' then j -> 'work_links' else '[]' end) loop
    v_i := v_i + 1;
    if coalesce(v_link ->> 'url', '') !~* '^https?://' then perform ct.fail('Work links must start with http:// or https://', 'LINK'); end if;
    v_lid := ct.try_uuid(v_link ->> 'id');
    if v_lid is not null and exists (select 1 from work_links where id = v_lid and profile_id <> v_uid) then v_lid := null; end if;
    insert into work_links as w (id, profile_id, role_code, kind, url, title, year, sort)
    values (coalesce(v_lid, gen_random_uuid()), v_uid, v_link ->> 'role_code', coalesce(v_link ->> 'kind', 'OTHER'), v_link ->> 'url',
            nullif(v_link ->> 'title', ''), (v_link ->> 'year')::int, v_i)
    on conflict (id) do update set role_code = excluded.role_code, kind = excluded.kind, url = excluded.url, title = excluded.title,
            year = excluded.year, sort = excluded.sort
    returning w.id into v_lid;
    v_keep := v_keep || v_lid;
  end loop;
  delete from work_links where profile_id = v_uid and not (id = any (v_keep));

  -- busy dates
  delete from busy_dates where profile_id = v_uid;
  insert into busy_dates (profile_id, day) select distinct v_uid, d from unnest(ct.jdates(j -> 'busy_dates')) d;

  -- private rate (null clears it)
  select currency into v_cur from cities where id = j ->> 'city_id';
  insert into profile_private (id, min_day_rate, currency) values (v_uid, p_min_rate, v_cur)
  on conflict (id) do update set min_day_rate = excluded.min_day_rate, currency = excluded.currency;

  return public.get_my_profile();
end $$;

-- Relationship-scoped profile read (PRD §9.3). Null when no relationship exists or either side blocked the other.
create function public.get_profile(p_profile uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := auth.uid(); v_counterpart boolean; v_rel boolean; v_head boolean; v_lat float8; v_lng float8;
begin
  if v_uid is null or p_profile is null then return null; end if;
  if p_profile <> v_uid and ct.blocked(v_uid, p_profile) then return null; end if;
  v_counterpart := exists (select 1 from matches m where (m.owner_id = v_uid and m.collaborator_id = p_profile)
                                                     or (m.owner_id = p_profile and m.collaborator_id = v_uid));
  v_rel := p_profile = v_uid or v_counterpart
    -- the person applied to / was asked for a role I own
    or exists (select 1 from applications a join project_roles r on r.id = a.role_id join projects p on p.id = r.project_id
               where a.applicant_id = p_profile and p.owner_id = v_uid)
    -- I applied to / was asked for a role the person owns
    or exists (select 1 from applications a join project_roles r on r.id = a.role_id join projects p on p.id = r.project_id
               where a.applicant_id = v_uid and p.owner_id = p_profile)
    -- posters are public: the person owns an OPEN project
    or exists (select 1 from projects p where p.owner_id = p_profile and p.status = 'OPEN')
    -- teammates on a project (both booked, or one owns it)
    or exists (select 1 from matches m1 join matches m2 on m2.project_id = m1.project_id
               where m1.collaborator_id = v_uid and m2.collaborator_id = p_profile
                 and m1.state in ('BOOKED', 'WRAPPED') and m2.state in ('BOOKED', 'WRAPPED'));
  if not v_rel then return null; end if;
  v_head := p_profile = v_uid or v_counterpart
    or exists (select 1 from applications a join project_roles r on r.id = a.role_id join projects p on p.id = r.project_id
               where a.applicant_id = p_profile and a.origin = 'APPLIED' and p.owner_id = v_uid and ct.role_dept(r.role_code) = 'CAST');
  select a.lat, a.lng into v_lat, v_lng from profiles me join areas a on a.id = me.area_id where me.id = v_uid;
  return ct.card_json(p_profile, v_head, false, case when p_profile = v_uid then null else v_lat end, v_lng);
end $$;

create function public.get_reputation(p_profile uuid) returns jsonb language sql stable security definer set search_path = public, ct as $$
  select case when exists (select 1 from public.profiles where id = p_profile) then ct.reputation_json(p_profile) end $$;

-- PRD §9.2 name for the same aggregate (security definer; ratios null when n < 3).
create function public.reputation_stats(p_profile uuid) returns jsonb language sql stable security definer set search_path = public, ct as $$
  select public.get_reputation(p_profile) $$;

create function public.export_my_data() returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid();
begin
  return jsonb_build_object(
    'exported_at', now(),
    'account', (select jsonb_build_object('id', u.id, 'email', u.email, 'created_at', u.created_at) from auth.users u where u.id = v_uid),
    'profile', ct.profile_json(v_uid, true, true, true),
    'private', (select to_jsonb(x) from profile_private x where x.id = v_uid),
    'projects', coalesce((select jsonb_agg(ct.project_json(p, true) || jsonb_build_object('roles',
                   coalesce((select jsonb_agg(ct.role_json(r)) from project_roles r where r.project_id = p.id), '[]'::jsonb)))
                 from projects p where p.owner_id = v_uid), '[]'::jsonb),
    'applications', coalesce((select jsonb_agg(ct.app_json(a)) from applications a where a.applicant_id = v_uid), '[]'::jsonb),
    'matches', coalesce((select jsonb_agg(ct.match_json(m)) from matches m where v_uid in (m.owner_id, m.collaborator_id)), '[]'::jsonb),
    'messages_sent', coalesce((select jsonb_agg(ct.message_json(x) order by x.created_at) from messages x where x.sender_id = v_uid), '[]'::jsonb),
    'booking_cards_sent', coalesce((select jsonb_agg(ct.card_row_json(b)) from booking_cards b where b.sent_by = v_uid), '[]'::jsonb),
    'swipes', coalesce((select jsonb_agg(to_jsonb(s)) from swipes s where s.actor_id = v_uid), '[]'::jsonb),
    'feedback_given', coalesce((select jsonb_agg(to_jsonb(f)) from feedback f where f.from_id = v_uid), '[]'::jsonb),
    'reports', coalesce((select jsonb_agg(to_jsonb(r)) from reports r where r.reporter_id = v_uid), '[]'::jsonb),
    'blocks', coalesce((select jsonb_agg(to_jsonb(b)) from blocks b where b.blocker_id = v_uid), '[]'::jsonb),
    'notifications', coalesce((select jsonb_agg(to_jsonb(n) order by n.created_at) from notifications n where n.user_id = v_uid), '[]'::jsonb));
end $$;

-- Deletes the auth user; every row the person owns cascades (PRD F16).
create function public.delete_my_account() returns void language plpgsql security definer set search_path = public, auth, ct as $$
declare v_uid uuid := ct.require_uid();
begin
  delete from auth.users where id = v_uid;
end $$;

-- ───────────────────────────────────────── 9. RPCs: cities & density ─────────────────────────────────────────

create function public.get_cities() returns jsonb language sql stable security definer set search_path = public as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'id', c.id, 'name', c.name, 'country', c.country, 'currency', c.currency, 'lat', c.lat, 'lng', c.lng,
      'paid_floor_day', c.paid_floor_day, 'emergency_number', c.emergency_number, 'tz', c.tz,
      'is_live', c.is_live, 'is_early', c.is_early,
      'areas', coalesce((select jsonb_agg(jsonb_build_object('id', a.id, 'city_id', a.city_id, 'name', a.name, 'lat', a.lat, 'lng', a.lng)
                                          order by a.sort, a.name) from areas a where a.city_id = c.id), '[]'::jsonb))
    order by c.is_live desc, c.is_early asc, c.sort, c.name), '[]'::jsonb)
  from cities c $$;

-- Honest density header (F5): counts only, never identities. Guests may call it.
create function public.city_density(p_city text) returns jsonb language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'city_id', c.id, 'city_name', c.name, 'is_live', c.is_live, 'is_early', c.is_early,
    'people', (select count(*) from profiles p where p.city_id = c.id),
    'open_projects', (select count(*) from projects p where p.city_id = c.id and p.status = 'OPEN'),
    'per_role', coalesce((select jsonb_object_agg(s.primary_role, s.n) from
                  (select primary_role, count(*) as n from profiles p where p.city_id = c.id group by primary_role) s), '{}'::jsonb))
  from cities c where c.id = p_city $$;

-- Onboarding "≈ N people and M projects within X km".
create function public.nearby_counts(p_area text, p_radius_km int) returns jsonb language sql stable security definer set search_path = public, ct as $$
  select jsonb_build_object(
    'people', (select count(*) from profiles p join areas a on a.id = p.area_id where ct.km(x.lat, x.lng, a.lat, a.lng) <= p_radius_km),
    'projects', (select count(*) from projects p join areas a on a.id = p.area_id where p.status = 'OPEN' and ct.km(x.lat, x.lng, a.lat, a.lng) <= p_radius_km))
  from areas x where x.id = p_area $$;

-- ───────────────────────────────────────── 10. RPCs: Gigs ─────────────────────────────────────────

-- Pre-gate OPEN roles for a city (+ remote roles in the same country). Excludes own, swiped, applied, blocked,
-- auto-hidden owners and past-start on-set roles. Gates + scoring run on the device (MatchEngine).
create function public.discover_roles(p_city text default null, p_include_remote boolean default true, p_limit int default 300)
returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := auth.uid(); v_city text := p_city; v_country text;
begin
  if v_city is null and v_uid is not null then select city_id into v_city from profiles where id = v_uid; end if;
  if v_city is null then perform ct.fail('Pick a city first.', 'NO_CITY'); end if;
  select country into v_country from cities where id = v_city;
  return coalesce((select jsonb_agg(ct.rwp_json(s.id, v_uid) order by s.published_at desc nulls last, s.id) from (
    select r.id, p.published_at
    from project_roles r join projects p on p.id = r.project_id join cities c on c.id = p.city_id
    where r.status = 'OPEN' and p.status = 'OPEN'
      and (p.city_id = v_city or (coalesce(p_include_remote, true) and r.remote_ok and c.country = v_country))
      and (ct.is_post_role(r) or p.shoot_start is null or p.shoot_start >= ct.city_today(p.city_id))
      and not ct.auto_hidden(p.owner_id)
      and (v_uid is null or (
            p.owner_id <> v_uid
        and not ct.blocked(v_uid, p.owner_id)
        and not exists (select 1 from swipes s where s.actor_id = v_uid and s.target_kind = 'ROLE' and s.target_id = r.id)
        and not exists (select 1 from applications a where a.role_id = r.id and a.applicant_id = v_uid)))
    order by p.published_at desc nulls last
    limit greatest(1, least(coalesce(p_limit, 300), 500))) s), '[]'::jsonb);
end $$;

create function public.get_role(p_role uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := auth.uid(); vp projects;
begin
  select p.* into vp from project_roles r join projects p on p.id = r.project_id where r.id = p_role;
  if vp.id is null then return null; end if;
  if vp.status = 'DRAFT' and vp.owner_id is distinct from v_uid then return null; end if;
  if v_uid is not null and ct.blocked(v_uid, vp.owner_id) then return null; end if;
  return ct.rwp_json(p_role, v_uid);
end $$;

create function public.saved_roles() returns jsonb language sql stable security definer set search_path = public, ct as $$
  select coalesce(jsonb_agg(ct.rwp_json(s.target_id, auth.uid()) order by s.created_at desc), '[]'::jsonb)
  from swipes s join project_roles r on r.id = s.target_id join projects p on p.id = r.project_id
  where s.actor_id = auth.uid() and s.target_kind = 'ROLE' and s.direction = 'SAVE' and p.status <> 'DRAFT'
    and not ct.blocked(auth.uid(), p.owner_id) $$;

create function public.swipe_role(p_role uuid, p_dir text) returns void language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid();
begin
  perform ct.require_profile(v_uid);
  if not (p_dir = any (ct.e_swipe())) then perform ct.fail('Unknown swipe.', 'BAD_DIR'); end if;
  if not exists (select 1 from project_roles where id = p_role) then perform ct.fail('This role is no longer listed.', 'NOT_FOUND'); end if;
  insert into swipes (actor_id, target_kind, target_id, direction) values (v_uid, 'ROLE', p_role, p_dir)
  on conflict (actor_id, target_kind, target_id, (coalesce(context_role_id, '00000000-0000-0000-0000-000000000000'::uuid)))
  do update set direction = excluded.direction, created_at = now();
end $$;

-- Undo (5 s chip): only LEFT / SAVE; RIGHT created an application.
create function public.undo_swipe(p_role uuid) returns void language sql security definer set search_path = public as $$
  delete from public.swipes where actor_id = auth.uid() and target_kind = 'ROLE' and target_id = p_role and direction in ('LEFT', 'SAVE') $$;

create function public.likes_left_today() returns int language sql stable security definer set search_path = public, ct as $$
  select greatest(0, 25 - (select count(*) from applications a where a.applicant_id = auth.uid() and a.origin = 'APPLIED'
                           and (a.created_at at time zone ct.user_tz(auth.uid()))::date = ct.user_today(auth.uid())))::int $$;

create function public.apply_to_role(p_role uuid, p_note text default null, p_link uuid default null, p_answer text default null, p_kit boolean default false)
returns jsonb language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vu profiles; vr project_roles; vp projects; va applications; v_n int;
begin
  vu := ct.require_profile(v_uid);
  select * into vr from project_roles where id = p_role for update;
  if not found then perform ct.fail('This role is no longer listed.', 'NOT_FOUND'); end if;
  select * into vp from projects where id = vr.project_id;
  if vp.owner_id = v_uid then perform ct.fail('You can''t apply to your own project.', 'OWN_ROLE'); end if;
  if ct.blocked(v_uid, vp.owner_id) then perform ct.fail('This role is no longer open.', 'BLOCKED'); end if;
  if vr.status <> 'OPEN' or vp.status <> 'OPEN' then perform ct.fail('Just filled. Here''s a similar one.', 'ROLE_CLOSED'); end if;
  if not ct.is_post_role(vr) and vp.shoot_start is not null and vp.shoot_start < ct.city_today(vp.city_id) then
    perform ct.fail('This shoot has already started.', 'PAST_START');
  end if;
  if exists (select 1 from applications where role_id = p_role and applicant_id = v_uid) then
    perform ct.fail('You''ve already applied to this role.', 'DUPLICATE');
  end if;
  if vr.applications_count >= vr.application_cap then
    perform ct.fail('This role has all the applications it can take right now.', 'CAP_REACHED');
  end if;
  select count(*) into v_n from applications a where a.applicant_id = v_uid and a.origin = 'APPLIED'
     and (a.created_at at time zone ct.user_tz(v_uid))::date = ct.user_today(v_uid);
  if v_n >= 25 then perform ct.fail('You''ve sent 25 today. Fewer, better applications get answered. Back tomorrow.', 'DAILY_CAP'); end if;
  if char_length(coalesce(p_note, '')) > 140 then perform ct.fail('Keep your note to 140 characters.', 'NOTE_LENGTH'); end if;
  if char_length(coalesce(p_answer, '')) > 300 then perform ct.fail('Keep your answer to 300 characters.', 'ANSWER_LENGTH'); end if;
  if btrim(coalesce(vr.screening_question, '')) <> '' and btrim(coalesce(p_answer, '')) = '' then
    perform ct.fail('Answer the screening question to apply.', 'ANSWER_REQUIRED');
  end if;
  if p_link is not null and not exists (select 1 from work_links where id = p_link and profile_id = v_uid) then p_link := null; end if;

  insert into applications (role_id, applicant_id, origin, note, work_link_id, screening_answer, kit_offered, status)
  values (p_role, v_uid, 'APPLIED', nullif(btrim(coalesce(p_note, '')), ''), p_link, nullif(btrim(coalesce(p_answer, '')), ''),
          coalesce(p_kit, false), 'PENDING')
  returning * into va;
  insert into swipes (actor_id, target_kind, target_id, direction) values (v_uid, 'ROLE', p_role, 'RIGHT')
  on conflict (actor_id, target_kind, target_id, (coalesce(context_role_id, '00000000-0000-0000-0000-000000000000'::uuid)))
  do update set direction = 'RIGHT', created_at = now();
  perform ct.recount_role(p_role);
  update profiles set last_active_at = now() where id = v_uid;
  perform ct.notify(vp.owner_id, 'APPLICATION_RECEIVED', 'New applicant',
                    ct.short_name(vu.display_name) || ' applied to be ' || ct.role_label(vr.role_code) || ' on "' || vp.title || '".',
                    'calltime://p/' || vp.id, jsonb_build_object('role_id', vr.id, 'application_id', va.id));
  return ct.app_json(va);
end $$;

-- ───────────────────────────────────────── 11. RPCs: Projects (owner) ─────────────────────────────────────────

-- Atomic draft save: the project + its roles. Roles missing from p_roles are deleted (or CLOSED if people applied).
-- Works for DRAFT and for OPEN/CREWED listings (edits of an OPEN listing are re-checked against the rules at commit).
create function public.save_project_draft(p_project jsonb, p_roles jsonb default '[]') returns uuid
language plpgsql security definer set search_path = public, ct as $$
declare
  v_uid uuid := ct.require_uid(); j jsonb := coalesce(p_project, '{}'); v_id uuid := ct.try_uuid(j ->> 'id'); vp projects;
  v_cur text; v_role jsonb; v_rid uuid; v_keep uuid[] := '{}'; v_i int := 0; v_code text;
begin
  perform ct.require_profile(v_uid);
  if not exists (select 1 from areas where id = j ->> 'area_id' and city_id = j ->> 'city_id') then
    perform ct.fail('Pick the city and area of the shoot.', 'AREA');
  end if;
  select currency into v_cur from cities where id = j ->> 'city_id';
  if v_id is not null then select * into vp from projects where id = v_id for update; end if;
  if vp.id is not null then
    if vp.owner_id <> v_uid then perform ct.fail('This isn''t your project.', 'NOT_OWNER'); end if;
    if vp.status not in ('DRAFT', 'OPEN', 'CREWED') then perform ct.fail('This project can''t be edited any more.', 'LOCKED'); end if;
  else
    v_id := coalesce(v_id, gen_random_uuid());
    insert into projects (id, owner_id, payer_id, city_id, area_id) values (v_id, v_uid, v_uid, j ->> 'city_id', j ->> 'area_id');
  end if;

  update projects set
    title = left(btrim(coalesce(j ->> 'title', '')), 60), logline = left(btrim(coalesce(j ->> 'logline', '')), 200),
    format = coalesce(j ->> 'format', 'SHORT'), runtime_min = least(240, greatest(1, coalesce((j ->> 'runtime_min')::int, 10))),
    page_count = (j ->> 'page_count')::int, genres = (ct.jarr(j -> 'genres'))[1:3], languages = ct.jarr(j -> 'languages', '{en}'),
    stage = coalesce(j ->> 'stage', 'PRE'), script_status = coalesce(j ->> 'script_status', 'DRAFT'),
    script_readable = coalesce((j ->> 'script_readable')::boolean, false), lookbook_url = nullif(btrim(coalesce(j ->> 'lookbook_url', '')), ''),
    ambition_tier = coalesce(j ->> 'ambition_tier', 'PORTFOLIO'), honest_zero_budget = coalesce((j ->> 'honest_zero_budget')::boolean, false),
    city_id = j ->> 'city_id', area_id = j ->> 'area_id', shoot_location_type = j ->> 'shoot_location_type',
    exact_address = nullif(btrim(coalesce(j ->> 'exact_address', '')), ''), story_setting = nullif(btrim(coalesce(j ->> 'story_setting', '')), ''),
    shoot_dates = ct.jdates(j -> 'shoot_dates'), picture_lock_date = (j ->> 'picture_lock_date')::date,
    date_certainty = j ->> 'date_certainty', hours_cap = (j ->> 'hours_cap')::int, meals = j ->> 'meals', transport = j ->> 'transport',
    travel_allowance_per_day = (j ->> 'travel_allowance_per_day')::int, lodging = (j ->> 'lodging')::boolean,
    content_flags = ct.jarr(j -> 'content_flags'), content_none_confirmed = coalesce((j ->> 'content_none_confirmed')::boolean, false),
    intimacy_coordinator = (j ->> 'intimacy_coordinator')::boolean, stunt_or_armourer = (j ->> 'stunt_or_armourer')::boolean,
    guardian_present = (j ->> 'guardian_present')::boolean, safety_measures = nullif(btrim(coalesce(j ->> 'safety_measures', '')), ''),
    audition_mode = j ->> 'audition_mode', bring_friend_welcome = coalesce((j ->> 'bring_friend_welcome')::boolean, false),
    insurance = j ->> 'insurance', permits = j ->> 'permits'
  where id = v_id;

  for v_role in select * from jsonb_array_elements(case when jsonb_typeof(p_roles) = 'array' then p_roles else '[]' end) loop
    v_i := v_i + 1;
    v_code := v_role ->> 'role_code';
    if v_code is null or not (v_code = any (ct.e_role())) then perform ct.fail('Pick a role from the list.', 'ROLE_CODE'); end if;
    v_rid := ct.try_uuid(v_role ->> 'id');
    if v_rid is null or not exists (select 1 from project_roles where id = v_rid and project_id = v_id) then
      if v_rid is null or exists (select 1 from project_roles where id = v_rid) then v_rid := gen_random_uuid(); end if;
      insert into project_roles (id, project_id, role_code, comp_type, currency) values (v_rid, v_id, v_code, coalesce(v_role ->> 'comp_type', 'UNPAID'), coalesce(v_cur, 'INR'));
    end if;
    update project_roles set
      role_code = v_code, count = least(10, greatest(1, coalesce((v_role ->> 'count')::int, 1))),
      credit_title = left(coalesce(nullif(btrim(coalesce(v_role ->> 'credit_title', '')), ''), ct.role_credit(v_code)), 60),
      scope_includes = ct.jarr(v_role -> 'scope_includes'), scope_excludes = ct.jarr(v_role -> 'scope_excludes'),
      step_up_friendly = coalesce((v_role ->> 'step_up_friendly')::boolean, false), level_wanted = v_role ->> 'level_wanted',
      comp_type = coalesce(v_role ->> 'comp_type', 'UNPAID'), amount_min = (v_role ->> 'amount_min')::int, amount_max = (v_role ->> 'amount_max')::int,
      currency = coalesce(v_cur, 'INR'), kit_fee_day = (v_role ->> 'kit_fee_day')::int, payment_terms = v_role ->> 'payment_terms',
      perks = ct.jarr(v_role -> 'perks'), footage_due_weeks = (v_role ->> 'footage_due_weeks')::int,
      final_film_due_weeks = (v_role ->> 'final_film_due_weeks')::int, gear_policy = v_role ->> 'gear_policy',
      remote_ok = coalesce((v_role ->> 'remote_ok')::boolean, false), post_deadline = (v_role ->> 'post_deadline')::date,
      role_details = case when jsonb_typeof(v_role -> 'role_details') = 'object' then v_role -> 'role_details' else '{}'::jsonb end,
      screening_question = nullif(btrim(coalesce(v_role ->> 'screening_question', '')), ''),
      application_cap = least(40, greatest(5, coalesce(nullif((v_role ->> 'application_cap')::int, 0), ct.default_cap(v_code)))),
      sort = v_i
    where id = v_rid;
    v_keep := v_keep || v_rid;
  end loop;

  update project_roles x set status = 'CLOSED'
   where x.project_id = v_id and not (x.id = any (v_keep)) and exists (select 1 from applications a where a.role_id = x.id);
  delete from project_roles x
   where x.project_id = v_id and not (x.id = any (v_keep)) and not exists (select 1 from applications a where a.role_id = x.id);
  return v_id;
end $$;

-- Owner's editable copy (includes exact_address).
create function public.get_project_draft(p_project uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare vp projects;
begin
  select * into vp from projects where id = p_project and owner_id = auth.uid();
  if not found then return null; end if;
  return jsonb_build_object('project', ct.project_json(vp, true),
    'roles', coalesce((select jsonb_agg(ct.role_json(r) order by r.sort, r.created_at) from project_roles r where r.project_id = vp.id), '[]'::jsonb));
end $$;

-- BLOCK rules for the owner's wizard / tests: [{rule, severity, message, role_id, field}]. Empty = publishable.
create function public.listing_rules_check(p_project uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
begin
  if not exists (select 1 from projects where id = p_project and owner_id = auth.uid()) and not ct.is_service() then return null; end if;
  return ct.listing_violations(p_project, true);
end $$;

-- PRD §9.4: SQL mirror of the BLOCK rules (R01–R14, R18–R20).
create function public.listing_rules_ok(p_project uuid) returns boolean language sql stable security definer set search_path = public, ct as $$
  select case when exists (select 1 from projects where id = p_project and (owner_id = auth.uid() or ct.is_service()))
              then jsonb_array_length(ct.listing_violations(p_project, true)) = 0 end $$;

create function public.publish_project(p_project uuid) returns jsonb language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vp projects; v jsonb; v_floor int;
begin
  select * into vp from projects where id = p_project for update;
  if not found or vp.owner_id <> v_uid then perform ct.fail('This isn''t your project.', 'NOT_OWNER'); end if;
  if vp.status not in ('DRAFT', 'OPEN') then perform ct.fail('Only drafts can be published.', 'NOT_DRAFT'); end if;
  v := ct.listing_violations(vp.id, true);
  if jsonb_array_length(v) > 0 then
    perform ct.fail('Can''t publish yet: ' || (v -> 0 ->> 'message'),
                    'RULES:' || (select string_agg(distinct e ->> 'rule', ',') from jsonb_array_elements(v) e));
  end if;
  -- R04 (AUTO): paid per day below the city's floor is relabelled Stipend, so the card never says "Paid".
  select paid_floor_day into v_floor from cities where id = vp.city_id;
  update project_roles set comp_type = 'STIPEND'
   where project_id = vp.id and comp_type = 'PAID_DAY' and coalesce(amount_max, amount_min, 0) < coalesce(v_floor, 0);
  update projects set status = 'OPEN', published_at = coalesce(published_at, now()) where id = vp.id;
  return jsonb_build_object('ok', true, 'project_id', vp.id);
end $$;

create function public.close_role(p_role uuid) returns void language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vr project_roles; vp projects; va record;
begin
  select * into vr from project_roles where id = p_role for update;
  select * into vp from projects where id = vr.project_id;
  if vp.id is null or vp.owner_id <> v_uid then perform ct.fail('This isn''t your project.', 'NOT_OWNER'); end if;
  update project_roles set status = 'CLOSED' where id = vr.id;
  for va in update applications set status = 'CLOSED', decided_at = now()
             where role_id = vr.id and status = 'PENDING' returning applicant_id loop
    perform ct.notify(va.applicant_id, 'APPLICATION_CLOSED', 'Role closed',
                      'The ' || ct.role_label(vr.role_code) || ' role on "' || vp.title || '" was closed. Thanks for applying.',
                      'calltime://r/' || vr.id);
  end loop;
  perform ct.recount_role(vr.id);
end $$;

create function public.my_projects() returns jsonb language sql stable security definer set search_path = public, ct as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'project', ct.project_json(p, true),
      'roles', coalesce((select jsonb_agg(ct.role_json(r) order by r.sort, r.created_at) from project_roles r where r.project_id = p.id), '[]'::jsonb),
      'filled_slots', (select coalesce(sum(least(r.filled_count, r.count)), 0) from project_roles r where r.project_id = p.id and r.status <> 'CLOSED'),
      'total_slots', (select coalesce(sum(r.count), 0) from project_roles r where r.project_id = p.id and r.status <> 'CLOSED'),
      'next_shoot_date', (select min(d) from unnest(p.shoot_dates) d where d >= ct.city_today(p.city_id)),
      'overdue_wrap_items', (select count(*) from wrap_items w join matches m on m.id = w.match_id
                              where m.project_id = p.id and w.status in ('OPEN', 'DONE') and w.due_date < ct.city_today(p.city_id)),
      'new_applicants', (select count(*) from applications a join project_roles r on r.id = a.role_id
                          where r.project_id = p.id and a.status = 'PENDING' and a.origin = 'APPLIED'))
    order by p.updated_at desc), '[]'::jsonb)
  from projects p where p.owner_id = auth.uid() $$;

create function public.joined_projects() returns jsonb language sql stable security definer set search_path = public, ct as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'project', ct.project_json(p, true),
      'roles', coalesce((select jsonb_agg(ct.role_json(r) order by r.sort, r.created_at) from project_roles r where r.project_id = p.id), '[]'::jsonb),
      'filled_slots', (select coalesce(sum(least(r.filled_count, r.count)), 0) from project_roles r where r.project_id = p.id and r.status <> 'CLOSED'),
      'total_slots', (select coalesce(sum(r.count), 0) from project_roles r where r.project_id = p.id and r.status <> 'CLOSED'),
      'next_shoot_date', (select min(d) from unnest(p.shoot_dates) d where d >= ct.city_today(p.city_id)),
      'my_role_code', mr.role_code, 'my_match_id', m.id,
      'my_delivery_date', (select (bc.terms ->> 'delivery_date') from booking_cards bc
                           where bc.match_id = m.id and bc.status = 'ACCEPTED' order by bc.version desc limit 1))
    order by p.shoot_start nulls last), '[]'::jsonb)
  from matches m join projects p on p.id = m.project_id join project_roles mr on mr.id = m.role_id
  where m.collaborator_id = auth.uid() and m.state in ('BOOKED', 'WRAPPED') $$;

-- Applicant views for the owner: application + sanitised card + server-side rate_fit. Never min_day_rate / content_wont_do.
create function ct.applicant_rows(p_role uuid, p_statuses text[]) returns jsonb language sql stable security definer set search_path = public, ct as $$
  select coalesce(jsonb_agg(jsonb_build_object(
      'application', ct.app_json(a),
      'card', ct.card_json(a.applicant_id, ct.role_dept(r.role_code) = 'CAST' and a.origin = 'APPLIED', true, ar.lat, ar.lng),
      'rate_fit', round(ct.rate_fit(r, p, pp.min_day_rate)::numeric, 3),
      'headshot_url', case when ct.role_dept(r.role_code) = 'CAST' and a.origin = 'APPLIED' then
                        (select w.url from work_links w where w.profile_id = a.applicant_id and w.kind = 'HEADSHOT' order by w.sort limit 1) end,
      'answer_duplicate', a.screening_answer is not null and exists (
                        select 1 from applications o where o.applicant_id = a.applicant_id and o.id <> a.id
                          and o.screening_answer is not null and ct.similarity(o.screening_answer, a.screening_answer) >= 0.9))
    order by a.backup_rank nulls last, a.created_at), '[]'::jsonb)
  from applications a join project_roles r on r.id = a.role_id join projects p on p.id = r.project_id
       join areas ar on ar.id = p.area_id left join profile_private pp on pp.id = a.applicant_id
  where a.role_id = p_role and a.status = any (p_statuses) and not ct.blocked(p.owner_id, a.applicant_id) $$;

create function public.get_project_full(p_project uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := auth.uid(); vp projects; v_owner boolean; v_member boolean; vm matches;
begin
  select * into vp from projects where id = p_project;
  if not found then return null; end if;
  v_owner := vp.owner_id = v_uid;
  select * into vm from matches where project_id = vp.id and collaborator_id = v_uid order by created_at desc limit 1;
  v_member := vm.id is not null and vm.state in ('BOOKED', 'WRAPPED');
  if vp.status = 'DRAFT' and not v_owner then return null; end if;
  if v_uid is not null and not v_owner and ct.blocked(v_uid, vp.owner_id) then return null; end if;
  return jsonb_build_object(
    'project', ct.project_json(vp, ct.can_see_address(vp.id, v_uid)),
    'roles', coalesce((select jsonb_agg(ct.role_json(r) order by r.sort, r.created_at) from project_roles r where r.project_id = vp.id), '[]'::jsonb),
    'owner', ct.owner_json(vp.owner_id), 'is_owner', coalesce(v_owner, false),
    'team', case when v_owner or v_member then coalesce((select jsonb_agg(jsonb_build_object(
                 'match', ct.match_json(m), 'person', ct.card_json(m.collaborator_id, false, false, null, null), 'role_code', r.role_code,
                 'wrap_done', (select count(*) from wrap_items w where w.match_id = m.id and w.status = 'CONFIRMED'),
                 'wrap_total', (select count(*) from wrap_items w where w.match_id = m.id)) order by m.booked_at)
               from matches m join project_roles r on r.id = m.role_id where m.project_id = vp.id and m.state in ('BOOKED', 'WRAPPED')), '[]'::jsonb)
            else '[]'::jsonb end,
    'backups', case when v_owner then coalesce((select jsonb_object_agg(r.id, ct.applicant_rows(r.id, '{BACKUP}'))
                 from project_roles r where r.project_id = vp.id and exists (select 1 from applications a where a.role_id = r.id and a.status = 'BACKUP')), '{}'::jsonb)
               else '{}'::jsonb end,
    'my_match', case when vm.id is not null then ct.match_json(vm) end,
    'paid_roles', (select count(*) from project_roles x where x.project_id = vp.id and x.comp_type in ('PAID_DAY', 'PAID_FLAT') and x.status <> 'CLOSED'),
    'total_roles', (select count(*) from project_roles x where x.project_id = vp.id and x.status <> 'CLOSED'),
    'city_name', (select name from cities where id = vp.city_id), 'area_name', (select name from areas where id = vp.area_id),
    'area_lat', (select lat from areas where id = vp.area_id), 'area_lng', (select lng from areas where id = vp.area_id));
end $$;

-- ───────────────────────────────────────── 12. RPCs: Crew up ─────────────────────────────────────────

create function public.get_applicants(p_role uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid();
begin
  if not exists (select 1 from project_roles r join projects p on p.id = r.project_id where r.id = p_role and p.owner_id = v_uid) then
    perform ct.fail('Only the project owner can see applicants.', 'NOT_OWNER');
  end if;
  return ct.applicant_rows(p_role, '{PENDING,BACKUP}');
end $$;

create function ct.create_match(p_app uuid) returns public.matches language plpgsql security definer set search_path = public, ct as $$
declare va applications; vr project_roles; vp projects; vm matches;
begin
  select * into va from applications where id = p_app;
  select * into vr from project_roles where id = va.role_id;
  select * into vp from projects where id = vr.project_id;
  insert into matches (application_id, project_id, role_id, owner_id, collaborator_id, state, is_post_role)
  values (va.id, vp.id, vr.id, vp.owner_id, va.applicant_id, 'TALKING', ct.is_post_role(vr)) returning * into vm;
  insert into messages (match_id, sender_id, kind, body) values (vm.id, null, 'SYSTEM', ct.terms_summary(vr, vp));
  return vm;
end $$;

create function public.decide_application(p_app uuid, p_decision text, p_reason text default null) returns jsonb
language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); va applications; vr project_roles; vp projects; vm matches; vo profiles; v_rank int;
begin
  select * into va from applications where id = p_app for update;
  if not found then perform ct.fail('Application not found.', 'NOT_FOUND'); end if;
  select * into vr from project_roles where id = va.role_id;
  select * into vp from projects where id = vr.project_id;
  if vp.owner_id <> v_uid then perform ct.fail('Only the project owner can answer applicants.', 'NOT_OWNER'); end if;
  if va.origin <> 'APPLIED' then perform ct.fail('This person hasn''t answered your ask yet.', 'ASK_PENDING'); end if;
  if va.status not in ('PENDING', 'BACKUP') then perform ct.fail('This application was already answered.', 'ALREADY_DECIDED'); end if;
  if ct.blocked(v_uid, va.applicant_id) then perform ct.fail('This person isn''t available.', 'BLOCKED'); end if;
  select * into vo from profiles where id = v_uid;
  case upper(coalesce(p_decision, ''))
    when 'TALK' then
      if vr.status <> 'OPEN' then perform ct.fail('This role is no longer open.', 'ROLE_CLOSED'); end if;
      update applications set status = 'MATCHED', decided_at = now() where id = va.id;
      vm := ct.create_match(va.id);
      perform ct.notify(va.applicant_id, 'MATCH_CREATED', 'In talks',
        ct.short_name(vo.display_name) || ' wants to talk about ' || ct.role_label(vr.role_code) || ' on "' || vp.title || '".',
        'calltime://m/' || vm.id, jsonb_build_object('match_id', vm.id));
    when 'BACKUP' then
      if va.status <> 'BACKUP' then
        select coalesce(max(backup_rank), 0) + 1 into v_rank from applications where role_id = vr.id and status = 'BACKUP';
        update applications set status = 'BACKUP', backup_rank = v_rank, decided_at = now() where id = va.id;
        perform ct.notify(va.applicant_id, 'APPLICATION_BACKUP', 'You''re on the backup list',
          'You''re on the backup list for ' || ct.role_label(vr.role_code) || ' on "' || vp.title || '". If a slot opens, you''ll hear first.',
          'calltime://r/' || vr.id);
      end if;
    when 'DECLINE' then
      update applications set status = 'DECLINED', decline_reason = left(p_reason, 200), decided_at = now() where id = va.id;
      perform ct.notify(va.applicant_id, 'APPLICATION_CLOSED', 'Not this time',
        'This role went another way. Thanks for applying. (' || ct.role_label(vr.role_code) || ', "' || vp.title || '")',
        'calltime://r/' || vr.id);
    else
      perform ct.fail('Unknown decision.', 'BAD_DECISION');
  end case;
  insert into swipes (actor_id, target_kind, target_id, context_role_id, direction)
  values (v_uid, 'PROFILE', va.applicant_id, vr.id,
          case upper(p_decision) when 'TALK' then 'RIGHT' when 'BACKUP' then 'BACKUP' else 'LEFT' end)
  on conflict (actor_id, target_kind, target_id, (coalesce(context_role_id, '00000000-0000-0000-0000-000000000000'::uuid)))
  do update set direction = excluded.direction, created_at = now();
  perform ct.recount_role(vr.id);
  update profiles set last_active_at = now() where id = v_uid;
  return case when vm.id is not null then ct.match_json(vm) end;
end $$;

-- Scout (crew roles only): non-applicants who fit the role and pass reverse G4/G5 (+G7) server-side.
create function public.scout_candidates(p_role uuid, p_limit int default 100) returns jsonb
language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vr project_roles; vp projects; v_country text; v_lat float8; v_lng float8;
begin
  select * into vr from project_roles where id = p_role;
  select * into vp from projects where id = vr.project_id;
  if vp.id is null or vp.owner_id <> v_uid then perform ct.fail('Only the project owner can scout.', 'NOT_OWNER'); end if;
  if ct.role_dept(vr.role_code) = 'CAST' then
    perform ct.fail('Actors apply to you. Calltime doesn''t let anyone browse actors.', 'CAST_SCOUT');
  end if;
  select c.country into v_country from cities c where c.id = vp.city_id;
  select a.lat, a.lng into v_lat, v_lng from areas a where a.id = vp.area_id;
  return coalesce((select jsonb_agg(jsonb_build_object(
             'application', null,
             'card', ct.card_json(s.id, false, true, v_lat, v_lng),
             'rate_fit', round(ct.rate_fit(vr, vp, s.min_day_rate)::numeric, 3),
             'headshot_url', null, 'answer_duplicate', false) order by s.last_active_at desc)
    from (select u.id, u.last_active_at, pp.min_day_rate
          from profiles u left join profile_private pp on pp.id = u.id join cities uc on uc.id = u.city_id
          where u.id <> v_uid
            and (u.primary_role = vr.role_code or exists (select 1 from profile_roles x where x.profile_id = u.id and x.role_code = vr.role_code
                   and (x.kind in ('PRIMARY', 'SECONDARY') or (x.kind = 'STEP_UP' and vr.step_up_friendly))))
            and (u.city_id = vp.city_id or (vr.remote_ok and u.remote_ok and uc.country = v_country))
            and ct.ask_block_reason(vr.id, u.id) is null
            and not exists (select 1 from applications a where a.role_id = vr.id and a.applicant_id = u.id)
            and not exists (select 1 from applications a join project_roles r2 on r2.id = a.role_id join projects p2 on p2.id = r2.project_id
                            where a.applicant_id = u.id and a.origin = 'INVITED' and p2.owner_id = v_uid and a.created_at > now() - interval '30 days')
            and not exists (select 1 from swipes w where w.actor_id = v_uid and w.target_kind = 'PROFILE' and w.target_id = u.id
                            and w.context_role_id = vr.id and w.direction = 'LEFT')
          order by u.last_active_at desc
          limit greatest(1, least(coalesce(p_limit, 100), 300))) s), '[]'::jsonb);
end $$;

create function public.asks_left_today(p_project uuid) returns int language sql stable security definer set search_path = public, ct as $$
  select greatest(0, 10 - (select count(*) from applications a join project_roles r on r.id = a.role_id
                           where r.project_id = p_project and a.origin = 'INVITED'
                             and (a.created_at at time zone ct.user_tz(auth.uid()))::date = ct.user_today(auth.uid())))::int $$;

-- Null if an Ask may be sent, else the reason to show ("Kabir only takes paid work right now").
create function public.ask_check(p_role uuid, p_profile uuid) returns text language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vr project_roles; vp projects; v text;
begin
  select * into vr from project_roles where id = p_role;
  select * into vp from projects where id = vr.project_id;
  if vp.id is null or vp.owner_id <> v_uid then return 'Only the project owner can send asks.'; end if;
  if vr.status <> 'OPEN' or vp.status <> 'OPEN' then return 'This role isn''t open.'; end if;
  v := ct.ask_block_reason(p_role, p_profile);
  if v is not null then return v; end if;
  if exists (select 1 from applications where role_id = p_role and applicant_id = p_profile) then return 'Already asked, or they applied.'; end if;
  if public.asks_left_today(vp.id) <= 0 then return 'You''ve sent 10 asks for this project today. More tomorrow.'; end if;
  return null;
end $$;

create function public.send_ask(p_role uuid, p_profile uuid, p_note text default null) returns jsonb
language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vr project_roles; vp projects; vo profiles; va applications; v_reason text;
begin
  select * into vr from project_roles where id = p_role for update;
  select * into vp from projects where id = vr.project_id;
  if vp.id is null or vp.owner_id <> v_uid then perform ct.fail('Only the project owner can send asks.', 'NOT_OWNER'); end if;
  if vr.status <> 'OPEN' or vp.status <> 'OPEN' then perform ct.fail('This role isn''t open.', 'ROLE_CLOSED'); end if;
  v_reason := ct.ask_block_reason(p_role, p_profile);
  if v_reason is not null then perform ct.fail(v_reason, 'ASK_BLOCKED'); end if;
  if exists (select 1 from applications where role_id = p_role and applicant_id = p_profile) then
    perform ct.fail('Already asked, or they applied.', 'DUPLICATE');
  end if;
  if public.asks_left_today(vp.id) <= 0 then perform ct.fail('You''ve sent 10 asks for this project today. More tomorrow.', 'ASK_CAP'); end if;
  if char_length(coalesce(p_note, '')) > 140 then perform ct.fail('Keep the note to 140 characters.', 'NOTE_LENGTH'); end if;
  select * into vo from profiles where id = v_uid;
  insert into applications (role_id, applicant_id, origin, note, note_flags, is_rebook, status)
  values (p_role, p_profile, 'INVITED', nullif(btrim(coalesce(p_note, '')), ''), ct.message_flags(p_note, true),
          ct.on_keep_list(v_uid, p_profile), 'PENDING')
  returning * into va;
  perform ct.notify(p_profile, 'ASK_RECEIVED', 'Would you be ' || ct.role_label(vr.role_code) || '?',
    ct.short_name(vo.display_name) || ' asks: ' || ct.role_label(vr.role_code) || ' on "' || vp.title || '" · '
      || ct.when_line(vr, vp) || ' · ' || ct.pay_line(vr, vp),
    'calltime://r/' || vr.id, jsonb_build_object('application_id', va.id, 'role_id', vr.id));
  update profiles set last_active_at = now() where id = v_uid;
  return ct.app_json(va);
end $$;

create function public.respond_ask(p_app uuid, p_accept boolean, p_reason text default null) returns jsonb
language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); va applications; vr project_roles; vp projects; vm matches; vu profiles;
begin
  select * into va from applications where id = p_app for update;
  if not found or va.applicant_id <> v_uid then perform ct.fail('Ask not found.', 'NOT_FOUND'); end if;
  if va.origin <> 'INVITED' or va.status <> 'PENDING' then perform ct.fail('This ask was already answered.', 'ALREADY_DECIDED'); end if;
  select * into vr from project_roles where id = va.role_id;
  select * into vp from projects where id = vr.project_id;
  select * into vu from profiles where id = v_uid;
  if p_reason is not null and not (p_reason = any (ct.e_ask_decline())) then perform ct.fail('Pick a reason from the list.', 'BAD_REASON'); end if;
  if coalesce(p_accept, false) then
    if ct.blocked(v_uid, vp.owner_id) then perform ct.fail('This ask is no longer available.', 'BLOCKED'); end if;
    if vr.status <> 'OPEN' or vp.status <> 'OPEN' then
      update applications set status = 'CLOSED', decided_at = now() where id = va.id;
      perform ct.fail('This role was filled meanwhile. Thanks for saying yes.', 'ROLE_CLOSED');
    end if;
    update applications set status = 'MATCHED', decided_at = now() where id = va.id;
    vm := ct.create_match(va.id);
    perform ct.notify(vp.owner_id, 'MATCH_CREATED', 'In talks',
      ct.short_name(vu.display_name) || ' said yes to ' || ct.role_label(vr.role_code) || ' on "' || vp.title || '".',
      'calltime://m/' || vm.id, jsonb_build_object('match_id', vm.id));
    update profiles set last_active_at = now() where id = v_uid;
    return ct.match_json(vm);
  end if;
  update applications set status = 'DECLINED', decline_reason = p_reason, decided_at = now() where id = va.id;
  perform ct.notify(vp.owner_id, 'SYSTEM', 'Ask declined',
    ct.short_name(vu.display_name) || ' can''t do ' || ct.role_label(vr.role_code) || ' on "' || vp.title || '"'
      || coalesce(' · ' || case p_reason when 'DATES_DONT_WORK' then 'Dates don''t work' when 'TOO_FAR' then 'Too far'
                                         when 'NOT_MY_KIND' then 'Not my kind of project' when 'PAID_ONLY_NOW' then 'Paid work only right now' end, '') || '.',
    'calltime://p/' || vp.id);
  return null;
end $$;

-- ───────────────────────────────────────── 13. RPCs: Inbox, chat, auditions, booking ─────────────────────────────────────────

create function public.get_inbox() returns jsonb language sql stable security definer set search_path = public, ct as $$
  select jsonb_build_object(
    'asks', coalesce((select jsonb_agg(jsonb_build_object('application', ct.app_json(a), 'item', ct.rwp_json(a.role_id, auth.uid()),
                                                         'is_rebook', a.is_rebook) order by a.created_at desc)
                      from applications a join project_roles r on r.id = a.role_id join projects p on p.id = r.project_id
                      where a.applicant_id = auth.uid() and a.origin = 'INVITED' and a.status = 'PENDING'
                        and not ct.blocked(auth.uid(), p.owner_id)), '[]'::jsonb),
    'conversations', coalesce((select jsonb_agg(c.r_json order by c.last_at desc) from (
        select jsonb_build_object(
          'match', ct.match_json(m), 'counterpart_name', ct.short_name(u.display_name), 'counterpart_initials', ct.initials(u.display_name),
          'role_code', r.role_code, 'project_title', p.title, 'is_owner', m.owner_id = auth.uid(),
          'last_message', lm.body, 'last_at', ct.ms(coalesce(lm.created_at, m.created_at)),
          'unread', (select count(*) from messages x where x.match_id = m.id and x.sender_id is distinct from auth.uid()
                     and x.created_at > coalesce(case when m.owner_id = auth.uid() then m.owner_read_at else m.collab_read_at end, '-infinity'::timestamptz))
        ) as r_json, coalesce(lm.created_at, m.created_at) as last_at
        from matches m join projects p on p.id = m.project_id join project_roles r on r.id = m.role_id
             join profiles u on u.id = case when m.owner_id = auth.uid() then m.collaborator_id else m.owner_id end
             left join lateral (select x.body, x.created_at from messages x where x.match_id = m.id order by x.created_at desc limit 1) lm on true
        where auth.uid() in (m.owner_id, m.collaborator_id) and not ct.blocked(m.owner_id, m.collaborator_id)) c), '[]'::jsonb),
    'updates', coalesce((select jsonb_agg((to_jsonb(n) - 'created_at' - 'read_at') || jsonb_build_object('created_at', ct.ms(n.created_at), 'read_at', ct.ms(n.read_at))
                                          order by n.created_at desc)
                         from (select * from notifications n where n.user_id = auth.uid() and n.kind <> 'MESSAGE'
                               order by n.created_at desc limit 50) n), '[]'::jsonb)) $$;

create function public.get_match_view(p_match uuid) returns jsonb language plpgsql stable security definer set search_path = public, ct as $$
declare v_uid uuid := auth.uid(); vm matches; v_other uuid; v_lat float8; v_lng float8;
begin
  select * into vm from matches where id = p_match;
  if not found or v_uid is null or v_uid not in (vm.owner_id, vm.collaborator_id) then return null; end if;
  v_other := case when vm.owner_id = v_uid then vm.collaborator_id else vm.owner_id end;
  select a.lat, a.lng into v_lat, v_lng from profiles me join areas a on a.id = me.area_id where me.id = v_uid;
  return jsonb_build_object(
    'match', ct.match_json(vm), 'item', ct.rwp_json(vm.role_id, v_uid),
    'counterpart', ct.card_json(v_other, true, vm.owner_id = v_uid, v_lat, v_lng),
    'is_owner', vm.owner_id = v_uid,
    'current_card', (select ct.card_row_json(b) from booking_cards b where b.match_id = vm.id order by b.version desc limit 1),
    'accepted_card', (select ct.card_row_json(b) from booking_cards b where b.match_id = vm.id and b.status = 'ACCEPTED' order by b.version desc limit 1));
end $$;

create function public.mark_read(p_match uuid) returns void language sql security definer set search_path = public as $$
  update public.matches set owner_read_at = case when owner_id = auth.uid() then now() else owner_read_at end,
                            collab_read_at = case when collaborator_id = auth.uid() then now() else collab_read_at end
  where id = p_match and auth.uid() in (owner_id, collaborator_id) $$;

-- F20. Owner of a cast-role match in talks. Mode locked to the listing; in-person 08:00-21:00; >= 1 other person present.
create function public.send_audition_request(p_match uuid, p_payload jsonb) returns jsonb
language plpgsql security definer set search_path = public, ct as $$
declare
  v_uid uuid := ct.require_uid(); vm matches; vr project_roles; vp projects; j jsonb := coalesce(p_payload, '{}');
  v_mode text := j ->> 'mode'; v_date date; v_due date; v_time time; v_also text[]; v_req jsonb; v_body text; vmsg messages; v_today date;
begin
  select * into vm from matches where id = p_match;
  if not found or vm.owner_id <> v_uid then perform ct.fail('Only the project owner can request an audition.', 'NOT_OWNER'); end if;
  select * into vr from project_roles where id = vm.role_id;
  select * into vp from projects where id = vm.project_id;
  if ct.role_dept(vr.role_code) <> 'CAST' then perform ct.fail('Auditions are for cast roles only.', 'NOT_CAST'); end if;
  if vm.state <> 'TALKING' then perform ct.fail('Request auditions while you''re in talks, before the booking card.', 'BAD_STATE'); end if;
  if ct.blocked(vm.owner_id, vm.collaborator_id) then perform ct.fail('This conversation has ended.', 'BLOCKED'); end if;
  if vp.audition_mode is null or v_mode is distinct from vp.audition_mode then
    perform ct.fail('This listing says auditions are: ' || ct.audition_label(vp.audition_mode) || '. Use that.', 'AUDITION_MODE');
  end if;
  v_today := ct.city_today(vp.city_id);
  v_date := (j ->> 'date')::date; v_due := (j ->> 'due_date')::date; v_time := (j ->> 'time')::time;
  v_also := ct.jarr(j -> 'also_present');
  if v_mode = 'SELF_TAPE' then
    if v_due is null or v_due < v_today then perform ct.fail('Set a due date for the tape (today or later).', 'DUE_DATE'); end if;
    if coalesce(j ->> 'sides_url', '') <> '' and (j ->> 'sides_url') !~* '^https?://' then perform ct.fail('The sides link must start with https://', 'SIDES_URL'); end if;
  elsif v_mode = 'VIDEO_CALL' then
    if v_date is null or v_time is null or v_date < v_today then perform ct.fail('Pick a date and time for the video call.', 'DATE_TIME'); end if;
  else
    if v_date is null or v_time is null or v_date < v_today then perform ct.fail('Pick a date and time.', 'DATE_TIME'); end if;
    if v_time < time '08:00' or v_time > time '21:00' then perform ct.fail('In-person auditions happen between 08:00 and 21:00.', 'HOURS'); end if;
    if btrim(coalesce(j ->> 'venue', '')) = '' or btrim(coalesce(j ->> 'address', '')) = '' then
      perform ct.fail('Add the venue name and address.', 'VENUE');
    end if;
    if cardinality(v_also) < 1 then perform ct.fail('Say who else will be there (at least one person, by role).', 'ALSO_PRESENT'); end if;
  end if;
  v_req := jsonb_build_object('mode', v_mode,
    'sides_url', case when v_mode = 'SELF_TAPE' then nullif(j ->> 'sides_url', '') end,
    'due_date', case when v_mode = 'SELF_TAPE' then v_due end,
    'date', case when v_mode <> 'SELF_TAPE' then v_date end,
    'time', case when v_mode <> 'SELF_TAPE' then left(v_time::text, 5) end,
    'meeting_link', case when v_mode = 'VIDEO_CALL' then nullif(j ->> 'meeting_link', '') end,
    'venue', case when v_mode = 'STUDIO_OR_PUBLIC_WITH_OTHERS' then btrim(j ->> 'venue') end,
    'address', case when v_mode = 'STUDIO_OR_PUBLIC_WITH_OTHERS' then btrim(j ->> 'address') end,
    'also_present', case when v_mode = 'STUDIO_OR_PUBLIC_WITH_OTHERS' then to_jsonb(v_also) else '[]'::jsonb end,
    'note', left(nullif(btrim(coalesce(j ->> 'note', '')), ''), 280));
  v_body := 'Audition request · ' || ct.audition_label(v_mode) || ' · '
    || case when v_mode = 'SELF_TAPE' then 'tape due ' || to_char(v_due, 'Dy FMDD Mon')
            else to_char(v_date, 'Dy FMDD Mon') || ' ' || left(v_time::text, 5) end
    || ' · No nudity at auditions · No fees · Bring a friend if you like · You never have to meet alone';
  insert into messages (match_id, sender_id, kind, body, payload)
  values (vm.id, v_uid, 'AUDITION', v_body, jsonb_build_object('request', v_req, 'response', null, 'tape_url', null))
  returning * into vmsg;
  perform ct.notify(vm.collaborator_id, 'MESSAGE', 'Audition request', v_body, 'calltime://m/' || vm.id, jsonb_build_object('match_id', vm.id));
  return ct.message_json(vmsg);
end $$;

create function public.respond_audition(p_message uuid, p_response text, p_tape_url text default null) returns jsonb
language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vmsg messages; vm matches;
begin
  select * into vmsg from messages where id = p_message for update;
  if not found or vmsg.kind <> 'AUDITION' then perform ct.fail('Audition request not found.', 'NOT_FOUND'); end if;
  select * into vm from matches where id = vmsg.match_id;
  if vm.collaborator_id <> v_uid then perform ct.fail('Only the person invited can answer.', 'NOT_COLLABORATOR'); end if;
  if not (p_response = any (ct.e_aud_response())) then perform ct.fail('Unknown answer.', 'BAD_RESPONSE'); end if;
  if p_response = 'TAPE_SENT' then
    if vmsg.payload #>> '{request,mode}' <> 'SELF_TAPE' then perform ct.fail('Tapes are only for self-tape auditions.', 'NOT_SELF_TAPE'); end if;
    if coalesce(p_tape_url, '') !~* '^https?://' then perform ct.fail('Paste a link to your tape (https://...).', 'TAPE_URL'); end if;
  end if;
  update messages set payload = jsonb_set(jsonb_set(coalesce(payload, '{}'), '{response}', to_jsonb(p_response)),
                                          '{tape_url}', coalesce(to_jsonb(p_tape_url), 'null'::jsonb))
   where id = vmsg.id returning * into vmsg;
  insert into messages (match_id, sender_id, kind, body)
  values (vm.id, v_uid, 'TEXT', case p_response when 'WILL_DO' then 'I''ll do it.' when 'CANT_MAKE_IT' then 'Can''t make that time.'
                                               else 'Here''s my tape: ' || p_tape_url end);
  perform ct.notify(vm.owner_id, 'MESSAGE', 'Audition answer',
    case p_response when 'WILL_DO' then 'They''ll do the audition.' when 'CANT_MAKE_IT' then 'They can''t make that time.' else 'Tape sent.' end,
    'calltime://m/' || vm.id, jsonb_build_object('match_id', vm.id));
  return ct.message_json(vmsg);
end $$;

-- BookingDiff keys (domain/Booking.kt) between two terms JSON objects.
create function ct.terms_diff(a jsonb, b jsonb) returns text[] language sql immutable as $$
  select coalesce(array_agg(k.kt order by k.kt), '{}') from (values
      ('dates', 'dates', true), ('delivery_date', 'deliveryDate', false), ('amount', 'amount', false), ('kit_fee_day', 'kitFeeDay', false),
      ('payment_terms', 'paymentTerms', false), ('perks', 'perks', true), ('meals', 'meals', false), ('transport', 'transport', false),
      ('call_window', 'callWindow', false), ('credit_title', 'creditTitle', false), ('notes', 'notes', false),
      ('revision_rounds', 'revisionRounds', false), ('picture_lock_date', 'pictureLockDate', false)) as k(ks, kt, is_set)
  where case when k.is_set
             then array(select x from jsonb_array_elements_text(coalesce(a -> k.ks, '[]')) x order by x)
                  is distinct from array(select x from jsonb_array_elements_text(coalesce(b -> k.ks, '[]')) x order by x)
             else nullif(a -> k.ks, 'null'::jsonb) is distinct from nullif(b -> k.ks, 'null'::jsonb) end $$;

create function public.send_booking_card(p_match uuid, p_terms jsonb) returns jsonb
language plpgsql security definer set search_path = public, ct as $$
declare
  v_uid uuid := ct.require_uid(); vm matches; vr project_roles; vp projects; vprev booking_cards; vc booking_cards; vo profiles;
  t jsonb := coalesce(p_terms, '{}'); v_ver int; v_changed text[] := '{}';
begin
  select * into vm from matches where id = p_match for update;
  if not found then perform ct.fail('Conversation not found.', 'NOT_FOUND'); end if;
  select * into vp from projects where id = vm.project_id;
  select * into vr from project_roles where id = vm.role_id;
  if vp.payer_id <> v_uid then perform ct.fail('Only the payer sends the booking card.', 'NOT_PAYER'); end if;
  if vm.state not in ('TALKING', 'OFFERED', 'BOOKED') then perform ct.fail('This conversation has ended.', 'BAD_STATE'); end if;
  if ct.blocked(vm.owner_id, vm.collaborator_id) then perform ct.fail('This conversation has ended.', 'BLOCKED'); end if;
  if not (coalesce(t ->> 'comp_type', '') = any (ct.e_comp_type())) then perform ct.fail('Say how this role is paid.', 'COMP_TYPE'); end if;
  if t ->> 'comp_type' <> 'UNPAID' and (coalesce((t ->> 'amount')::int, 0) <= 0 or t ->> 'payment_terms' is null) then
    perform ct.fail('Add the amount and when it''s paid.', 'AMOUNT');
  end if;
  if vm.is_post_role then
    if t ->> 'delivery_date' is null then perform ct.fail('Add a delivery date.', 'DELIVERY_DATE'); end if;
  elsif (case when jsonb_typeof(t -> 'dates') = 'array' then jsonb_array_length(t -> 'dates') else 0 end) = 0 then
    perform ct.fail('Add the shoot dates.', 'DATES');
  end if;
  if char_length(coalesce(t ->> 'notes', '')) > 280 then perform ct.fail('Keep notes to 280 characters.', 'NOTES'); end if;
  select * into vo from profiles where id = vp.payer_id;
  t := t || jsonb_build_object('payer_id', vp.payer_id, 'payer_name', ct.short_name(vo.display_name),
                               'role_code', coalesce(t ->> 'role_code', vr.role_code), 'is_post_role', vm.is_post_role,
                               'currency', coalesce(t ->> 'currency', vr.currency), 'project_title', coalesce(t ->> 'project_title', vp.title));
  select * into vprev from booking_cards where match_id = vm.id order by version desc limit 1;
  v_ver := coalesce(vprev.version, 0) + 1;
  if vprev.id is not null then v_changed := ct.terms_diff(vprev.terms, t); end if;
  update booking_cards set status = 'SUPERSEDED' where match_id = vm.id and status = 'SENT';
  insert into booking_cards (match_id, version, terms, status, changed_fields, sent_by)
  values (vm.id, v_ver, t, 'SENT', v_changed, v_uid) returning * into vc;
  if vm.state = 'BOOKED' then
    update matches set change_pending = true where id = vm.id;
  else
    update matches set state = 'OFFERED' where id = vm.id;
  end if;
  insert into messages (match_id, sender_id, kind, body, booking_card_id)
  values (vm.id, v_uid, 'BOOKING', 'Booking card v' || v_ver || case when cardinality(v_changed) > 0 then ' · changed: ' || array_to_string(v_changed, ', ') else '' end, vc.id);
  perform ct.notify(vm.collaborator_id, case when v_ver > 1 then 'BOOKING_CHANGED' else 'BOOKING_CARD' end,
    case when v_ver > 1 then 'Booking card changed' else 'Booking card' end,
    ct.short_name(vo.display_name) || ' sent a booking card for ' || ct.role_label(vr.role_code) || ' on "' || vp.title || '" (v' || v_ver || ').',
    'calltime://m/' || vm.id, jsonb_build_object('match_id', vm.id, 'card_id', vc.id));
  return ct.card_row_json(vc);
end $$;

-- Deliverables → wrap items. Falls back to defaults (CREDIT, PAID if paid, FOOTAGE/FINAL_FILM perks, DELIVERY for post roles).
create function ct.upsert_wrap_items(p_match uuid, t jsonb) returns void language plpgsql security definer set search_path = public, ct as $$
declare d jsonb; v_kinds text[] := '{}'; v_kind text; v_last date; v_deliv jsonb;
begin
  v_deliv := case when jsonb_typeof(t -> 'deliverables') = 'array' and jsonb_array_length(t -> 'deliverables') > 0 then t -> 'deliverables' end;
  if v_deliv is null then
    v_last := (select max(x::date) from jsonb_array_elements_text(coalesce(t -> 'dates', '[]')) x);
    v_deliv := jsonb_build_array(jsonb_build_object('kind', 'CREDIT', 'due_date', coalesce(t ->> 'delivery_date', v_last::text)));
    if t ->> 'comp_type' <> 'UNPAID' then
      v_deliv := v_deliv || jsonb_build_object('kind', 'PAID', 'due_date', coalesce(t ->> 'pay_by_date', t ->> 'delivery_date', v_last::text));
    end if;
    if coalesce((t ->> 'is_post_role')::boolean, false) then
      v_deliv := v_deliv || jsonb_build_object('kind', 'DELIVERY', 'due_date', t ->> 'delivery_date');
    elsif t -> 'perks' ? 'FOOTAGE_BY_DATE' then
      v_deliv := v_deliv || jsonb_build_object('kind', 'FOOTAGE', 'due_date', (v_last + 7 * coalesce((t ->> 'footage_due_weeks')::int, 8))::text);
    end if;
    if t -> 'perks' ? 'FINAL_FILM_BY_DATE' then
      v_deliv := v_deliv || jsonb_build_object('kind', 'FINAL_FILM', 'due_date', (coalesce(v_last, (t ->> 'delivery_date')::date) + 7 * coalesce((t ->> 'final_film_due_weeks')::int, 26))::text);
    end if;
  end if;
  for d in select * from jsonb_array_elements(v_deliv) loop
    v_kind := d ->> 'kind';
    continue when v_kind is null or not (v_kind = any (ct.e_wrap_kind())) or v_kind = any (v_kinds);
    v_kinds := v_kinds || v_kind;
    insert into wrap_items (match_id, kind, due_date, owed_by)
    values (p_match, v_kind, (d ->> 'due_date')::date, case when v_kind = 'DELIVERY' then 'COLLABORATOR' else 'PRODUCTION' end)
    on conflict (match_id, kind) do update set due_date = excluded.due_date where wrap_items.status = 'OPEN';
  end loop;
  delete from wrap_items where match_id = p_match and status = 'OPEN' and not (kind = any (v_kinds));
end $$;

create function public.respond_booking_card(p_card uuid, p_accept boolean) returns jsonb
language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vc booking_cards; vm matches; vr project_roles; vp projects; vu profiles; va record; v_first boolean;
begin
  select * into vc from booking_cards where id = p_card for update;
  if not found then perform ct.fail('Booking card not found.', 'NOT_FOUND'); end if;
  select * into vm from matches where id = vc.match_id for update;
  if vm.collaborator_id <> v_uid then perform ct.fail('Only the person being booked can answer.', 'NOT_COLLABORATOR'); end if;
  if vc.status <> 'SENT' or exists (select 1 from booking_cards where match_id = vm.id and version > vc.version) then
    perform ct.fail('This version was replaced or already answered.', 'CARD_STALE');
  end if;
  if ct.blocked(vm.owner_id, vm.collaborator_id) or vm.state not in ('OFFERED', 'TALKING', 'BOOKED') then
    perform ct.fail('This conversation has ended.', 'BAD_STATE');
  end if;
  select * into vr from project_roles where id = vm.role_id for update;
  select * into vp from projects where id = vm.project_id;
  select * into vu from profiles where id = v_uid;
  if coalesce(p_accept, false) then
    v_first := vm.state <> 'BOOKED';
    if v_first and (vr.status <> 'OPEN' or vr.filled_count >= vr.count) then
      perform ct.fail('This role was just filled.', 'ROLE_FILLED');
    end if;
    update booking_cards set status = 'SUPERSEDED' where match_id = vm.id and status = 'ACCEPTED';
    update booking_cards set status = 'ACCEPTED', responded_at = now() where id = vc.id returning * into vc;
    update matches set state = 'BOOKED', booked_at = coalesce(booked_at, now()), change_pending = false where id = vm.id returning * into vm;
    perform ct.recount_role(vr.id);
    select * into vr from project_roles where id = vr.id;
    if vr.status = 'OPEN' and vr.filled_count >= vr.count then
      update project_roles set status = 'FILLED' where id = vr.id;
      for va in update applications set status = 'CLOSED', decided_at = now()
                 where role_id = vr.id and status = 'PENDING' returning applicant_id loop
        perform ct.notify(va.applicant_id, 'APPLICATION_CLOSED', 'Role filled',
          'The ' || ct.role_label(vr.role_code) || ' role on "' || vp.title || '" was filled. Thanks for applying.',
          'calltime://r/' || vr.id);
      end loop;
      perform ct.recount_role(vr.id);
      if not exists (select 1 from project_roles where project_id = vp.id and status = 'OPEN') and vp.status = 'OPEN' then
        update projects set status = 'CREWED' where id = vp.id;
      end if;
    end if;
    perform ct.upsert_wrap_items(vm.id, vc.terms);
    insert into messages (match_id, sender_id, kind, body)
    values (vm.id, null, 'SYSTEM', case when v_first then 'Booked. ' || ct.short_name(vu.display_name) || ' accepted booking card v' || vc.version || '.'
                                        else 'Booking card v' || vc.version || ' accepted. These terms are now in force.' end);
    perform ct.notify(vm.owner_id, 'BOOKING_ACCEPTED', 'Booked',
      ct.short_name(vu.display_name) || ' accepted booking card v' || vc.version || ' for ' || ct.role_label(vr.role_code) || ' on "' || vp.title || '".',
      'calltime://m/' || vm.id, jsonb_build_object('match_id', vm.id, 'card_id', vc.id));
  else
    update booking_cards set status = 'DECLINED', responded_at = now() where id = vc.id returning * into vc;
    update matches set state = case when state = 'OFFERED' then 'TALKING' else state end, change_pending = false where id = vm.id returning * into vm;
    insert into messages (match_id, sender_id, kind, body)
    values (vm.id, null, 'SYSTEM', 'Booking card v' || vc.version || ' declined.'
                                   || case when vm.state = 'BOOKED' then ' The earlier accepted terms stay in force.' else '' end);
    perform ct.notify(vm.owner_id, 'BOOKING_CHANGED', 'Booking card declined',
      ct.short_name(vu.display_name) || ' declined booking card v' || vc.version || '.', 'calltime://m/' || vm.id,
      jsonb_build_object('match_id', vm.id, 'card_id', vc.id));
  end if;
  return ct.match_json(vm);
end $$;

create function public.share_contacts(p_match uuid) returns jsonb language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vm matches;
begin
  select * into vm from matches where id = p_match for update;
  if not found or v_uid not in (vm.owner_id, vm.collaborator_id) then perform ct.fail('Conversation not found.', 'NOT_FOUND'); end if;
  if vm.state not in ('BOOKED', 'WRAPPED') then perform ct.fail('Contacts can be shared once you''re booked.', 'NOT_BOOKED'); end if;
  update matches set contacts_shared_by_owner = contacts_shared_by_owner or (owner_id = v_uid),
                     contacts_shared_by_collab = contacts_shared_by_collab or (collaborator_id = v_uid)
   where id = vm.id returning * into vm;
  return ct.match_json(vm);
end $$;

-- Contacts the counterpart chose to share (email from auth), once booked.
create function public.shared_contacts(p_match uuid) returns jsonb language plpgsql stable security definer set search_path = public, auth, ct as $$
declare v_uid uuid := auth.uid(); vm matches; v_other uuid; v_shared boolean;
begin
  select * into vm from matches where id = p_match;
  if not found or v_uid is null or v_uid not in (vm.owner_id, vm.collaborator_id) then return null; end if;
  v_other := case when vm.owner_id = v_uid then vm.collaborator_id else vm.owner_id end;
  v_shared := case when vm.owner_id = v_uid then vm.contacts_shared_by_collab else vm.contacts_shared_by_owner end;
  if not v_shared or vm.state not in ('BOOKED', 'WRAPPED') then return null; end if;
  return (select jsonb_build_object('email', u.email, 'phone', u.phone) from auth.users u where u.id = v_other);
end $$;

-- ───────────────────────────────────────── 14. RPCs: safety ─────────────────────────────────────────

-- Block hides both people from each other everywhere and ends active matches with a neutral system message (PRD §5.5).
create function public.block_user(p_profile uuid) returns void language plpgsql security definer set search_path = public, ct as $$
declare v_uid uuid := ct.require_uid(); vm record; v_roles uuid[] := '{}'; v_r uuid;
begin
  if p_profile = v_uid then perform ct.fail('You can''t block yourself.', 'SELF'); end if;
  if not exists (select 1 from profiles where id = p_profile) then perform ct.fail('Profile not found.', 'NOT_FOUND'); end if;
  insert into blocks (blocker_id, blocked_id) values (v_uid, p_profile) on conflict do nothing;
  for vm in update matches set state = 'CLOSED', ended_at = now()
             where state in ('TALKING', 'OFFERED', 'BOOKED')
               and ((owner_id = v_uid and collaborator_id = p_profile) or (owner_id = p_profile and collaborator_id = v_uid))
             returning id, role_id loop
    insert into messages (match_id, sender_id, kind, body) values (vm.id, null, 'SYSTEM', 'This conversation has ended.');
    update booking_cards set status = 'CANCELLED' where match_id = vm.id and status = 'SENT';
    v_roles := v_roles || vm.role_id;
  end loop;
  with closed as (
    update applications a set status = 'CLOSED', decided_at = now()
     from project_roles r join projects p on p.id = r.project_id
     where r.id = a.role_id and a.status in ('PENDING', 'BACKUP')
       and ((a.applicant_id = p_profile and p.owner_id = v_uid) or (a.applicant_id = v_uid and p.owner_id = p_profile))
     returning a.role_id)
  select v_roles || coalesce(array_agg(role_id), '{}') into v_roles from closed;
  foreach v_r in array v_roles loop perform ct.recount_role(v_r); end loop;
end $$;

create function public.unblock_user(p_profile uuid) returns void language sql security definer set search_path = public as $$
  delete from public.blocks where blocker_id = auth.uid() and blocked_id = p_profile $$;

-- People I blocked (names only: the block itself is the relationship).
create function public.my_blocked() returns jsonb language sql stable security definer set search_path = public, ct as $$
  select coalesce(jsonb_agg(ct.card_json(b.blocked_id, false, false, null, null) order by b.created_at desc), '[]'::jsonb)
  from blocks b where b.blocker_id = auth.uid() $$;

-- Keep list: people I said I'd work with again (feedback, L1). Empty until wrap + feedback ship.
create function public.keep_list() returns jsonb language sql stable security definer set search_path = public, ct as $$
  select coalesce(jsonb_agg(ct.card_json(s.to_id, false, false, null, null)), '[]'::jsonb) from (
    select distinct f.to_id from feedback f
    where f.from_id = auth.uid() and coalesce((f.answers ->> 'work_again')::boolean, (f.answers ->> 'workAgain')::boolean, false)
      and not ct.blocked(auth.uid(), f.to_id)) s $$;

-- ───────────────────────────────────────── 15. RLS ─────────────────────────────────────────

alter table public.cities enable row level security;
alter table public.areas enable row level security;
alter table public.profiles enable row level security;
alter table public.profile_private enable row level security;
alter table public.profile_roles enable row level security;
alter table public.work_links enable row level security;
alter table public.busy_dates enable row level security;
alter table public.projects enable row level security;
alter table public.project_roles enable row level security;
alter table public.swipes enable row level security;
alter table public.applications enable row level security;
alter table public.matches enable row level security;
alter table public.booking_cards enable row level security;
alter table public.messages enable row level security;
alter table public.shoot_days enable row level security;
alter table public.call_times enable row level security;
alter table public.wrap_items enable row level security;
alter table public.feedback enable row level security;
alter table public.reports enable row level security;
alter table public.blocks enable row level security;
alter table public.notifications enable row level security;
alter table public.events enable row level security;

-- reference: anyone reads, service_role writes
create policy cities_read on public.cities for select to anon, authenticated using (true);
create policy areas_read on public.areas for select to anon, authenticated using (true);

-- profiles: own row only. Other people's cards come from relationship-checked RPCs.
create policy profiles_own_select on public.profiles for select to authenticated using (id = auth.uid());
create policy profiles_own_insert on public.profiles for insert to authenticated with check (id = auth.uid());
create policy profiles_own_update on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());
create policy profiles_own_delete on public.profiles for delete to authenticated using (id = auth.uid());

create policy profile_private_own on public.profile_private for all to authenticated using (id = auth.uid()) with check (id = auth.uid());
create policy profile_roles_own on public.profile_roles for all to authenticated using (profile_id = auth.uid()) with check (profile_id = auth.uid());
create policy work_links_own on public.work_links for all to authenticated using (profile_id = auth.uid()) with check (profile_id = auth.uid());
create policy busy_dates_own on public.busy_dates for all to authenticated using (profile_id = auth.uid()) with check (profile_id = auth.uid());

-- projects: public when not DRAFT; the owner always. exact_address is column-revoked (read via RPC).
create policy projects_read on public.projects for select to anon, authenticated using (status <> 'DRAFT' or owner_id = auth.uid());
create policy projects_insert on public.projects for insert to authenticated
  with check (owner_id = auth.uid() and payer_id = auth.uid() and status = 'DRAFT');
create policy projects_update on public.projects for update to authenticated
  using (owner_id = auth.uid() and status in ('DRAFT', 'OPEN', 'CREWED')) with check (owner_id = auth.uid() and payer_id = auth.uid());
create policy projects_delete on public.projects for delete to authenticated using (owner_id = auth.uid() and status = 'DRAFT');

create policy project_roles_read on public.project_roles for select to anon, authenticated
  using (exists (select 1 from public.projects p where p.id = project_id and (p.status <> 'DRAFT' or p.owner_id = auth.uid())));
create policy project_roles_insert on public.project_roles for insert to authenticated
  with check (exists (select 1 from public.projects p where p.id = project_id and p.owner_id = auth.uid() and p.status in ('DRAFT', 'OPEN', 'CREWED')));
create policy project_roles_update on public.project_roles for update to authenticated
  using (exists (select 1 from public.projects p where p.id = project_id and p.owner_id = auth.uid() and p.status in ('DRAFT', 'OPEN', 'CREWED')))
  with check (exists (select 1 from public.projects p where p.id = project_id and p.owner_id = auth.uid()));
create policy project_roles_delete on public.project_roles for delete to authenticated
  using (exists (select 1 from public.projects p where p.id = project_id and p.owner_id = auth.uid() and p.status in ('DRAFT', 'OPEN', 'CREWED'))
         and not exists (select 1 from public.applications a where a.role_id = project_roles.id));

create policy swipes_own_select on public.swipes for select to authenticated using (actor_id = auth.uid());
create policy swipes_own_insert on public.swipes for insert to authenticated with check (actor_id = auth.uid());
create policy swipes_own_delete on public.swipes for delete to authenticated using (actor_id = auth.uid());

create policy applications_read on public.applications for select to authenticated
  using (applicant_id = auth.uid() or exists (select 1 from public.project_roles r join public.projects p on p.id = r.project_id
                                              where r.id = role_id and p.owner_id = auth.uid()));

create policy matches_read on public.matches for select to authenticated using (auth.uid() in (owner_id, collaborator_id));

create policy booking_cards_read on public.booking_cards for select to authenticated
  using (exists (select 1 from public.matches m where m.id = match_id and auth.uid() in (m.owner_id, m.collaborator_id)));

create policy messages_read on public.messages for select to authenticated
  using (exists (select 1 from public.matches m where m.id = match_id and auth.uid() in (m.owner_id, m.collaborator_id)));
create policy messages_insert on public.messages for insert to authenticated
  with check (sender_id = auth.uid() and kind = 'TEXT'
              and exists (select 1 from public.matches m where m.id = match_id and auth.uid() in (m.owner_id, m.collaborator_id)
                          and m.state not in ('CLOSED', 'RELEASED', 'WITHDRAWN')
                          and not ct.blocked(m.owner_id, m.collaborator_id)));

create policy shoot_days_read on public.shoot_days for select to authenticated
  using (exists (select 1 from public.projects p where p.id = project_id and p.owner_id = auth.uid())
      or exists (select 1 from public.matches m where m.project_id = shoot_days.project_id and m.collaborator_id = auth.uid()
                 and m.state in ('BOOKED', 'WRAPPED')));
create policy call_times_read on public.call_times for select to authenticated
  using (profile_id = auth.uid()
      or exists (select 1 from public.shoot_days d join public.projects p on p.id = d.project_id where d.id = shoot_day_id and p.owner_id = auth.uid())
      or exists (select 1 from public.shoot_days d join public.matches m on m.project_id = d.project_id
                 where d.id = shoot_day_id and m.collaborator_id = auth.uid() and m.state in ('BOOKED', 'WRAPPED')));

create policy wrap_items_read on public.wrap_items for select to authenticated
  using (exists (select 1 from public.matches m where m.id = match_id and auth.uid() in (m.owner_id, m.collaborator_id)));

create policy feedback_own on public.feedback for select to authenticated using (from_id = auth.uid());

create policy reports_own_select on public.reports for select to authenticated using (reporter_id = auth.uid());
create policy reports_own_insert on public.reports for insert to authenticated with check (reporter_id = auth.uid());

create policy blocks_own_select on public.blocks for select to authenticated using (blocker_id = auth.uid());
create policy blocks_own_insert on public.blocks for insert to authenticated with check (blocker_id = auth.uid());
create policy blocks_own_delete on public.blocks for delete to authenticated using (blocker_id = auth.uid());

create policy notifications_own_select on public.notifications for select to authenticated using (user_id = auth.uid());
create policy notifications_own_update on public.notifications for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy events_own_insert on public.events for insert to authenticated with check (user_id = auth.uid());

-- ───────────────────────────────────────── 16. Grants ─────────────────────────────────────────
-- Supabase grants ALL on new public tables to anon/authenticated by default; narrow that to what RLS intends.

revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
grant all on all tables in schema public to service_role;
grant all on all sequences in schema public to service_role;

grant select on public.cities, public.areas to anon, authenticated;

grant select (id, owner_id, payer_id, title, logline, format, runtime_min, page_count, genres, languages, stage, script_status,
              script_readable, lookbook_url, ambition_tier, honest_zero_budget, city_id, area_id, shoot_location_type, story_setting,
              shoot_dates, shoot_start, shoot_end, shoot_days, picture_lock_date, date_certainty, hours_cap, meals, transport,
              travel_allowance_per_day, lodging, content_flags, content_none_confirmed, intimacy_coordinator, stunt_or_armourer,
              guardian_present, safety_measures, audition_mode, bring_friend_welcome, insurance, permits, status, reschedule_count,
              created_at, published_at, updated_at)
  on public.projects to anon, authenticated;
grant insert (id, owner_id, payer_id, title, logline, format, runtime_min, page_count, genres, languages, stage, script_status,
              script_readable, lookbook_url, ambition_tier, honest_zero_budget, city_id, area_id, shoot_location_type, exact_address,
              story_setting, shoot_dates, picture_lock_date, date_certainty, hours_cap, meals, transport, travel_allowance_per_day,
              lodging, content_flags, content_none_confirmed, intimacy_coordinator, stunt_or_armourer, guardian_present,
              safety_measures, audition_mode, bring_friend_welcome, insurance, permits)
  on public.projects to authenticated;
grant update (title, logline, format, runtime_min, page_count, genres, languages, stage, script_status, script_readable,
              lookbook_url, ambition_tier, honest_zero_budget, city_id, area_id, shoot_location_type, exact_address, story_setting,
              shoot_dates, picture_lock_date, date_certainty, hours_cap, meals, transport, travel_allowance_per_day, lodging,
              content_flags, content_none_confirmed, intimacy_coordinator, stunt_or_armourer, guardian_present, safety_measures,
              audition_mode, bring_friend_welcome, insurance, permits)
  on public.projects to authenticated;
grant delete on public.projects to authenticated;
grant select on public.project_cards to anon, authenticated;

grant select on public.project_roles to anon, authenticated;
grant insert (id, project_id, role_code, count, credit_title, scope_includes, scope_excludes, step_up_friendly, level_wanted,
              comp_type, amount_min, amount_max, currency, kit_fee_day, payment_terms, perks, footage_due_weeks, final_film_due_weeks,
              gear_policy, remote_ok, post_deadline, role_details, screening_question, application_cap, sort)
  on public.project_roles to authenticated;
grant update (role_code, count, credit_title, scope_includes, scope_excludes, step_up_friendly, level_wanted, comp_type,
              amount_min, amount_max, currency, kit_fee_day, payment_terms, perks, footage_due_weeks, final_film_due_weeks,
              gear_policy, remote_ok, post_deadline, role_details, screening_question, application_cap, sort)
  on public.project_roles to authenticated;
grant delete on public.project_roles to authenticated;

grant select, insert, update, delete on public.profiles, public.profile_private, public.profile_roles, public.work_links,
  public.busy_dates to authenticated;
grant select, insert, delete on public.swipes to authenticated;
grant select on public.applications, public.matches, public.booking_cards, public.shoot_days, public.call_times,
  public.wrap_items, public.feedback to authenticated;
grant select on public.messages to authenticated;
grant insert (match_id, sender_id, kind, body) on public.messages to authenticated;
grant select on public.reports to authenticated;
grant insert (reporter_id, target_kind, target_id, reason, details) on public.reports to authenticated;
grant select, insert, delete on public.blocks to authenticated;
grant select on public.notifications to authenticated;
grant update (read_at) on public.notifications to authenticated;
grant insert (user_id, name, props) on public.events to authenticated;
grant usage on sequence public.events_id_seq to authenticated;

revoke all on public.profile_cards from anon, authenticated;

-- Functions: nothing callable by default; then the RPC surface explicitly.
revoke execute on all functions in schema public from public, anon, authenticated;
grant execute on all functions in schema public to service_role;
grant execute on function
  public.get_cities(), public.city_density(text), public.nearby_counts(text, int),
  public.discover_roles(text, boolean, int), public.get_role(uuid)
  to anon, authenticated;
grant execute on function
  public.get_my_profile(), public.my_private_min_rate(), public.touch_active(), public.save_profile(jsonb, int),
  public.get_profile(uuid), public.get_reputation(uuid), public.reputation_stats(uuid), public.export_my_data(), public.delete_my_account(),
  public.saved_roles(), public.swipe_role(uuid, text), public.undo_swipe(uuid), public.likes_left_today(),
  public.apply_to_role(uuid, text, uuid, text, boolean),
  public.save_project_draft(jsonb, jsonb), public.get_project_draft(uuid), public.listing_rules_check(uuid), public.listing_rules_ok(uuid),
  public.publish_project(uuid), public.close_role(uuid), public.my_projects(), public.joined_projects(), public.get_project_full(uuid),
  public.get_applicants(uuid), public.decide_application(uuid, text, text), public.scout_candidates(uuid, int),
  public.asks_left_today(uuid), public.ask_check(uuid, uuid), public.send_ask(uuid, uuid, text), public.respond_ask(uuid, boolean, text),
  public.get_inbox(), public.get_match_view(uuid), public.mark_read(uuid),
  public.send_audition_request(uuid, jsonb), public.respond_audition(uuid, text, text),
  public.send_booking_card(uuid, jsonb), public.respond_booking_card(uuid, boolean),
  public.share_contacts(uuid), public.shared_contacts(uuid),
  public.block_user(uuid), public.unblock_user(uuid), public.my_blocked(), public.keep_list()
  to authenticated;

-- ───────────────────────────────────────── 17. Realtime (PRD §9.5) ─────────────────────────────────────────

do $$
begin
  if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    create publication supabase_realtime;
  end if;
end $$;
alter publication supabase_realtime add table public.messages, public.notifications, public.booking_cards, public.applications, public.call_times;
