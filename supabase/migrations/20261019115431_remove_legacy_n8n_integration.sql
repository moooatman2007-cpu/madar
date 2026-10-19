-- Mirrors what is applied on production (idempotent).
-- Remove the unused legacy n8n integration. It sent every new attendance / exam /
-- monthly-report row to an external n8n webhook. Notifications are handled by the
-- notification worker, so nothing depends on these.

-- 1) triggers first
DROP TRIGGER IF EXISTS trg_attendance_inserted      ON public.attendance;
DROP TRIGGER IF EXISTS trg_exam_inserted            ON public.exams;
DROP TRIGGER IF EXISTS trg_monthly_report_inserted  ON public.monthly_reports;

-- 2) then the functions (callers before the shared sender)
DROP FUNCTION IF EXISTS public.fn_send_attendance_to_n8n();
DROP FUNCTION IF EXISTS public.fn_send_exam_to_n8n();
DROP FUNCTION IF EXISTS public.fn_send_monthly_report_to_n8n();
DROP FUNCTION IF EXISTS public.fn_send_recitation_to_n8n();
DROP FUNCTION IF EXISTS public.fn_send_studentmanager_event(text, jsonb);

-- 3) the webhook secret for the removed integration
DELETE FROM vault.secrets WHERE name = 'studentmanager_n8n_webhook';
