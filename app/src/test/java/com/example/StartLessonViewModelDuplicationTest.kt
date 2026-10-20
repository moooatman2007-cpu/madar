package com.example

import com.example.core.model.*
import com.example.data.repository.*
import com.example.ui.start_lesson.StartLessonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartLessonViewModelDuplicationTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testFinishLessonDuplicationPrevention() = runTest {
        val viewModel = StartLessonViewModel(
            gradeRepository = MockGradeRepository(),
            studentRepository = MockStudentRepository(),
            attendanceRepository = MockAttendanceRepository(),
            recitationRepository = MockRecitationRepository(),
            homeworkRepository = MockHomeworkRepository(),
            examRepository = MockExamRepository(),
            paymentRepository = MockPaymentRepository(),
            notificationEventRepository = NotificationEventRepositoryMock()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.finishLesson()
        
        // Assert saving state while background coroutine is pending
        assertTrue(viewModel.uiState.value.isSaving)
        
        // Assert that a second call while saving does nothing or is ignored
        viewModel.finishLesson()
        assertTrue(viewModel.uiState.value.isSaving)

        // Advance to let saving finish
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isFinished)
    }
}
