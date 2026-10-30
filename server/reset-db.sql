-- Serlok app-only reset. Supabase system schemas are intentionally untouched.
BEGIN;
DROP TABLE IF EXISTS public.sos_events CASCADE;
DROP TABLE IF EXISTS public.room_members CASCADE;
DROP TABLE IF EXISTS public.rooms CASCADE;
DROP TABLE IF EXISTS public.devices CASCADE;
DROP TABLE IF EXISTS public.refresh_tokens CASCADE;
DROP TABLE IF EXISTS public.users CASCADE;
DROP TABLE IF EXISTS drizzle.__drizzle_migrations;
COMMIT;
