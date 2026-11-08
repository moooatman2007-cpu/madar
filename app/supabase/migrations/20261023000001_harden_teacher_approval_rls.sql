-- =====================================================================
-- Migration: 20261023000001_harden_teacher_approval_rls.sql
-- Description: Enforces strict RLS approval check (account_status = 'active')
-- across all teacher-scoped tables and prevents self-escalation of account status.
-- =====================================================================

-- 1. Function: is_active_teacher()
-- Returns true if the currently authenticated user has an 'active' teacher account.
CREATE OR REPLACE FUNCTION public.is_active_teacher()
RETURNS BOOLEAN
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    RETURN EXISTS (
        SELECT 1 FROM public.teachers
        WHERE id = auth.uid()
          AND account_status = 'active'
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.is_active_teacher() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_active_teacher() TO authenticated, service_role;

-- 2. Trigger Function: protect_teacher_account_status()
-- Prevents authenticated users (teachers) from modifying their own account_status or id.
CREATE OR REPLACE FUNCTION public.protect_teacher_account_status()
RETURNS TRIGGER
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF auth.role() = 'authenticated' THEN
        IF NEW.account_status IS DISTINCT FROM OLD.account_status THEN
            RAISE EXCEPTION 'Unauthorized: teachers cannot modify their own account_status.';
        END IF;
        IF NEW.id IS DISTINCT FROM OLD.id THEN
            RAISE EXCEPTION 'Unauthorized: teacher id cannot be changed.';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_protect_teacher_account_status ON public.teachers;
CREATE TRIGGER trg_protect_teacher_account_status
BEFORE UPDATE ON public.teachers
FOR EACH ROW
EXECUTE FUNCTION public.protect_teacher_account_status();

-- 3. Harden Teachers Table RLS Policies
-- Allow teachers to read their own profile (even if pending/suspended so they see their status screen)
DROP POLICY IF EXISTS "teachers_select_own" ON public.teachers;
CREATE POLICY "teachers_select_own" ON public.teachers
FOR SELECT TO authenticated
USING (id = (SELECT auth.uid()));

-- Only active teachers can update their profile (and cannot change account_status due to trigger)
DROP POLICY IF EXISTS "teachers_update_own" ON public.teachers;
CREATE POLICY "teachers_update_own" ON public.teachers
FOR UPDATE TO authenticated
USING (id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()))
WITH CHECK (id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- 4. Harden Tenant Data Tables RLS Policies (Require active account status)
-- Tables: grades, students, attendance, recitations, exams, homework, lesson_payments, groups, group_days, notifications, teacher_whatsapp_sessions

-- Grades
DROP POLICY IF EXISTS "grades_select_own" ON public.grades;
DROP POLICY IF EXISTS "grades_insert_own" ON public.grades;
DROP POLICY IF EXISTS "grades_update_own" ON public.grades;
DROP POLICY IF EXISTS "grades_delete_own" ON public.grades;

CREATE POLICY "grades_select_own" ON public.grades FOR SELECT TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "grades_insert_own" ON public.grades FOR INSERT TO authenticated WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "grades_update_own" ON public.grades FOR UPDATE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher())) WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "grades_delete_own" ON public.grades FOR DELETE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Students
DROP POLICY IF EXISTS "students_select_own" ON public.students;
DROP POLICY IF EXISTS "students_insert_own" ON public.students;
DROP POLICY IF EXISTS "students_update_own" ON public.students;
DROP POLICY IF EXISTS "students_delete_own" ON public.students;

CREATE POLICY "students_select_own" ON public.students FOR SELECT TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "students_insert_own" ON public.students FOR INSERT TO authenticated WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "students_update_own" ON public.students FOR UPDATE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher())) WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "students_delete_own" ON public.students FOR DELETE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Attendance
DROP POLICY IF EXISTS "attendance_select_own" ON public.attendance;
DROP POLICY IF EXISTS "attendance_insert_own" ON public.attendance;
DROP POLICY IF EXISTS "attendance_update_own" ON public.attendance;
DROP POLICY IF EXISTS "attendance_delete_own" ON public.attendance;

CREATE POLICY "attendance_select_own" ON public.attendance FOR SELECT TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "attendance_insert_own" ON public.attendance FOR INSERT TO authenticated WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "attendance_update_own" ON public.attendance FOR UPDATE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher())) WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "attendance_delete_own" ON public.attendance FOR DELETE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Recitations
DROP POLICY IF EXISTS "recitations_select_own" ON public.recitations;
DROP POLICY IF EXISTS "recitations_insert_own" ON public.recitations;
DROP POLICY IF EXISTS "recitations_update_own" ON public.recitations;
DROP POLICY IF EXISTS "recitations_delete_own" ON public.recitations;

CREATE POLICY "recitations_select_own" ON public.recitations FOR SELECT TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "recitations_insert_own" ON public.recitations FOR INSERT TO authenticated WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "recitations_update_own" ON public.recitations FOR UPDATE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher())) WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "recitations_delete_own" ON public.recitations FOR DELETE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Exams
DROP POLICY IF EXISTS "exams_select_own" ON public.exams;
DROP POLICY IF EXISTS "exams_insert_own" ON public.exams;
DROP POLICY IF EXISTS "exams_update_own" ON public.exams;
DROP POLICY IF EXISTS "exams_delete_own" ON public.exams;

CREATE POLICY "exams_select_own" ON public.exams FOR SELECT TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "exams_insert_own" ON public.exams FOR INSERT TO authenticated WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "exams_update_own" ON public.exams FOR UPDATE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher())) WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "exams_delete_own" ON public.exams FOR DELETE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Homework
DROP POLICY IF EXISTS "homework_select_own" ON public.homework;
DROP POLICY IF EXISTS "homework_insert_own" ON public.homework;
DROP POLICY IF EXISTS "homework_update_own" ON public.homework;
DROP POLICY IF EXISTS "homework_delete_own" ON public.homework;

CREATE POLICY "homework_select_own" ON public.homework FOR SELECT TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "homework_insert_own" ON public.homework FOR INSERT TO authenticated WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "homework_update_own" ON public.homework FOR UPDATE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher())) WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "homework_delete_own" ON public.homework FOR DELETE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));

-- Lesson Payments
DROP POLICY IF EXISTS "lesson_payments_select_own" ON public.lesson_payments;
DROP POLICY IF EXISTS "lesson_payments_insert_own" ON public.lesson_payments;
DROP POLICY IF EXISTS "lesson_payments_update_own" ON public.lesson_payments;
DROP POLICY IF EXISTS "lesson_payments_delete_own" ON public.lesson_payments;

CREATE POLICY "lesson_payments_select_own" ON public.lesson_payments FOR SELECT TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "lesson_payments_insert_own" ON public.lesson_payments FOR INSERT TO authenticated WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "lesson_payments_update_own" ON public.lesson_payments FOR UPDATE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher())) WITH CHECK (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
CREATE POLICY "lesson_payments_delete_own" ON public.lesson_payments FOR DELETE TO authenticated USING (teacher_id = (SELECT auth.uid()) AND (SELECT public.is_active_teacher()));
