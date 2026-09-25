-- PL/pgSQL variables named actor / window_start collide with rate_limits columns.

create or replace function public.assert_rate_limit(
  p_bucket text,
  p_limit integer default 8,
  p_window interval default interval '1 minute'
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_actor text := left(public.client_ip(), 120);
  v_window_start timestamptz := date_trunc('minute', now());
  hits integer;
begin
  insert into public.rate_limits (bucket, actor, window_start, hit_count)
  values (p_bucket, v_actor, v_window_start, 1)
  on conflict (bucket, actor, window_start)
  do update set hit_count = public.rate_limits.hit_count + 1
  returning public.rate_limits.hit_count into hits;

  delete from public.rate_limits
  where public.rate_limits.window_start < now() - p_window;

  if hits > p_limit then
    raise exception 'Too many attempts. Please wait a minute and try again.';
  end if;
end;
$$;

create or replace function public.assert_named_rate_limit(
  p_bucket text,
  p_actor text,
  p_limit integer default 60
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_window_start timestamptz := date_trunc('minute', now());
  hits integer;
begin
  insert into public.rate_limits (bucket, actor, window_start, hit_count)
  values (p_bucket, left(coalesce(p_actor, 'anon'), 120), v_window_start, 1)
  on conflict (bucket, actor, window_start)
  do update set hit_count = public.rate_limits.hit_count + 1
  returning public.rate_limits.hit_count into hits;

  delete from public.rate_limits
  where public.rate_limits.window_start < now() - interval '10 minutes';

  if hits > greatest(p_limit, 1) then
    raise exception 'Too many attempts. Please wait a minute and try again.';
  end if;
end;
$$;

grant execute on function public.assert_rate_limit(text, integer, interval) to anon, authenticated;
grant execute on function public.assert_named_rate_limit(text, text, integer) to service_role;
