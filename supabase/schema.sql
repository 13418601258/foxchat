-- FoxChat fixed two-person room schema.
-- Run this in the Supabase SQL editor after enabling anonymous sign-ins.

create extension if not exists pgcrypto;

create table if not exists public.rooms (
    id text primary key,
    background_uri text,
    created_at timestamptz not null default now()
);

alter table public.rooms add column if not exists background_uri text;

create table if not exists public.room_members (
    room_id text not null references public.rooms(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    participant_role text not null check (participant_role in ('A', 'B')),
    device_id text not null,
    analysis_consent boolean not null default false,
    avatar text,
    updated_at timestamptz not null default now(),
    primary key (room_id, participant_role),
    unique (room_id, user_id)
);

create table if not exists public.messages (
    id uuid primary key,
    conversation_id text not null references public.rooms(id) on delete cascade,
    sender_id text not null,
    sender_role text not null check (sender_role in ('A', 'B')),
    type text not null check (type in ('TEXT', 'IMAGE', 'AUDIO', 'SYSTEM', 'CHECKIN', 'CHECKIN_REPLY')),
    text text,
    media_path text,
    media_mime_type text,
    media_duration_ms bigint not null default 0,
    reply_to_message_id uuid references public.messages(id) on delete set null,
    created_at_ms bigint not null,
    edited_at_ms bigint,
    recalled_at_ms bigint,
    delivery_status text not null default 'SENT',
    read_at_ms bigint
);

create index if not exists messages_conversation_created_idx
    on public.messages(conversation_id, created_at_ms);

create table if not exists public.weekly_reports (
    id text primary key,
    conversation_id text not null references public.rooms(id) on delete cascade,
    week_key text not null,
    summary text not null,
    topics text not null default '',
    mood_trend text not null default '',
    interaction_change text not null default '',
    important_events text not null default '',
    created_at timestamptz not null default now(),
    unique(conversation_id, week_key)
);

create or replace function public.pair_device(
    room_key text,
    participant_role text,
    device_id text
) returns table(room_id text)
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
    derived_room_id text;
    member_count integer;
begin
    if pair_device.participant_role not in ('A', 'B') then
        raise exception 'participant_role must be A or B';
    end if;

    derived_room_id := 'room_' || substr(encode(digest(pair_device.room_key, 'sha256'), 'hex'), 1, 32);
    insert into public.rooms(id) values (derived_room_id) on conflict do nothing;

    select count(*) into member_count
    from public.room_members
    where public.room_members.room_id = derived_room_id;

    if member_count >= 2 and not exists (
        select 1 from public.room_members
        where public.room_members.room_id = derived_room_id
          and public.room_members.user_id = auth.uid()
    ) then
        raise exception 'room already has two participants';
    end if;

    insert into public.room_members(room_id, user_id, participant_role, device_id)
    values (derived_room_id, auth.uid(), pair_device.participant_role, pair_device.device_id)
    on conflict on constraint room_members_pkey do update set
        user_id = excluded.user_id,
        device_id = excluded.device_id,
        updated_at = now();

    return query select derived_room_id;
end;
$$;

grant execute on function public.pair_device(text, text, text) to authenticated;

create or replace function public.set_analysis_consent(
    room_id text,
    consent boolean
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.room_members
    set analysis_consent = consent,
        updated_at = now()
    where room_members.room_id = set_analysis_consent.room_id
      and room_members.user_id = auth.uid();
end;
$$;

grant execute on function public.set_analysis_consent(text, boolean) to authenticated;

create or replace function public.set_avatar(
    room_id text,
    avatar text
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.room_members
    set avatar = set_avatar.avatar,
        updated_at = now()
    where room_members.room_id = set_avatar.room_id
      and room_members.user_id = auth.uid();
end;
$$;

grant execute on function public.set_avatar(text, text) to authenticated;

create or replace function public.set_room_background(
    room_id text,
    background_uri text
) returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if not exists (
        select 1 from public.room_members
        where room_members.room_id = set_room_background.room_id
          and room_members.user_id = auth.uid()
    ) then
        raise exception 'not a member';
    end if;

    update public.rooms
    set background_uri = set_room_background.background_uri
    where rooms.id = set_room_background.room_id;
end;
$$;

grant execute on function public.set_room_background(text, text) to authenticated;

create or replace function public.is_room_member(
    target_room_id text,
    target_user_id uuid
) returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1
        from public.room_members
        where public.room_members.room_id = target_room_id
          and public.room_members.user_id = target_user_id
    );
$$;

grant execute on function public.is_room_member(text, uuid) to authenticated;

alter table public.rooms enable row level security;
alter table public.room_members enable row level security;
alter table public.messages enable row level security;
alter table public.weekly_reports enable row level security;

create policy "room members can view their room"
on public.rooms for select to authenticated
using (exists (
    select 1 from public.room_members
    where room_members.room_id = rooms.id
      and room_members.user_id = auth.uid()
));

drop policy if exists "members can view participants" on public.room_members;

create policy "members can view participants"
on public.room_members for select to authenticated
using (
    user_id = auth.uid()
    or public.is_room_member(public.room_members.room_id, auth.uid())
);

create policy "members can read messages"
on public.messages for select to authenticated
using (exists (
    select 1 from public.room_members
    where room_members.room_id = messages.conversation_id
      and room_members.user_id = auth.uid()
));

create policy "members can write messages"
on public.messages for insert to authenticated
with check (exists (
    select 1 from public.room_members
    where room_members.room_id = messages.conversation_id
      and room_members.user_id = auth.uid()
      and room_members.participant_role = messages.sender_role
));

create policy "members can update messages"
on public.messages for update to authenticated
using (exists (
    select 1 from public.room_members
    where room_members.room_id = messages.conversation_id
      and room_members.user_id = auth.uid()
      and room_members.participant_role = messages.sender_role
))
with check (true);

create policy "members can read reports"
on public.weekly_reports for select to authenticated
using (exists (
    select 1 from public.room_members
    where room_members.room_id = weekly_reports.conversation_id
      and room_members.user_id = auth.uid()
));

create policy "members can write reports"
on public.weekly_reports for insert to authenticated
with check (exists (
    select 1 from public.room_members
    where room_members.room_id = weekly_reports.conversation_id
      and room_members.user_id = auth.uid()
));

insert into storage.buckets(id, name, public)
values ('chat-media', 'chat-media', false)
on conflict (id) do nothing;

create policy "members can read chat media"
on storage.objects for select to authenticated
using (bucket_id = 'chat-media' and exists (
    select 1 from public.room_members
    where room_members.room_id = split_part(name, '/', 1)
      and room_members.user_id = auth.uid()
));

create policy "members can upload chat media"
on storage.objects for insert to authenticated
with check (bucket_id = 'chat-media' and exists (
    select 1 from public.room_members
    where room_members.room_id = split_part(name, '/', 1)
      and room_members.user_id = auth.uid()
));

-- 应用自更新：公开桶，存放 latest.json 与 APK
insert into storage.buckets(id, name, public)
values ('releases', 'releases', true)
on conflict (id) do nothing;
