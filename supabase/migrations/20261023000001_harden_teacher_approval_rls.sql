-- =====================================================================
-- 20261023000001_harden_teacher_approval_rls.sql
-- Comprehensive hardening for teacher approval, trigger protection, and RLS:
-- 1. Hardens protect_teacher_account_status() trigger function so regular
--    teachers cannot modify their own id or account_status, while allowing
--    authorized admins (public.is_admin()) to manage approval/suspension.
-- 2. Re-asserts and updates tenant RLS policies across all core tables
--    (students, grades, attendance, recitations, exams, homework, lesson_payments)
--    to explicitly require (SELECT public.is_active_teacher()).
-- =====================================================================

-- Part 1: Trigger and function protection
CREATE OR REPLACE FUNCTION public.protect_teacher_account_status()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
  -- Allow admins to change account_status
  IF public.is_admin() THEN
    RETURN NEW;
  END IF;

  -- Prevent non-admins from changing id
  IF OLD.id IS DISTINCT FROM NEW.id THEN
    RAISE EXCEPTION 'Unauthorized: cannot change teacher id';
  END IF;

  -- Prevent non-admins from changing account_status
  IF OLD.account_status IS DISTINCT FROM NEW.account_status THEN
    RAISE EXCEPTION 'Unauthorized: cannot change account status';
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_teacher_account_status ON public.teachers;
CREATE TRIGGER trg_protect_teacher_account_status
  BEFORE UPDATE ON public.teachers
  FOR EACH ROW
  EXECUTE FUNCTION public.protect_teacher_account_status();

REVOKE ALL ON FUNCTION public.protect_teacher_account_status() FROM PUBLIC, anon, authenticated;

-- Part 2: Re-assert RLS tenant isolation with is_active_teacher() on core tables
-- Students
ALTER POLICY IF EXISTS students_select_own ON public.students;
CREATE POLICY students_select_own ON public.students
  FOR SELECT TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS students_insert_own ON public.students;
CREATE POLICY students_insert_own ON public.students
  FOR INSERT TO authenticated
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS students_update_own ON public.students;
CREATE POLICY students_update_own ON public.students
  FOR UPDATE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS students_delete_own ON public.students;
CREATE POLICY students_delete_own ON public.students
  FOR DELETE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Grades
ALTER POLICY IF EXISTS grades_select_own ON public.grades;
CREATE POLICY grades_select_own ON public.grades
  FOR SELECT TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS grades_insert_own ON public.grades;
CREATE POLICY grades_insert_own ON public.grades
  FOR INSERT TO authenticated
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS grades_update_own ON public.grades;
CREATE POLICY grades_update_own ON public.grades
  FOR UPDATE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS grades_delete_own ON public.grades;
CREATE POLICY grades_delete_own ON public.grades
  FOR DELETE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Attendance
ALTER POLICY IF EXISTS attendance_select_own ON public.attendance;
CREATE POLICY attendance_select_own ON public.attendance
  FOR SELECT TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS attendance_insert_own ON public.attendance;
CREATE POLICY attendance_insert_own ON public.attendance
  FOR INSERT TO authenticated
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS attendance_update_own ON public.attendance;
CREATE POLICY attendance_update_own ON public.attendance
  FOR UPDATE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS attendance_delete_own ON public.attendance;
CREATE POLICY attendance_delete_own ON public.attendance
  FOR DELETE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Recitations
ALTER POLICY IF EXISTS recitations_select_own ON public.recitations;
CREATE POLICY recitations_select_own ON public.recitations
  FOR SELECT TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS recitations_insert_own ON public.recitations;
CREATE POLICY recitations_insert_own ON public.recitations
  FOR INSERT TO authenticated
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS recitations_update_own ON public.recitations;
CREATE POLICY recitations_update_own ON public.recitations
  FOR UPDATE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS recitations_delete_own ON public.recitations;
CREATE POLICY recitations_delete_own ON public.recitations
  FOR DELETE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Exams
ALTER POLICY IF EXISTS exams_select_own ON public.exams;
CREATE POLICY exams_select_own ON public.exams
  FOR SELECT TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS exams_insert_own ON public.exams;
CREATE POLICY exams_insert_own ON public.exams
  FOR INSERT TO authenticated
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS exams_update_own ON public.exams;
CREATE POLICY exams_update_own ON public.exams
  FOR UPDATE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS exams_delete_own ON public.exams;
CREATE POLICY exams_delete_own ON public.exams
  FOR DELETE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Homework
ALTER POLICY IF EXISTS homework_select_own ON public.homework;
CREATE POLICY homework_select_own ON public.homework
  FOR SELECT TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS homework_insert_own ON public.homework;
CREATE POLICY homework_insert_own ON public.homework
  FOR INSERT TO authenticated
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS homework_update_own ON public.homework;
CREATE POLICY homework_update_own ON public.homework
  FOR UPDATE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS homework_delete_own ON public.homework;
CREATE POLICY homework_delete_own ON public.homework
  FOR DELETE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Lesson Payments
ALTER POLICY IF EXISTS lesson_payments_select_own ON public.lesson_payments;
CREATE POLICY lesson_payments_select_own ON public.lesson_payments
  FOR SELECT TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS lesson_payments_insert_own ON public.lesson_payments;
CREATE POLICY lesson_payments_insert_own ON public.lesson_payments
  FOR INSERT TO authenticated
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS lesson_payments_update_own ON public.lesson_payments;
CREATE POLICY lesson_payments_update_own ON public.lesson_payments
  FOR UPDATE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
  WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

ALTER POLICY IF EXISTS lesson_payments_delete_own ON public.lesson_payments;
CREATE POLICY lesson_payments_delete_own ON public.lesson_payments
  FOR DELETE TO authenticated
  USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
