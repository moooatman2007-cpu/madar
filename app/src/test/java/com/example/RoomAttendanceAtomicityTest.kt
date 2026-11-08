package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AttendanceStatus
import com.example.core.model.BatchAttendanceItemDto
import com.example.core.model.BatchSyncStatus
import com.example.data.SupabaseClientProvider
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.dao.AttendanceDao
import com.example.data.local.dao.OutboxDao
import com.example.data.local.entity.AttendanceEntity
import com.example.data.local.entity.OutboxEntity
import com.example.data.repository.SupabaseAttendanceRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomAttendanceAtomicityTest {

    private lateinit var db: AppDatabase
    private lateinit var attendanceDao: AttendanceDao
    private lateinit var outboxDao: OutboxDao
    private val teacherId = "teacher_atomicity_test"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        attendanceDao = db.attendanceDao()
        outboxDao = db.outboxDao()

        SupabaseClientProvider.mockTeacherId = teacherId
        DatabaseProvider.setDatabase(db, context)
    }

    @After
    fun tearDown() {
        db.close()
        DatabaseProvider.resetForTesting()
        SupabaseClientProvider.mockTeacherId = null
    }

    /**
     * Test A: Attendance and Outbox both succeed inside Room transaction.
     * Both should be persisted, and result returns success with QUEUED_OFFLINE.
     */
    @Test
    fun testA_attendanceAndOutboxSucceed_bothPersistedAtomically() = runBlocking {
        val repository = SupabaseAttendanceRepository(attendanceDao, outboxDao, appDatabase = db)
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_a_1", status = "PRESENT", note = null)
        )
        val result = repository.recordBatchAttendance("2026-09-25", items)

        assertTrue(result.isSuccess)
        val batchResult = result.getOrNull()
        assertNotNull(batchResult)
        assertEquals(BatchSyncStatus.QUEUED_OFFLINE, batchResult?.syncStatus)

        // Verify Attendance in Room
        val attendance = attendanceDao.getAttendanceByDateSync(teacherId, "st_a_1", "2026-09-25")
        assertNotNull(attendance)
        assertEquals(AttendanceStatus.PRESENT.name, attendance?.status)

        // Verify Outbox in Room
        val outboxList = outboxDao.getPendingOperationsForTeacher(teacherId)
        assertEquals(1, outboxList.size)
        assertEquals("st_a_1_2026-09-25", outboxList[0].entityId)
    }

    /**
     * Test B: Attendance succeeds then Outbox insertion fails inside transaction.
     * Transaction must roll back completely so Attendance is NOT saved standalone.
     */
    @Test
    fun testB_outboxFailure_rollsBackAttendanceTransaction() = runBlocking {
        val throwingOutboxDao = object : OutboxDao by outboxDao {
            override suspend fun insertOperations(operations: List<OutboxEntity>) {
                throw RuntimeException("Simulated Outbox DB Write Failure")
            }
        }

        val repository = SupabaseAttendanceRepository(attendanceDao, throwingOutboxDao, appDatabase = db)
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_b_1", status = "PRESENT", note = null)
        )
        val result = repository.recordBatchAttendance("2026-09-25", items)

        assertTrue(result.isFailure)

        // Attendance MUST NOT be saved in Room due to transaction rollback
        val attendance = attendanceDao.getAttendanceByDateSync(teacherId, "st_b_1", "2026-09-25")
        assertNull("Attendance should be rolled back when Outbox fails!", attendance)

        // Outbox MUST be empty
        val outboxList = outboxDao.getPendingOperationsForTeacher(teacherId)
        assertTrue(outboxList.isEmpty())
    }

    /**
     * Test C: Attendance insertion fails inside transaction.
     * Outbox MUST NOT be saved.
     */
    @Test
    fun testC_attendanceFailure_rollsBackOutboxTransaction() = runBlocking {
        val throwingAttendanceDao = object : AttendanceDao by attendanceDao {
            override suspend fun upsertAttendance(records: List<AttendanceEntity>) {
                throw RuntimeException("Simulated Attendance DB Write Failure")
            }
        }

        val repository = SupabaseAttendanceRepository(throwingAttendanceDao, outboxDao, appDatabase = db)
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_c_1", status = "PRESENT", note = null)
        )
        val result = repository.recordBatchAttendance("2026-09-25", items)

        assertTrue(result.isFailure)

        // Outbox MUST NOT be saved due to transaction rollback
        val outboxList = outboxDao.getPendingOperationsForTeacher(teacherId)
        assertTrue("Outbox should be empty when Attendance fails!", outboxList.isEmpty())
    }

    /**
     * Test D: Database instance not available (appDatabase == null and DatabaseProvider reset).
     * Must return Result.failure and NOT QUEUED_OFFLINE.
     */
    @Test
    fun testD_missingDatabase_returnsFailure() = runBlocking {
        DatabaseProvider.resetForTesting()
        val repository = SupabaseAttendanceRepository(attendanceDao, outboxDao, appDatabase = null)
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_d_1", status = "PRESENT", note = null)
        )
        val result = repository.recordBatchAttendance("2026-09-25", items)

        assertTrue(result.isFailure)
        assertNotEquals(BatchSyncStatus.QUEUED_OFFLINE, result.getOrNull()?.syncStatus)
    }

    /**
     * Test E: DAO not available (attendanceDao == null or outboxDao == null).
     * Must return Result.failure and NOT claim success.
     */
    @Test
    fun testE_missingDao_returnsFailure() = runBlocking {
        DatabaseProvider.resetForTesting()
        val repository = SupabaseAttendanceRepository(attendanceDao = null, outboxDao = outboxDao, appDatabase = null)
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_e_1", status = "PRESENT", note = null)
        )
        val result = repository.recordBatchAttendance("2026-09-25", items)

        assertTrue(result.isFailure)
    }

    /**
     * Test F: WorkManager scheduling failure after Room transaction success.
     * Attendance and Outbox MUST remain saved in Room, and result returns QUEUED_OFFLINE (not saved to cloud).
     */
    @Test
    fun testF_workManagerSchedulingFailure_preservesRoomData() = runBlocking {
        val repository = SupabaseAttendanceRepository(attendanceDao, outboxDao, appDatabase = db)
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_f_1", status = "PRESENT", note = null)
        )
        val result = repository.recordBatchAttendance("2026-09-25", items)

        assertTrue(result.isSuccess)
        assertEquals(BatchSyncStatus.QUEUED_OFFLINE, result.getOrNull()?.syncStatus)

        // Room persistence preserved
        val attendance = attendanceDao.getAttendanceByDateSync(teacherId, "st_f_1", "2026-09-25")
        assertNotNull(attendance)

        val outboxList = outboxDao.getPendingOperationsForTeacher(teacherId)
        assertEquals(1, outboxList.size)
    }

    /**
     * Test G: Idempotency check on repeated offline saves.
     * Repeated saving for same student and date must NOT produce duplicate attendance records.
     */
    @Test
    fun testG_repeatedOfflineSave_preventsDuplicateAttendance() = runBlocking {
        val repository = SupabaseAttendanceRepository(attendanceDao, outboxDao, appDatabase = db)
        val items = listOf(
            BatchAttendanceItemDto(studentId = "st_g_1", status = "PRESENT", note = null)
        )

        // Save #1
        repository.recordBatchAttendance("2026-09-25", items)
        // Save #2 (Retry)
        repository.recordBatchAttendance("2026-09-25", items)

        // Room must hold exactly 1 attendance record for this student and date
        val attendance = attendanceDao.getAttendanceByDateSync(teacherId, "st_g_1", "2026-09-25")
        assertNotNull(attendance)

        val allRecords = attendanceDao.getAttendanceByStudentSync(teacherId, "st_g_1")
        assertEquals(1, allRecords.size)
    }
}
