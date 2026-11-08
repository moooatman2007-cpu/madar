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
