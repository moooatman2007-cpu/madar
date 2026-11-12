package com.example

import com.example.core.model.Attendance
import com.example.core.model.AttendanceStatus
import com.example.core.model.Exam
import com.example.core.model.Grade
import com.example.core.model.Group
import com.example.core.model.Homework
import com.example.core.model.HomeworkStatus
import com.example.core.model.Recitation
import com.example.core.model.Student
import com.example.ui.reports.ReportsViewModel
import com.example.ui.reports.TopStudentsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TopStudentsFairRankingTest {

    private lateinit var viewModel: ReportsViewModel

    private val grade1 = Grade(id = "grade1", name = "الصف الأول المتوسط")
    private val grade2 = Grade(id = "grade2", name = "الصف الثاني المتوسط")

    private val group1 = Group(id = "group1", teacherId = "t1", gradeId = "grade1", name = "مجموعة أ", startTime = "16:00", endTime = "17:00")
    private val group2 = Group(id = "group2", teacherId = "t1", gradeId = "grade2", name = "مجموعة ب", startTime = "17:00", endTime = "18:00")

    @Before
    fun setup() {
        viewModel = ReportsViewModel()
    }

    // Helper builders
    private fun makeRecitation(id: String, studentId: String, score: Double, maxScore: Double, date: String = "2026-03-01") = Recitation(
        recitationId = id,
        studentId = studentId,
        date = date,
        title = "تسميع $id",
        content = "محتوى $id",
        score = score,
        maxScore = maxScore
    )

    private fun makeExam(id: String, studentId: String, score: Double, maxScore: Double, date: String = "2026-03-01") = Exam(
        examId = id,
        studentId = studentId,
        date = date,
        examName = "امتحان $id",
        score = score,
        maxScore = maxScore
    )

    private fun makeHomework(id: String, studentId: String, status: HomeworkStatus, date: String = "2026-03-01") = Homework(
        homeworkId = id,
        studentId = studentId,
        date = date,
        title = "واجب $id",
        status = status
    )
    private fun makeAttendance(id: String, studentId: String, status: AttendanceStatus, date: String = "2026-03-01") = Attendance(
        attendanceId = id,
        studentId = studentId,
        date = date,
        status = status
    )

    // 1. Production data simulation (15 students, 7 recitations, 0 exams, 0 homeworks -> 0 qualified, insufficient data card)
    @Test
    fun testProductionDataSimulation_15Students_7Recitations_0Exams_0Homework_NoQualified_InsufficientDataCard() {
        val students = (1..15).map { i ->
            Student(studentId = "s$i", fullName = "طالب $i", gradeId = "grade1", groupId = "group1")
        }

        // 7 recitations distributed among 5 students
        val recitations = listOf(
            makeRecitation("r1", "s1", 10.0, 10.0, "2026-03-01"),
            makeRecitation("r2", "s1", 9.0, 10.0, "2026-03-02"),
            makeRecitation("r3", "s2", 10.0, 10.0, "2026-03-01"),
            makeRecitation("r4", "s3", 8.0, 10.0, "2026-03-01"),
            makeRecitation("r5", "s4", 10.0, 10.0, "2026-03-01"),
            makeRecitation("r6", "s5", 7.0, 10.0, "2026-03-01"),
            makeRecitation("r7", "s5", 10.0, 10.0, "2026-03-02")
        )

        val report = viewModel.calculateTopStudents(
            allStudents = students,
            allRecitations = recitations,
            allExams = emptyList(),
            allHomework = emptyList(),
            allAttendance = emptyList(),
            gradeId = "grade1",
            groupId = "group1",
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = listOf(group1)
        )

        assertFalse("Data should be insufficient", report.isDataSufficient)
        assertTrue("Qualified students should be empty", report.students.isEmpty())
        assertEquals("Unqualified students count should match all 15 students", 15, report.unqualifiedStudents.size)
        assertNotNull("Sufficient data message should be present", report.sufficientDataMessage)
        assertTrue("Message should warn about missing data", report.sufficientDataMessage!!.contains("لا توجد بيانات كافية"))
    }

    // 2. Student with 1 recitation 100% -> Not qualified
    @Test
    fun testStudentWith1Recitation100_NotQualified() {
        val student = Student(studentId = "s1", fullName = "أحمد علي", gradeId = "grade1", groupId = "group1")
        val recitations = listOf(makeRecitation("r1", "s1", 10.0, 10.0))

        val report = viewModel.calculateTopStudents(
            allStudents = listOf(student),
            allRecitations = recitations,
            allExams = emptyList(),
            allHomework = emptyList(),
            allAttendance = emptyList(),
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = emptyList()
        )

        assertFalse("Data should be marked insufficient when 0 qualify", report.isDataSufficient)
        assertEquals(0, report.students.size)
        assertEquals(1, report.unqualifiedStudents.size)

        val unqualified = report.unqualifiedStudents[0]
        assertFalse(unqualified.isQualified)
        assertNotNull(unqualified.unqualifiedReason)
        assertTrue(unqualified.unqualifiedReason!!.contains("تسميعات: 1/3"))
        assertTrue(unqualified.unqualifiedReason!!.contains("امتحانات: 0/3"))
        assertTrue(unqualified.unqualifiedReason!!.contains("واجبات: 0/3"))
    }

    // 3. Student with 3 recitations only -> Not qualified
    @Test
    fun testStudentWith3RecitationsOnly_NotQualified() {
        val student = Student(studentId = "s1", fullName = "عمر خالد", gradeId = "grade1", groupId = "group1")
        val recitations = listOf(
            makeRecitation("r1", "s1", 10.0, 10.0, "2026-03-01"),
            makeRecitation("r2", "s1", 10.0, 10.0, "2026-03-02"),
            makeRecitation("r3", "s1", 10.0, 10.0, "2026-03-03")
        )

        val report = viewModel.calculateTopStudents(
            allStudents = listOf(student),
            allRecitations = recitations,
            allExams = emptyList(),
            allHomework = emptyList(),
            allAttendance = emptyList(),
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = emptyList()
        )

        assertEquals(0, report.students.size)
        assertEquals(1, report.unqualifiedStudents.size)
        val unqualified = report.unqualifiedStudents[0]
        assertFalse(unqualified.isQualified)
        assertTrue(unqualified.unqualifiedReason!!.contains("امتحانات: 0/3"))
        assertTrue(unqualified.unqualifiedReason!!.contains("واجبات: 0/3"))
    }

    // 4. Student with 3 valid records in each category -> Qualified
    @Test
    fun testStudentWith3InEachCategory_Qualified() {
        val student = Student(studentId = "s1", fullName = "محمد حسن", gradeId = "grade1", groupId = "group1")
        val recitations = (1..3).map { makeRecitation("r$it", "s1", 10.0, 10.0, "2026-03-0$it") }
        val exams = (1..3).map { makeExam("e$it", "s1", 20.0, 20.0, "2026-03-0$it") }
        val homeworks = (1..3).map { makeHomework("h$it", "s1", HomeworkStatus.COMPLETED, "2026-03-0$it") }
        val attendance = (1..3).map { makeAttendance("a$it", "s1", AttendanceStatus.PRESENT, "2026-03-0$it") }

        val report = viewModel.calculateTopStudents(
            allStudents = listOf(student),
            allRecitations = recitations,
            allExams = exams,
            allHomework = homeworks,
            allAttendance = attendance,
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = emptyList()
        )

        assertTrue(report.isDataSufficient)
        assertEquals(1, report.students.size)
        val qualified = report.students[0]
        assertTrue(qualified.isQualified)
        assertEquals(100.0, qualified.overallScore!!, 0.01)
        assertNull(qualified.unqualifiedReason)
        assertEquals(1, qualified.rank)
        assertFalse(qualified.isTied)
    }

    // 5. Varying max_score across records -> Percentage calculated accurately per record
    @Test
    fun testVaryingMaxScore_CalculatedAccuratelyPerRecord_IgnoresZeroMaxScore() {
        val student = Student(studentId = "s1", fullName = "ياسين محمود", gradeId = "grade1", groupId = "group1")

        // 10/10 (100%), 15/20 (75%), 40/50 (80%) -> Average = (100 + 75 + 80) / 3 = 85%
        // Plus 1 invalid record with maxScore = 0.0 which should be ignored
        val recitations = listOf(
            makeRecitation("r1", "s1", 10.0, 10.0, "2026-03-01"),
            makeRecitation("r2", "s1", 15.0, 20.0, "2026-03-02"),
            makeRecitation("r3", "s1", 40.0, 50.0, "2026-03-03"),
            makeRecitation("r4", "s1", 0.0, 0.0, "2026-03-04")
        )
        val exams = (1..3).map { makeExam("e$it", "s1", 20.0, 20.0, "2026-03-0$it") }
        val homeworks = (1..3).map { makeHomework("h$it", "s1", HomeworkStatus.COMPLETED, "2026-03-0$it") }

        val report = viewModel.calculateTopStudents(
            allStudents = listOf(student),
            allRecitations = recitations,
            allExams = exams,
            allHomework = homeworks,
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = emptyList()
        )

        assertEquals(1, report.students.size)
        val top = report.students[0]
        assertEquals(85.0f, top.recitationAvg!!, 0.1f)
        assertEquals(100.0f, top.examAvg!!, 0.1f)
        assertEquals(100.0f, top.homeworkPercent!!, 0.1f)

        // Overall score = (85 + 100 + 100) / 3 = 95.0%
        assertEquals(95.0, top.overallScore!!, 0.1)
    }

    // 6. Homework completion percentage calculation
    @Test
    fun testHomeworkCompletionPercentage() {
        val student = Student(studentId = "s1", fullName = "طارق سعيد", gradeId = "grade1", groupId = "group1")
        val recitations = (1..3).map { makeRecitation("r$it", "s1", 10.0, 10.0, "2026-03-0$it") }
        val exams = (1..3).map { makeExam("e$it", "s1", 20.0, 20.0, "2026-03-0$it") }

        // 3 completed, 1 pending (total 4) -> 75%
        val homeworks = listOf(
            makeHomework("h1", "s1", HomeworkStatus.COMPLETED, "2026-03-01"),
            makeHomework("h2", "s1", HomeworkStatus.COMPLETED, "2026-03-02"),
            makeHomework("h3", "s1", HomeworkStatus.COMPLETED, "2026-03-03"),
            makeHomework("h4", "s1", HomeworkStatus.PENDING, "2026-03-04")
        )

        val report = viewModel.calculateTopStudents(
            allStudents = listOf(student),
            allRecitations = recitations,
            allExams = exams,
            allHomework = homeworks,
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = emptyList()
        )

        assertEquals(1, report.students.size)
        val top = report.students[0]
        assertEquals(4, top.homeworkCount)
        assertEquals(3, top.completedHomeworkCount)
        assertEquals(75.0f, top.homeworkPercent!!, 0.1f)
    }

    // 7. Ties -> Joint ranking (same rank, isTied = true)
    @Test
    fun testTiedScores_JointRanking() {
        val s1 = Student(studentId = "s1", fullName = "طالب 1", gradeId = "grade1", groupId = "group1")
        val s2 = Student(studentId = "s2", fullName = "طالب 2", gradeId = "grade1", groupId = "group1")

        val students = listOf(s1, s2)
        val recitations = listOf(
            makeRecitation("r1", "s1", 10.0, 10.0, "2026-03-01"),
            makeRecitation("r2", "s1", 10.0, 10.0, "2026-03-02"),
            makeRecitation("r3", "s1", 10.0, 10.0, "2026-03-03"),
            makeRecitation("r4", "s2", 10.0, 10.0, "2026-03-01"),
            makeRecitation("r5", "s2", 10.0, 10.0, "2026-03-02"),
            makeRecitation("r6", "s2", 10.0, 10.0, "2026-03-03")
        )
        val exams = listOf(
            makeExam("e1", "s1", 20.0, 20.0, "2026-03-01"),
            makeExam("e2", "s1", 20.0, 20.0, "2026-03-02"),
            makeExam("e3", "s1", 20.0, 20.0, "2026-03-03"),
            makeExam("e4", "s2", 20.0, 20.0, "2026-03-01"),
            makeExam("e5", "s2", 20.0, 20.0, "2026-03-02"),
            makeExam("e6", "s2", 20.0, 20.0, "2026-03-03")
        )
        val homeworks = listOf(
            makeHomework("h1", "s1", HomeworkStatus.COMPLETED, "2026-03-01"),
            makeHomework("h2", "s1", HomeworkStatus.COMPLETED, "2026-03-02"),
            makeHomework("h3", "s1", HomeworkStatus.COMPLETED, "2026-03-03"),
            makeHomework("h4", "s2", HomeworkStatus.COMPLETED, "2026-03-01"),
            makeHomework("h5", "s2", HomeworkStatus.COMPLETED, "2026-03-02"),
            makeHomework("h6", "s2", HomeworkStatus.COMPLETED, "2026-03-03")
        )

        val report = viewModel.calculateTopStudents(
            allStudents = students,
            allRecitations = recitations,
            allExams = exams,
            allHomework = homeworks,
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = emptyList()
        )

        assertEquals(2, report.students.size)
        val top1 = report.students[0]
        val top2 = report.students[1]

        assertEquals(1, top1.rank)
        assertEquals(1, top2.rank)
        assertTrue("Top 1 should be tied", top1.isTied)
        assertTrue("Top 2 should be tied", top2.isTied)
    }

    // 8. No data at all -> Clear insufficient data state
    @Test
    fun testNoData_EmptyLists_InsufficientData() {
        val report = viewModel.calculateTopStudents(
            allStudents = emptyList(),
            allRecitations = emptyList(),
            allExams = emptyList(),
            allHomework = emptyList(),
            allAttendance = emptyList(),
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = emptyList()
        )

        assertFalse(report.isDataSufficient)
        assertTrue(report.students.isEmpty())
        assertTrue(report.unqualifiedStudents.isEmpty())
        assertNotNull(report.sufficientDataMessage)
    }

    // 9. Class vs Group filter switching -> Results updated correctly
    @Test
    fun testModeSwitching_ClassVsGroupFilter() {
        val s1 = Student(studentId = "s1", fullName = "طالب صف 1 مجموعة 1", gradeId = "grade1", groupId = "group1")
        val s2 = Student(studentId = "s2", fullName = "طالب صف 1 مجموعة 2", gradeId = "grade1", groupId = "group2")

        val students = listOf(s1, s2)

        // Class report for grade1 (includes both s1 and s2)
        val classReport = viewModel.calculateTopStudents(
            allStudents = students,
            allRecitations = emptyList(),
            allExams = emptyList(),
            allHomework = emptyList(),
            allAttendance = emptyList(),
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1),
            allGroups = listOf(group1, group2)
        )

        assertEquals(2, classReport.unqualifiedStudents.size)

        // Group report for group1 (includes only s1)
        val groupReport = viewModel.calculateTopStudents(
            allStudents = students,
            allRecitations = emptyList(),
            allExams = emptyList(),
            allHomework = emptyList(),
            allAttendance = emptyList(),
            gradeId = "grade1",
            groupId = "group1",
            mode = TopStudentsMode.GROUP,
            allGrades = listOf(grade1),
            allGroups = listOf(group1, group2)
        )

        assertEquals(1, groupReport.unqualifiedStudents.size)
        assertEquals("s1", groupReport.unqualifiedStudents[0].student.studentId)
    }

    // 10. Tenant and Scope isolation -> Exclude students from other grades or groups
    @Test
    fun testTenantAndScopeIsolation() {
        val sGrade1 = Student(studentId = "s1", fullName = "طالب صف 1", gradeId = "grade1", groupId = "group1")
        val sGrade2 = Student(studentId = "s2", fullName = "طالب صف 2", gradeId = "grade2", groupId = "group2")

        val reportGrade1 = viewModel.calculateTopStudents(
            allStudents = listOf(sGrade1, sGrade2),
            allRecitations = emptyList(),
            allExams = emptyList(),
            allHomework = emptyList(),
            allAttendance = emptyList(),
            gradeId = "grade1",
            groupId = null,
            mode = TopStudentsMode.CLASS,
            allGrades = listOf(grade1, grade2),
            allGroups = listOf(group1, group2)
        )

        // sGrade2 should not be in scope for grade1
        assertEquals(1, reportGrade1.unqualifiedStudents.size)
        assertEquals("s1", reportGrade1.unqualifiedStudents[0].student.studentId)
    }
}
