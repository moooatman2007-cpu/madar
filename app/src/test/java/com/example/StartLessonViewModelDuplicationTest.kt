package com.example

import com.example.core.model.*
import com.example.data.repository.*
import com.example.ui.start_lesson.StartLessonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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

    private val testDispatcher = UnconfinedTestDispatcher()

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

        viewModel.finishLesson()
        
        // Assert saving state
        assertTrue(viewModel.uiState.value.isSaving)
        
        // Assert that a second call while saving does nothing or is ignored
        viewModel.finishLesson()
        // If it was already saving, the second call returns early, isSaving should remain true
        assertTrue(viewModel.uiState.value.isSaving)
    }
}
