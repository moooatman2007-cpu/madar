package com.example

import com.example.core.model.Group
import com.example.core.model.GroupDay
import com.example.core.model.Teacher
import com.example.data.repository.*
import com.example.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayGroupsScheduleTest {

    private val testDispatcher = StandardTestDispatcher()

    private val teacher1 = Teacher(
        id = "teacher_1",
        email = "teacher1@test.com",
        fullName = "الأستاذ الأول"
    )

    private val teacher2 = Teacher(
        id = "teacher_2",
        email = "teacher2@test.com",
        fullName = "الأستاذ الثاني"
    )

    private lateinit var teacherRepo: MockTeacherRepository
    private lateinit var groupRepo: MockGroupRepository
    private lateinit var gradeRepo: MockGradeRepository
    private lateinit var studentRepo: MockStudentRepository
    private lateinit var attendanceRepo: MockAttendanceRepository
    private lateinit var recitationRepo: MockRecitationRepository
    private lateinit var examRepo: MockExamRepository
    private lateinit var homeworkRepo: MockHomeworkRepository

    private val todayArabic: String
        get() = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "السبت"
            Calendar.SUNDAY -> "الأحد"
            Calendar.MONDAY -> "الاثنين"
            Calendar.TUESDAY -> "الثلاثاء"
            Calendar.WEDNESDAY -> "الأربعاء"
            Calendar.THURSDAY -> "الخميس"
            Calendar.FRIDAY -> "الجمعة"
            else -> ""
        }

    private val otherDayArabic: String
        get() = if (todayArabic == "الجمعة") "السبت" else "الجمعة"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        teacherRepo = MockTeacherRepository(teacher1)
        groupRepo = MockGroupRepository()
        gradeRepo = MockGradeRepository()
        studentRepo = MockStudentRepository(gradeRepo, emptyList())
        attendanceRepo = MockAttendanceRepository()
        recitationRepo = MockRecitationRepository()
        examRepo = MockExamRepository()
        homeworkRepo = MockHomeworkRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            studentRepository = studentRepo,
            gradeRepository = gradeRepo,
            attendanceRepository = attendanceRepo,
            recitationRepository = recitationRepo,
            examRepository = examRepo,
            homeworkRepository = homeworkRepo,
            groupRepository = groupRepo,
            teacherRepository = teacherRepo
        )
    }

    @Test
    fun testNewGroupScheduledForTodayAppearsDynamically() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Initially no groups
        assertTrue(viewModel.uiState.value.todayGroups.isEmpty())

        // Add group scheduled for today
        val group = Group(
            id = "grp_today_1",
            teacherId = "teacher_1",
            gradeId = "grade_1",
            name = "مجموعة اليوم المتفوقين",
            active = true,
            startTime = "10:00",
            endTime = "11:30"
        )
        groupRepo.createGroup(group)
        groupRepo.replaceGroupDays("teacher_1", "grp_today_1", listOf(todayArabic))
        testScheduler.advanceUntilIdle()

        // The group must appear in todayGroups
        val todayGroups = viewModel.uiState.value.todayGroups
        assertEquals(1, todayGroups.size)
        assertEquals("grp_today_1", todayGroups[0].id)
        assertEquals("مجموعة اليوم المتفوقين", todayGroups[0].name)
    }

    @Test
    fun testGroupScheduledForOtherDayDoesNotAppearToday() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Add group scheduled for a different day
        val group = Group(
            id = "grp_other_day",
            teacherId = "teacher_1",
            gradeId = "grade_1",
            name = "مجموعة يوم آخر",
            active = true,
            startTime = "12:00",
            endTime = "13:30"
        )
        groupRepo.createGroup(group)
        groupRepo.replaceGroupDays("teacher_1", "grp_other_day", listOf(otherDayArabic))
        testScheduler.advanceUntilIdle()

        // Must NOT appear in today's groups
        assertTrue(viewModel.uiState.value.todayGroups.isEmpty())
    }

    @Test
    fun testDeactivatingGroupRemovesItFromTodayAndReactivatingRestoresIt() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val group = Group(
            id = "grp_toggle",
            teacherId = "teacher_1",
            gradeId = "grade_1",
            name = "مجموعة التبديل",
            active = true,
            startTime = "14:00",
            endTime = "15:00"
        )
        groupRepo.createGroup(group)
        groupRepo.replaceGroupDays("teacher_1", "grp_toggle", listOf(todayArabic))
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.todayGroups.size)

        // Deactivate
        groupRepo.deactivateGroup("teacher_1", "grp_toggle")
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.todayGroups.isEmpty())

        // Reactivate
        groupRepo.updateGroup(group.copy(active = true))
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.todayGroups.size)
    }

    @Test
    fun testMultipleGroupsForSameTeacher() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        val g1 = Group("g1", "teacher_1", "grade_1", "فصل أ", active = true, startTime = "09:00", endTime = "10:00")
        val g2 = Group("g2", "teacher_1", "grade_1", "فصل ب", active = true, startTime = "11:00", endTime = "12:00")
        val g3 = Group("g3", "teacher_1", "grade_1", "فصل ج", active = true, startTime = "13:00", endTime = "14:00")

        groupRepo.createGroup(g1)
        groupRepo.createGroup(g2)
        groupRepo.createGroup(g3)

        // g1 and g3 are scheduled for today, g2 is for another day
        groupRepo.replaceGroupDays("teacher_1", "g1", listOf(todayArabic))
        groupRepo.replaceGroupDays("teacher_1", "g2", listOf(otherDayArabic))
        groupRepo.replaceGroupDays("teacher_1", "g3", listOf(todayArabic))
        testScheduler.advanceUntilIdle()

        val todayGroups = viewModel.uiState.value.todayGroups
        assertEquals(2, todayGroups.size)
        assertEquals("g1", todayGroups[0].id)
        assertEquals("g3", todayGroups[1].id)
    }

    @Test
    fun testTeacherIsolation() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        testScheduler.advanceUntilIdle()

        // Group scheduled for today, but belongs to teacher_2
        val otherTeacherGroup = Group(
            id = "grp_teacher_2",
            teacherId = "teacher_2",
            gradeId = "grade_1",
            name = "مجموعة معلم آخر",
            active = true,
            startTime = "10:00",
            endTime = "11:00"
        )
        groupRepo.createGroup(otherTeacherGroup)
        groupRepo.replaceGroupDays("teacher_2", "grp_teacher_2", listOf(todayArabic))
        testScheduler.advanceUntilIdle()

        // Must NOT appear for teacher_1
        assertTrue(viewModel.uiState.value.todayGroups.isEmpty())
    }
}
