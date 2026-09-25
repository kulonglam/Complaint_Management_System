create extension if not exists pgcrypto;

create or replace function public.hash_tracking_code(p_code text)
returns text
language sql
immutable
set search_path = public, extensions
as $$
  select encode(digest(convert_to(upper(trim(p_code)), 'UTF8'), 'sha256'::text), 'hex');
$$;
