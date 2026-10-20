package com.example

import com.example.core.model.AttendanceStatus
import com.example.core.model.BatchAttendanceItemDto
import com.example.core.model.EducationalStages
import com.example.core.model.Student
import com.example.data.repository.MockAttendanceRepository
import com.example.data.repository.MockExamRepository
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockHomeworkRepository
import com.example.data.repository.NotificationEventRepository
import com.example.core.model.NotificationEvent
import com.example.data.repository.NotificationEventCreationResult
import com.example.data.repository.NotificationWorkerResult
import com.example.data.repository.MockGroupRepository
import com.example.data.repository.MockPaymentRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.MockTeacherRepository
import com.example.ui.attendance.FastAttendanceViewModel
import com.example.ui.attendance.ScanResultType
import com.example.ui.start_lesson.StartLessonViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceUnificationComprehensiveTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currentDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var attendanceRepository: MockAttendanceRepository
    private lateinit var paymentRepository: MockPaymentRepository
    private lateinit var recitationRepository: MockRecitationRepository
    private lateinit var homeworkRepository: MockHomeworkRepository
    private lateinit var examRepository: MockExamRepository
    private lateinit var notificationEventRepository: TestNotificationEventRepository

    private class TestNotificationEventRepository : NotificationEventRepository {
        override suspend fun createNotificationEvent(event: NotificationEvent): NotificationEventCreationResult {
            return NotificationEventCreationResult.Created(event)
        }
        override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult {
            return NotificationWorkerResult.Success("OK")
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        com.example.data.SupabaseClientProvider.mockTeacherId = "teacher_test"
        gradeRepository = MockGradeRepository(initialStage = EducationalStages.SECONDARY)
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        attendanceRepository = MockAttendanceRepository()
        paymentRepository = MockPaymentRepository()
        recitationRepository = MockRecitationRepository()
        homeworkRepository = MockHomeworkRepository()
        examRepository = MockExamRepository()
        notificationEventRepository = TestNotificationEventRepository()
    }

    @After
    fun tearDown() {
        com.example.data.SupabaseClientProvider.mockTeacherId = null
        Dispatchers.resetMain()
    }

    private fun createTestStudents(gradeId: String, groupId: String? = null): List<Student> {
        return listOf(
            Student(
                studentId = "std_ahmed",
                teacherId = "teacher_test",
                gradeId = gradeId,
                groupId = groupId,
                fullName = "أحمد محمد",
                studentCode = "ST-001",
                parentPhone = "0110000001"
            ),
            Student(
                studentId = "std_mahmoud",
                teacherId = "teacher_test",
                gradeId = gradeId,
                groupId = groupId,
                fullName = "محمود علي",
                studentCode = "ST-002",
                parentPhone = "0110000002"
            )
        )
    }

    /**
     * Requirement 1:
     * تسجيل أحمد غائبًا عند بدء الحصة، ثم فتح الحضور السريع: تظهر حالته غائبًا بالفعل.
     */
    @Test
    fun test1_markAbsentInStartLesson_thenOpenFastAttendance_showsAbsent() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val testStudents = createTestStudents(targetGrade.id)
        val studentRepository = MockStudentRepository(gradeRepository = gradeRepository, customStudents = testStudents)

        // 1. In StartLesson: student ST-002 (Mahmoud) is present, ST-001 (Ahmed) is absent
        val startLessonVM = StartLessonViewModel(
            gradeRepository = gradeRepository,
            studentRepository = studentRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            homeworkRepository = homeworkRepository,
            examRepository = examRepository,
            paymentRepository = paymentRepository,
            notificationEventRepository = notificationEventRepository
        )
        advanceUntilIdle()

        startLessonVM.selectGrade(targetGrade.id)
        advanceUntilIdle()

        // Mahmoud is marked present, Ahmed is left unselected (absent)
        startLessonVM.toggleStudentPresent("std_mahmoud")
        advanceUntilIdle()

        // Finish lesson saves attendance
        startLessonVM.finishLesson()
        advanceUntilIdle()

        // Verify Ahmed is absent in repository
        val saved = attendanceRepository.getAttendanceForDateAndGroup(currentDate, null)
        val ahmedRecord = saved.find { it.studentId == "std_ahmed" }
        assertNotNull(ahmedRecord)
        assertEquals(AttendanceStatus.ABSENT, ahmedRecord?.status)

        // 2. Open FastAttendance: Ahmed must be loaded as explicitly absent!
        val fastAttendanceVM = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        fastAttendanceVM.setStudentsForScope(testStudents, testStudents)
        fastAttendanceVM.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        val fastUiState = fastAttendanceVM.uiState.value
        // Ahmed is recognized as absent
        assertTrue(fastUiState.absentStudentIds.contains("std_ahmed"))
        assertFalse(fastUiState.presentStudentIds.contains("std_ahmed"))
        assertEquals(AttendanceStatus.ABSENT, fastUiState.attendanceStatusMap["std_ahmed"])

        // Mahmoud is recognized as present
        assertTrue(fastUiState.presentStudentIds.contains("std_mahmoud"))
    }

    /**
     * Requirement 2:
     * تسجيل غياب الطالب من الحضور السريع، ثم فتح الحصة نفسها: تظهر حالته المحفوظة مسبقًا بدقة.
     */
    @Test
    fun test2_markAbsentInFastAttendance_thenOpenStartLesson_showsAbsent() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val testStudents = createTestStudents(targetGrade.id)
        val studentRepository = MockStudentRepository(gradeRepository = gradeRepository, customStudents = testStudents)

        // 1. Open FastAttendance: mark Mahmoud present, Ahmed absent, and finish
        val fastAttendanceVM = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        fastAttendanceVM.setStudentsForScope(testStudents, testStudents)
        fastAttendanceVM.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        fastAttendanceVM.processScannedBarcode("ST-002") // Mahmoud is scanned present
        advanceUntilIdle()

        fastAttendanceVM.finishAttendance() // Finishes session: Mahmoud present, Ahmed absent
        advanceUntilIdle()

        // 2. Open StartLesson for the same grade/date
        val startLessonVM = StartLessonViewModel(
            gradeRepository = gradeRepository,
            studentRepository = studentRepository,
            attendanceRepository = attendanceRepository,
            recitationRepository = recitationRepository,
            homeworkRepository = homeworkRepository,
            examRepository = examRepository,
            paymentRepository = paymentRepository,
            notificationEventRepository = notificationEventRepository
        )
        advanceUntilIdle()

        startLessonVM.selectGrade(targetGrade.id)
        advanceUntilIdle()

        val lessonUiState = startLessonVM.uiState.value
        // Ahmed's pre-recorded absent status is loaded accurately
        assertTrue(lessonUiState.absentStudentIds.contains("std_ahmed"))
        assertEquals(AttendanceStatus.ABSENT, lessonUiState.attendanceStatusMap["std_ahmed"])

        // Mahmoud is pre-loaded as present
        assertTrue(lessonUiState.presentStudentIds.contains("std_mahmoud"))
    }

    /**
     * Requirement 3:
     * محاولة تسجيل الحضور لطالب مسجل غائبًا في نفس الحصة:
     * يُعرض تنبيه أو تأكيد صريح لتغيير الحالة، ولا يُنشأ سجل مكرر.
     */
    @Test
    fun test3_toggleAbsentStudent_updatesStateWithoutDuplication() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val testStudents = createTestStudents(targetGrade.id)
        val studentRepository = MockStudentRepository(gradeRepository = gradeRepository, customStudents = testStudents)

        // Pre-save Ahmed as ABSENT in attendance repository
        attendanceRepository.recordBatchAttendance(
            date = currentDate,
            records = listOf(
                BatchAttendanceItemDto(studentId = "std_ahmed", status = AttendanceStatus.ABSENT.name)
            ),
            groupId = null
        )

        val fastAttendanceVM = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        fastAttendanceVM.setStudentsForScope(testStudents, testStudents)
        fastAttendanceVM.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // Ahmed starts absent
        assertTrue(fastAttendanceVM.uiState.value.absentStudentIds.contains("std_ahmed"))

        // Explicitly toggle Ahmed to present
        fastAttendanceVM.toggleStudentPresent("std_ahmed")
        advanceUntilIdle()

        val updatedState = fastAttendanceVM.uiState.value
        assertTrue(updatedState.presentStudentIds.contains("std_ahmed"))
        assertFalse(updatedState.absentStudentIds.contains("std_ahmed"))
        assertNotNull(updatedState.statusChangeMessage)
        assertTrue(updatedState.statusChangeMessage?.contains("حضور") == true)

        // Save attendance and verify no duplicate record is created
        fastAttendanceVM.finishAttendance()
        advanceUntilIdle()

        val records = attendanceRepository.getAttendanceForDateAndGroup(currentDate, null)
        val ahmedRecords = records.filter { it.studentId == "std_ahmed" }
        assertEquals(1, ahmedRecords.size)
        assertEquals(AttendanceStatus.PRESENT, ahmedRecords.first().status)
    }

    /**
     * Requirement 4:
     * مسح باركود طالب مسجل غائبًا في نفس الحصة:
     * لا يُفترض أنه غير مسجل، بل يُعرض أنه مسجل غائب (ALREADY_ABSENT)،
     * مع إمكانية تعديل حالته إلى حاضر إذا رغب المدرس.
     */
    @Test
    fun test4_scanBarcodeOfAbsentStudent_returnsAlreadyAbsent_andAllowsConversion() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()
        val testStudents = createTestStudents(targetGrade.id)
        val studentRepository = MockStudentRepository(gradeRepository = gradeRepository, customStudents = testStudents)

        // Pre-record Ahmed as absent
        attendanceRepository.recordBatchAttendance(
            date = currentDate,
            records = listOf(
                BatchAttendanceItemDto(studentId = "std_ahmed", status = AttendanceStatus.ABSENT.name)
            ),
            groupId = null
        )

        val fastAttendanceVM = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository
        )
        advanceUntilIdle()

        fastAttendanceVM.setStudentsForScope(testStudents, testStudents)
        fastAttendanceVM.onGradeSelected(targetGrade.id)
        advanceUntilIdle()

        // 1. Scan barcode ST-001 (Ahmed)
        fastAttendanceVM.processScannedBarcode("ST-001")
        advanceUntilIdle()

        val feedback = fastAttendanceVM.uiState.value.scanFeedback
        assertNotNull(feedback)
        assertEquals(ScanResultType.ALREADY_ABSENT, feedback?.type)
        assertEquals("std_ahmed", feedback?.student?.studentId)

        // 2. Change status to present
        fastAttendanceVM.changeAbsentToPresent("std_ahmed")
        advanceUntilIdle()

        val convertedState = fastAttendanceVM.uiState.value
        assertTrue(convertedState.presentStudentIds.contains("std_ahmed"))
        assertFalse(convertedState.absentStudentIds.contains("std_ahmed"))
        assertNotNull(convertedState.statusChangeMessage)
    }

    /**
     * Requirement 5:
     * وجود تسجيل سابق في حصة أو جلسة أخرى لا يعطل التسجيل الجديد.
     */
    @Test
    fun test5_guestOrDifferentSessionStudent_doesNotBlockNewSession() = runTest {
        val grades = gradeRepository.getGrades().first()
        val targetGrade = grades.first()

        // Guest student from group_B
        val guestStudent = Student(
            studentId = "std_guest",
            teacherId = "teacher_test",
            gradeId = targetGrade.id,
            groupId = "group_B",
            fullName = "طالب ضيف",
            studentCode = "ST-GUEST",
            parentPhone = "0119999999"
        )

        val groupStudents = createTestStudents(targetGrade.id, "group_A")
        val studentRepository = MockStudentRepository(gradeRepository = gradeRepository, customStudents = groupStudents + guestStudent)

        // Pre-record guest student attendance in group_B
        attendanceRepository.recordBatchAttendance(
            date = currentDate,
            records = listOf(
                BatchAttendanceItemDto(studentId = "std_guest", status = AttendanceStatus.PRESENT.name)
            ),
            groupId = "group_B"
        )

        val groupRepository = MockGroupRepository(
            initialGroups = listOf(
                com.example.core.model.Group(id = "group_A", teacherId = "teacher_test", gradeId = targetGrade.id, name = "مجموعة أ", startTime = "10:00", endTime = "11:00"),
                com.example.core.model.Group(id = "group_B", teacherId = "teacher_test", gradeId = targetGrade.id, name = "مجموعة ب", startTime = "11:00", endTime = "12:00")
            )
        )
        val teacherRepository = MockTeacherRepository(
            initialTeacher = com.example.core.model.Teacher(id = "teacher_test", email = "test@teacher.com", fullName = "مدرس تجريبي")
        )

        // Open fast attendance for group_A
        val fastAttendanceVM = FastAttendanceViewModel(
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = attendanceRepository,
            paymentRepository = paymentRepository,
            groupRepository = groupRepository,
            teacherRepository = teacherRepository
        )
        advanceUntilIdle()

        fastAttendanceVM.setStudentsForScope(groupStudents, groupStudents + guestStudent)
        fastAttendanceVM.setInitialGroupId("group_A", "مجموعة أ")
        advanceUntilIdle()

        // Scan guest student into group_A
        fastAttendanceVM.processScannedBarcode("ST-GUEST")
        val stateImmediate = fastAttendanceVM.uiState.value
        assertEquals(ScanResultType.SUCCESS_PRESENT, stateImmediate.scanFeedback?.type)
        advanceUntilIdle()

        val state = fastAttendanceVM.uiState.value
        // Guest is accepted into current session
        assertTrue(state.presentStudentIds.contains("std_guest"))
        assertTrue(state.extraSessionStudents.any { it.studentId == "std_guest" })
    }
}
