alter table public.customer_subscriptions
  add column if not exists audio_m3u_url text;
