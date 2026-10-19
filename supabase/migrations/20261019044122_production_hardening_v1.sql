-- =====================================================================
-- 20261019044122_production_hardening_v1
-- Mirrors what is applied on production (project oknpfsvmopdsgsfbbdcs).
-- Fully idempotent: safe to run on a fresh DB built from the baseline and
-- on production (where it is already recorded as applied).
-- No data is deleted.
-- =====================================================================

-- ---------------------------------------------------------------------
-- A) Lock down worker-only / internal functions (service_role only).
--    Before this they were callable by anon through PostgREST RPC:
--    claim_notification_event_v2(NULL) returned any teacher's pending
--    notification (PII), mark_*_sent could suppress notifications and
--    invoke_notification_worker_pg_net accepted an arbitrary URL.
-- ---------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.invoke_notification_worker_pg_net(text,text)        FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.claim_notification_event_by_id_v2(text,uuid)        FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.claim_notification_event_v2(uuid)                   FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.claim_notification_event_v2_core(text,uuid)         FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.mark_notification_sent(uuid)                        FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.mark_exam_notification_sent(uuid)                   FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.mark_homework_notification_sent(uuid)               FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.mark_monthly_report_notification_sent(uuid)         FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.mark_recitation_notification_sent(uuid)             FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.finalize_notification_sent_v2(uuid,uuid,uuid,text,uuid,uuid,uuid,text,text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.record_notification_delivery_attempt_v2(uuid,uuid,text,text,jsonb,text)     FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.reconcile_notification_delivery_attempt_v2(uuid,uuid,text,text,jsonb,text)  FROM PUBLIC, anon, authenticated;

-- Trigger functions must not be callable through REST (triggers keep firing).
REVOKE ALL ON FUNCTION public.create_absence_notification_event(),
                       public.create_exam_notification_event(),
                       public.create_homework_notification_event(),
                       public.create_monthly_report_notification_event(),
                       public.create_recitation_notification_event(),
                       public.protect_teacher_account_status()
  FROM PUBLIC, anon, authenticated;

-- Used by the app: authenticated only.
REVOKE EXECUTE ON FUNCTION public.get_student_monthly_performance(int,int,uuid,uuid) FROM PUBLIC, anon;

-- The queue-summary view bypassed RLS (owner = postgres) and was readable by anon.
ALTER VIEW public.v_notification_queue_summary SET (security_invoker = true);
REVOKE ALL ON public.v_notification_queue_summary FROM anon;

-- ---------------------------------------------------------------------
-- 1) Privileges: anon has no policies on any table; remove the grants as
--    defense in depth and stop future objects inheriting them.
-- ---------------------------------------------------------------------
REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM anon;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon;
REVOKE EXECUTE ON FUNCTION public.is_admin(), public.is_active_teacher() FROM PUBLIC, anon;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON TABLES    FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, anon;

-- 2) Pin search_path on the remaining functions
ALTER FUNCTION public.notification_event_metadata_v2(text)              SET search_path = public, pg_temp;
ALTER FUNCTION public.notification_logical_key_v2(text, uuid)           SET search_path = public, pg_temp;
ALTER FUNCTION public.notification_provider_idempotency_key_v2(text)    SET search_path = public, pg_temp;
ALTER FUNCTION public.validate_notification_event()                     SET search_path = public, pg_temp;

-- 3) Avatars bucket is public (URLs work without a policy). Replace the
--    public SELECT policy (allowed listing every teacher's files) with an
--    owner-only one (needed for upsert).
DROP POLICY IF EXISTS "Avatar Public Select Policy" ON storage.objects;
DROP POLICY IF EXISTS "Avatar Owner Select Policy"  ON storage.objects;
CREATE POLICY "Avatar Owner Select Policy" ON storage.objects
  FOR SELECT TO authenticated
  USING (bucket_id = 'teacher-avatars'
         AND split_part(name, '/', 1) = (SELECT auth.uid())::text);

-- 4) RLS performance: evaluate auth.uid()/is_active_teacher() once per query
ALTER POLICY groups_tenant_isolation ON public.groups
  USING      (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY group_days_select ON public.group_days
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
ALTER POLICY group_days_delete ON public.group_days
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
ALTER POLICY group_days_insert ON public.group_days
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
ALTER POLICY group_days_update ON public.group_days
  USING      (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY notifications_select_own ON public.notifications
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
ALTER POLICY notifications_delete_own ON public.notifications
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
ALTER POLICY notifications_insert_own ON public.notifications
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
ALTER POLICY notifications_update_own ON public.notifications
  USING      (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY "Teachers can select own whatsapp session" ON public.teacher_whatsapp_sessions
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- 5) Delete blockers: a student (or teacher) that ever received a notification
--    could not be deleted (NO ACTION FKs from the delivery tables).
ALTER TABLE public.notification_delivery_logicals
  DROP CONSTRAINT IF EXISTS notification_delivery_logicals_student_id_fkey,
  ADD  CONSTRAINT notification_delivery_logicals_student_id_fkey
       FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE,
  DROP CONSTRAINT IF EXISTS notification_delivery_logicals_teacher_id_fkey,
  ADD  CONSTRAINT notification_delivery_logicals_teacher_id_fkey
       FOREIGN KEY (teacher_id) REFERENCES public.teachers(id) ON DELETE CASCADE;

ALTER TABLE public.notification_delivery_attempts
  DROP CONSTRAINT IF EXISTS notification_delivery_attempts_student_id_fkey,
  ADD  CONSTRAINT notification_delivery_attempts_student_id_fkey
       FOREIGN KEY (student_id) REFERENCES public.students(id) ON DELETE CASCADE,
  DROP CONSTRAINT IF EXISTS notification_delivery_attempts_teacher_id_fkey,
  ADD  CONSTRAINT notification_delivery_attempts_teacher_id_fkey
       FOREIGN KEY (teacher_id) REFERENCES public.teachers(id) ON DELETE CASCADE;

-- 6) Covering index for every foreign key that lacks one
DO $$
DECLARE r record; v_cols text; v_name text;
BEGIN
  FOR r IN
    SELECT c.conrelid, c.conname, c.conkey, c.conrelid::regclass::text AS tbl
    FROM pg_constraint c
    WHERE c.contype = 'f'
      AND c.connamespace = 'public'::regnamespace
      AND NOT EXISTS (
        SELECT 1 FROM pg_index i
        WHERE i.indrelid = c.conrelid AND i.indisvalid
          AND (string_to_array(i.indkey::text, ' ')::int2[])[1:array_length(c.conkey, 1)] = c.conkey
      )
  LOOP
    SELECT string_agg(quote_ident(a.attname), ', ' ORDER BY k.ord) INTO v_cols
    FROM unnest(r.conkey) WITH ORDINALITY AS k(attnum, ord)
    JOIN pg_attribute a ON a.attrelid = r.conrelid AND a.attnum = k.attnum;
    v_name := left('idx_fk_' || r.conname, 63);
    EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %s (%s)', v_name, r.tbl, v_cols);
  END LOOP;
END $$;

-- 7) Duplicate unique indexes (the constraint-backed twin is kept)
DROP INDEX IF EXISTS public.uq_exam_notification_events_teacher_exam;
DROP INDEX IF EXISTS public.uq_homework_notification_events_teacher_homework;
DROP INDEX IF EXISTS public.uq_monthly_report_notification_events_teacher_report;
DROP INDEX IF EXISTS public.uq_recitation_notification_events_teacher_recitation;

-- 8) cron.job_run_details grows 1,440 rows/day (worker runs every minute)
SELECT cron.schedule(
  'purge-cron-run-details',
  '0 3 * * *',
  $cron$DELETE FROM cron.job_run_details WHERE end_time < now() - interval '7 days'$cron$
);
