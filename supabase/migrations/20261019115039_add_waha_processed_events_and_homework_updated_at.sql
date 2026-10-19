-- Mirrors what is applied on production (idempotent).

-- 1) waha-webhook de-duplication table (the Edge Function reads/writes it but it did not exist,
--    so idempotency was silently disabled and every retry logged a warning).
CREATE TABLE IF NOT EXISTS public.waha_processed_events (
  message_id   text        PRIMARY KEY,
  session_name text,
  processed_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_waha_processed_events_processed_at
  ON public.waha_processed_events (processed_at);

-- Server-side only: RLS on with no policies, no client grants.
ALTER TABLE public.waha_processed_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.waha_processed_events FROM PUBLIC, anon, authenticated;
GRANT ALL ON public.waha_processed_events TO service_role;

-- Retention: WAHA only retries within minutes; keep 7 days.
SELECT cron.schedule(
  'purge-waha-processed-events',
  '15 3 * * *',
  $cron$DELETE FROM public.waha_processed_events WHERE processed_at < now() - interval '7 days'$cron$
);

-- 2) homework has updated_at but nothing ever updated it.
DROP TRIGGER IF EXISTS trg_homework_updated_at ON public.homework;
CREATE TRIGGER trg_homework_updated_at
  BEFORE UPDATE ON public.homework
  FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();
