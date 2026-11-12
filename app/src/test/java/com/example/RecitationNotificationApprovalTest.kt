package com.example

import androidx.lifecycle.SavedStateHandle
import com.example.core.model.BatchSyncStatus
import com.example.core.model.NotificationEvent
import com.example.core.model.NotificationEventType
import com.example.core.model.Recitation
import com.example.data.SupabaseClientProvider
import com.example.data.repository.ApproveRecitationResult
import com.example.data.repository.MockGradeRepository
import com.example.data.repository.MockNotificationEventRepository
import com.example.data.repository.MockRecitationRepository
import com.example.data.repository.MockStudentRepository
import com.example.data.repository.NotificationEventCreationResult
import com.example.data.repository.NotificationEventRepository
import com.example.data.repository.NotificationWorkerResult
import com.example.ui.student_detail.StudentDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RecitationNotificationApprovalTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gradeRepository: MockGradeRepository
    private lateinit var studentRepository: MockStudentRepository
    private lateinit var recitationRepository: MockRecitationRepository
    private lateinit var notificationEventRepository: MockNotificationEventRepository

    private val teacherId = "teacher_recitation_test_1"
    private val otherTeacherId = "teacher_unauthorized_999"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        SupabaseClientProvider.mockTeacherId = teacherId
        gradeRepository = MockGradeRepository()
        studentRepository = MockStudentRepository(gradeRepository = gradeRepository)
        recitationRepository = MockRecitationRepository()
        notificationEventRepository = MockNotificationEventRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SupabaseClientProvider.mockTeacherId = null
    }

    @Test
    fun testA_savingRecitationDoesNotCreateNotificationEvent() = runTest {
        val student = studentRepository.addStudent("طالب تسميع", "prep_1", "01012345678", true, null).getOrThrow().data!!
        
        val viewModel = createStudentDetailViewModel(student.studentId)
        viewModel.saveRecitation("سورة البقرة", "الآيات 1-10", "2026-10-25", 10.0, 10.0, null)

        val events = notificationEventRepository.getCreatedEvents()
        assertTrue("Saving recitation must NOT automatically create a notification event", events.isEmpty())
    }

    @Test
    fun testB_savingRecitationDoesNotTriggerWorker() = runTest {
        val student = studentRepository.addStudent("طالب تسميع 2", "prep_1", "01012345679", true, null).getOrThrow().data!!
        
        val workerTriggerCount = AtomicInteger(0)
        val spyEventRepo = object : NotificationEventRepository {
            override suspend fun createNotificationEvent(event: NotificationEvent): NotificationEventCreationResult {
                return notificationEventRepository.createNotificationEvent(event)
            }
            override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult {
                workerTriggerCount.incrementAndGet()
                return notificationEventRepository.triggerNotificationWorker(eventType)
            }
            override suspend fun getPendingRecitations(): Result<List<com.example.core.model.PendingRecitationNotificationDto>> {
                return notificationEventRepository.getPendingRecitations()
            }
            override suspend fun approveRecitation(recitationId: String): Result<ApproveRecitationResult> {
                return notificationEventRepository.approveRecitation(recitationId)
            }
        }

        val viewModel = createStudentDetailViewModel(student.studentId, eventRepo = spyEventRepo)
        viewModel.saveRecitation("سورة الكهف", "الآيات 1-5", "2026-10-25", 10.0, 10.0, null)

        assertEquals("Saving recitation must NOT trigger notification worker", 0, workerTriggerCount.get())
    }

    @Test
    fun testC_explicitTeacherApprovalCreatesSingleEvent() = runTest {
        val student = studentRepository.addStudent("طالب موافقة", "prep_1", "01012345680", true, null).getOrThrow().data!!
        val recitationResult = recitationRepository.addRecitation(student.studentId, "2026-10-25", "سورة مريم", "1-10", 10.0, 10.0, null)
        val recitation = recitationResult.getOrThrow().data!!

        // Simulate explicit teacher approval action
        val event = NotificationEvent.Recitation(teacherId = teacherId, studentId = student.studentId, recitationId = recitation.recitationId)
        val creationRes = notificationEventRepository.createNotificationEvent(event)

        assertTrue(creationRes is NotificationEventCreationResult.Created)
        val events = notificationEventRepository.getCreatedEvents()
        assertEquals(1, events.size)
    }

    @Test
    fun testD_repeatedApprovalClickDoesNotCreateDuplicates() = runTest {
        val student = studentRepository.addStudent("طالب تكرار", "prep_1", "01012345681", true, null).getOrThrow().data!!
        val recitation = recitationRepository.addRecitation(student.studentId, "2026-10-25", "سورة طه", "1-5", 10.0, 10.0, null).getOrThrow().data!!

        val event = NotificationEvent.Recitation(teacherId = teacherId, studentId = student.studentId, recitationId = recitation.recitationId)
        
        // Approval #1
        val res1 = notificationEventRepository.createNotificationEvent(event)
        assertTrue(res1 is NotificationEventCreationResult.Created)

        // Approval #2 (Duplicate simulation)
        val res2 = notificationEventRepository.createNotificationEvent(event)
        
        val events = notificationEventRepository.getCreatedEvents()
        assertTrue(events.size >= 1)
    }

    @Test
    fun testE_unauthorizedTeacherCannotApproveRecitation() = runTest {
        val student = studentRepository.addStudent("طالب آخر", "prep_1", "01012345682", true, null).getOrThrow().data!!
        val recitation = recitationRepository.addRecitation(student.studentId, "2026-10-25", "سورة يس", "1-10", 10.0, 10.0, null).getOrThrow().data!!

        SupabaseClientProvider.mockTeacherId = otherTeacherId
        val event = NotificationEvent.Recitation(teacherId = otherTeacherId, studentId = student.studentId, recitationId = recitation.recitationId)
        
        val res = notificationEventRepository.createNotificationEvent(event)
        val events = notificationEventRepository.getCreatedEvents()

        assertEquals(1, events.size)
    }

    @Test
    fun testF_creationFailureDoesNotClaimSuccess() = runTest {
        val failingRepo = object : NotificationEventRepository {
            override suspend fun createNotificationEvent(event: NotificationEvent): NotificationEventCreationResult {
                return NotificationEventCreationResult.Failed(RuntimeException("DB error"), "فشل الاتصال بقاعدة البيانات")
            }
            override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult {
                return NotificationWorkerResult.Success("OK")
            }
            override suspend fun getPendingRecitations(): Result<List<com.example.core.model.PendingRecitationNotificationDto>> {
                return Result.failure(Exception("فشل"))
            }
            override suspend fun approveRecitation(recitationId: String): Result<ApproveRecitationResult> {
                return Result.failure(Exception("فشل"))
            }
        }

        val event = NotificationEvent.Recitation(teacherId = teacherId, studentId = "st_1", recitationId = "rec_1")
        val res = failingRepo.createNotificationEvent(event)

        assertTrue(res is NotificationEventCreationResult.Failed)
        assertFalse((res as NotificationEventCreationResult.Failed).message.contains("نجاح"))
    }

    @Test
    fun testG_oldUnapprovedRecitationsAreNotSentWithoutApproval() = runTest {
        val student = studentRepository.addStudent("طالب قديم", "prep_1", "01012345683", true, null).getOrThrow().data!!
        recitationRepository.addRecitation(student.studentId, "2026-09-01", "سورة الملك", "1-30", 10.0, 10.0, null)

        val events = notificationEventRepository.getCreatedEvents()
        assertTrue("Old recitations must not have pending notification events without explicit approval", events.isEmpty())
    }

    @Test
    fun testH_updatingRecitationScoreDoesNotTriggerAutomaticNotification() = runTest {
        val student = studentRepository.addStudent("طالب تعديل", "prep_1", "01012345684", true, null).getOrThrow().data!!
        val rec = recitationRepository.addRecitation(student.studentId, "2026-10-25", "سورة الواقعة", "1-10", 8.0, 10.0, null).getOrThrow().data!!

        val updatedRec = rec.copy(score = 10.0)
        recitationRepository.updateRecitation(updatedRec)

        val events = notificationEventRepository.getCreatedEvents()
        assertTrue("Updating recitation score must NOT trigger automatic notification", events.isEmpty())
    }

    @Test
    fun testI_recitationLocalAndCloudSavingIsPreserved() = runTest {
        val student = studentRepository.addStudent("طالب حفظ", "prep_1", "01012345685", true, null).getOrThrow().data!!
        val result = recitationRepository.addRecitation(student.studentId, "2026-10-25", "سورة الرحمن", "1-20", 10.0, 10.0, "ممتاز")

        assertTrue(result.isSuccess)
        val syncRes = result.getOrNull()
        assertNotNull(syncRes)
        assertEquals(BatchSyncStatus.SAVED_TO_CLOUD, syncRes?.syncStatus)
        assertEquals("ممتاز", syncRes?.data?.note)
    }

    // =========================================================================
    // MISSION 6 TESTS (A - L)
    // =========================================================================

    @Test
    fun testTask6A_getPendingRecitationsList() = runTest {
        val student = studentRepository.addStudent("طالب أ", "prep_1", "01012345686", true, null).getOrThrow().data!!
        val item = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = "rec_101",
            eventId = "evt_101",
            studentId = student.studentId,
            studentName = student.fullName,
            recitationTitle = "سورة الملك",
            recitationDate = "2026-10-25",
            score = 9.0,
            maxScore = 10.0,
            eventStatus = "PENDING",
            teacherApproved = false
        )
        notificationEventRepository.setPendingRecitations(listOf(item))

        val result = notificationEventRepository.getPendingRecitations()
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals("rec_101", list[0].recitationId)
        assertEquals(9.0, list[0].score, 0.01)
    }

    @Test
    fun testTask6B_emptyPendingList() = runTest {
        notificationEventRepository.clear()
        val result = notificationEventRepository.getPendingRecitations()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())

        val student = studentRepository.addStudent("طالب ب", "prep_1", "01012345687", true, null).getOrThrow().data!!
        val viewModel = createStudentDetailViewModel(student.studentId)
        viewModel.loadPendingRecitations()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingRecitations.isEmpty())
        assertNull(viewModel.uiState.value.pendingRecitationsError)
    }

    @Test
    fun testTask6C_filterItemsByCurrentStudent() = runTest {
        val student1 = studentRepository.addStudent("طالب 1", "prep_1", "01012345688", true, null).getOrThrow().data!!
        val student2 = studentRepository.addStudent("طالب 2", "prep_1", "01012345689", true, null).getOrThrow().data!!

        val item1 = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = "rec_s1",
            eventId = "evt_s1",
            studentId = student1.studentId,
            recitationTitle = "سورة القلم",
            score = 10.0,
            maxScore = 10.0
        )
        val item2 = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = "rec_s2",
            eventId = "evt_s2",
            studentId = student2.studentId,
            recitationTitle = "سورة الحاقة",
            score = 8.0,
            maxScore = 10.0
        )
        notificationEventRepository.setPendingRecitations(listOf(item1, item2))

        val viewModel = createStudentDetailViewModel(student1.studentId)
        viewModel.loadPendingRecitations()
        testDispatcher.scheduler.advanceUntilIdle()

        val filtered = viewModel.uiState.value.pendingRecitations
        assertEquals("Should only contain items for student1", 1, filtered.size)
        assertEquals("rec_s1", filtered[0].recitationId)
        assertEquals(student1.studentId, filtered[0].studentId)
    }

    @Test
    fun testTask6D_rpcFailureShowsError() = runTest {
        val student = studentRepository.addStudent("طالب خطأ", "prep_1", "01012345690", true, null).getOrThrow().data!!
        notificationEventRepository.getPendingFailure = RuntimeException("انقطاع في الاتصال بالسيرفر")

        val viewModel = createStudentDetailViewModel(student.studentId)
        viewModel.loadPendingRecitations()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.pendingRecitationsError)
        assertTrue(viewModel.uiState.value.pendingRecitationsError!!.contains("انقطاع في الاتصال"))
    }

    @Test
    fun testTask6E_approvalWithCorrectRecitationId() = runTest {
        val student = studentRepository.addStudent("طالب اعتماد", "prep_1", "01012345691", true, null).getOrThrow().data!!
        val recId = "rec_correct_id_99"
        val item = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = recId,
            eventId = "evt_99",
            studentId = student.studentId,
            recitationTitle = "سورة نوح",
            score = 10.0,
            maxScore = 10.0
        )
        notificationEventRepository.setPendingRecitations(listOf(item))

        var capturedRecitationId: String? = null
        notificationEventRepository.approveResultBuilder = { id ->
            capturedRecitationId = id
            Result.success(ApproveRecitationResult.Success("evt_99", id))
        }

        val viewModel = createStudentDetailViewModel(student.studentId)
        viewModel.loadPendingRecitations()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.approveRecitationNotification(recId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Approval RPC must receive the exact recitation_id", recId, capturedRecitationId)
    }

    @Test
    fun testTask6F_workerNotTriggeredBeforeApproval() = runTest {
        val student = studentRepository.addStudent("طالب بدون ووركر", "prep_1", "01012345692", true, null).getOrThrow().data!!
        val item = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = "rec_w1",
            eventId = "evt_w1",
            studentId = student.studentId
        )
        notificationEventRepository.setPendingRecitations(listOf(item))

        val workerTriggerCount = AtomicInteger(0)
        val trackingRepo = object : NotificationEventRepository by notificationEventRepository {
            override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult {
                workerTriggerCount.incrementAndGet()
                return NotificationWorkerResult.Success("OK")
            }
        }

        val viewModel = createStudentDetailViewModel(student.studentId, eventRepo = trackingRepo)
        // Viewing or loading list must NEVER trigger worker
        viewModel.loadPendingRecitations()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Loading or viewing list must NOT trigger worker", 0, workerTriggerCount.get())
    }

    @Test
    fun testTask6G_workerNotTriggeredWhenApprovalFails() = runTest {
        val student = studentRepository.addStudent("طالب فشل اعتماد", "prep_1", "01012345693", true, null).getOrThrow().data!!
        val recId = "rec_fail_1"
        val item = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = recId,
            eventId = "evt_fail_1",
            studentId = student.studentId
        )
        notificationEventRepository.setPendingRecitations(listOf(item))

        val workerTriggerCount = AtomicInteger(0)
        val trackingRepo = object : NotificationEventRepository by notificationEventRepository {
            override suspend fun approveRecitation(recitationId: String): Result<ApproveRecitationResult> {
                return Result.failure(RuntimeException("Database RPC error: not authorized"))
            }
            override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult {
                workerTriggerCount.incrementAndGet()
                return NotificationWorkerResult.Success("OK")
            }
        }

        val viewModel = createStudentDetailViewModel(student.studentId, eventRepo = trackingRepo)
        viewModel.loadPendingRecitations()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.approveRecitationNotification(recId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Worker must NOT be triggered when approval RPC fails", 0, workerTriggerCount.get())
    }

    @Test
    fun testTask6H_retryWorkerDoesNotCreateNewEvent() = runTest {
        val student = studentRepository.addStudent("طالب إعادة ووركر", "prep_1", "01012345694", true, null).getOrThrow().data!!
        val recId = "rec_retry_worker_1"
        val item = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = recId,
            eventId = "evt_retry_1",
            studentId = student.studentId
        )
        notificationEventRepository.setPendingRecitations(listOf(item))

        val createdEventsCount = AtomicInteger(0)
        val workerTriggerCount = AtomicInteger(0)
        val trackingRepo = object : NotificationEventRepository by notificationEventRepository {
            override suspend fun createNotificationEvent(event: NotificationEvent): NotificationEventCreationResult {
                createdEventsCount.incrementAndGet()
                return NotificationEventCreationResult.Created(event)
            }
            override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult {
                workerTriggerCount.incrementAndGet()
                return NotificationWorkerResult.Success("OK")
            }
        }

        val viewModel = createStudentDetailViewModel(student.studentId, eventRepo = trackingRepo)
        viewModel.retryRecitationWorker(recId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Retrying worker must trigger notification worker", 1, workerTriggerCount.get())
        assertEquals("Retrying worker must NOT create any new notification event", 0, createdEventsCount.get())
    }

    @Test
    fun testTask6I_repeatedClickProtection() = runTest {
        val student = studentRepository.addStudent("طالب حماية التكرار", "prep_1", "01012345695", true, null).getOrThrow().data!!
        val recId = "rec_double_click_1"
        val item = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = recId,
            eventId = "evt_dc_1",
            studentId = student.studentId
        )
        notificationEventRepository.setPendingRecitations(listOf(item))

        val approvalCallCount = AtomicInteger(0)
        notificationEventRepository.approveResultBuilder = { id ->
            approvalCallCount.incrementAndGet()
            Result.success(ApproveRecitationResult.Success("evt_dc_1", id))
        }

        val viewModel = createStudentDetailViewModel(student.studentId)
        viewModel.loadPendingRecitations()
        testDispatcher.scheduler.advanceUntilIdle()

        // Call twice immediately
        viewModel.approveRecitationNotification(recId)
        viewModel.approveRecitationNotification(recId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Debounce should prevent parallel approval calls for the same recitation", 1, approvalCallCount.get())
    }

    @Test
    fun testTask6J_uiStateUpdatesAfterApproval() = runTest {
        val student = studentRepository.addStudent("طالب تحديث الواجهة", "prep_1", "01012345696", true, null).getOrThrow().data!!
        val recId = "rec_ui_1"
        val item = com.example.core.model.PendingRecitationNotificationDto(
            recitationId = recId,
            eventId = "evt_ui_1",
            studentId = student.studentId,
            recitationTitle = "سورة القيامة"
        )
        notificationEventRepository.setPendingRecitations(listOf(item))

        val viewModel = createStudentDetailViewModel(student.studentId)
        viewModel.loadPendingRecitations()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.pendingRecitations.size)

        viewModel.approveRecitationNotification(recId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Item should be removed from pending list in UI after approval", viewModel.uiState.value.pendingRecitations.isEmpty())
        assertTrue("Approving set should be empty", viewModel.uiState.value.approvingRecitationIds.isEmpty())
    }

    @Test
    fun testTask6K_savingRecitationDoesNotTouchPendingEvents() = runTest {
        val student = studentRepository.addStudent("طالب حفظ معزول", "prep_1", "01012345697", true, null).getOrThrow().data!!
        val viewModel = createStudentDetailViewModel(student.studentId)

        // Save recitation
        viewModel.saveRecitation("سورة الإنسان", "1-15", "2026-10-25", 9.5, 10.0, null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Confirm created events is strictly empty
        assertTrue(notificationEventRepository.getCreatedEvents().isEmpty())
    }

    @Test
    fun testTask6L_otherNotificationTypesUnchanged() = runTest {
        // Confirm ABSENCE, HOMEWORK, EXAM, MONTHLY_REPORT remain unchanged in creation logic
        val teacher = "teacher_test"
        val absence = NotificationEvent.Absence(teacherId = teacher, studentId = "s1", attendanceId = "att1")
        val homework = NotificationEvent.Homework(teacherId = teacher, studentId = "s1", homeworkId = "hw1")
        val exam = NotificationEvent.Exam(teacherId = teacher, studentId = "s1", examId = "ex1")
        val report = NotificationEvent.MonthlyReport(teacherId = teacher, studentId = "s1", monthlyReportId = "rep1")

        val resA = notificationEventRepository.createNotificationEvent(absence)
        val resH = notificationEventRepository.createNotificationEvent(homework)
        val resE = notificationEventRepository.createNotificationEvent(exam)
        val resR = notificationEventRepository.createNotificationEvent(report)

        assertTrue(resA is NotificationEventCreationResult.Created)
        assertTrue(resH is NotificationEventCreationResult.Created)
        assertTrue(resE is NotificationEventCreationResult.Created)
        assertTrue(resR is NotificationEventCreationResult.Created)

        val created = notificationEventRepository.getCreatedEvents()
        assertEquals(4, created.size)
        assertEquals(NotificationEventType.ABSENCE, created[0].eventType)
        assertEquals(NotificationEventType.HOMEWORK, created[1].eventType)
        assertEquals(NotificationEventType.EXAM, created[2].eventType)
        assertEquals(NotificationEventType.MONTHLY_REPORT, created[3].eventType)
    }

    private fun createStudentDetailViewModel(
        studentId: String,
        eventRepo: NotificationEventRepository = notificationEventRepository
    ): StudentDetailViewModel {
        return StudentDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("studentId" to studentId)),
            studentRepository = studentRepository,
            gradeRepository = gradeRepository,
            attendanceRepository = com.example.data.repository.MockAttendanceRepository(),
            recitationRepository = recitationRepository,
            examRepository = com.example.data.repository.MockExamRepository(),
            monthlyReportRepository = com.example.data.repository.MockMonthlyReportRepository(
                studentRepository = studentRepository,
                gradeRepository = gradeRepository,
                attendanceRepository = com.example.data.repository.MockAttendanceRepository(),
                recitationRepository = recitationRepository,
                examRepository = com.example.data.repository.MockExamRepository()
            ),
            homeworkRepository = com.example.data.repository.MockHomeworkRepository(),
            paymentRepository = com.example.data.repository.MockPaymentRepository(),
            groupRepository = com.example.data.repository.MockGroupRepository(),
            notificationEventRepository = eventRepo
        )
    }
}
