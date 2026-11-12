package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.Student
import com.example.data.SupabaseClientProvider
import com.example.data.auth.AccountSessionManager
import com.example.data.auth.AuthActionContext
import com.example.data.auth.SafeAuthErrorMapper
import com.example.data.local.AppDatabase
import com.example.data.local.DatabaseProvider
import com.example.data.local.entity.OutboxEntity
import com.example.data.local.mapper.toEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SafeLogoutOutboxProtectionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseProvider.setDatabase(database, context)
    }

    @After
    fun tearDown() {
        database.close()
        DatabaseProvider.resetForTesting()
        AccountSessionManager.setActiveTeacherId(null)
        SupabaseClientProvider.mockTeacherId = null
        Dispatchers.resetMain()
    }

    // --- Scenario 1: Write starts first, holds lock, Logout waits and sees operation, aborts safely ---
    @Test
    fun testConcurrencyWriteStartsFirstLogoutWaitsAndAborts() = runTest {
        val teacherId = "teacher_race_1"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        val outboxDao = database.outboxDao()
        val writeStarted = CompletableDeferred<Unit>()
        val logoutAttempted = CompletableDeferred<Unit>()

        // Coroutine A: Repository write holding Shared Gate
        val writeJob = launch(Dispatchers.IO) {
            AccountSessionManager.runIfSessionActive(teacherId) {
                writeStarted.complete(Unit)
                // Simulate holding local write brief window
                logoutAttempted.await()
                outboxDao.insertOperation(
                    OutboxEntity(
                        id = UUID.randomUUID().toString(),
                        operationType = "INSERT",
                        entityType = "STUDENT",
                        entityId = "s_race_1",
                        payload = "{}",
                        createdAt = System.currentTimeMillis(),
                        status = "PENDING",
                        teacherId = teacherId
                    )
                )
            }
        }

        // Coroutine B: Concurrent Logout attempt
        val logoutDeferred = async(Dispatchers.IO) {
            writeStarted.await()
            logoutAttempted.complete(Unit)
            AccountSessionManager.logout()
        }

        writeJob.join()
        val logoutResult = logoutDeferred.await()

        // Logout MUST fail because write completed first and inserted pending outbox op
        assertTrue(logoutResult.isFailure)
        assertEquals(1, outboxDao.getUnsyncedOperationsForTeacher(teacherId).size)
    }

    // --- Scenario 2: Logout starts first, new Write attempt is rejected ---
    @Test
    fun testConcurrencyLogoutStartsFirstNewWriteRejected() = runTest {
        val teacherId = "teacher_race_2"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        val outboxDao = database.outboxDao()
        val studentDao = database.studentDao()

        // 1. Perform clean logout with empty outbox
        val logoutResult = AccountSessionManager.logout()
        assertTrue(logoutResult.isSuccess)

        // 2. Attempt new write via Shared Session Write Gate after logout
        val writeResult = AccountSessionManager.runIfSessionActive(teacherId) {
            studentDao.upsertStudent(
                Student(
                    studentId = "s_race_2",
                    studentCode = "ST-002",
                    fullName = "طالب مرفوض",
                    gradeId = "g1",
                    teacherId = teacherId
                ).toEntity()
            )
            outboxDao.insertOperation(
                OutboxEntity(
                    id = UUID.randomUUID().toString(),
                    operationType = "INSERT",
                    entityType = "STUDENT",
                    entityId = "s_race_2",
                    payload = "{}",
                    createdAt = System.currentTimeMillis(),
                    status = "PENDING",
                    teacherId = teacherId
                )
            )
            true
        }

        // Write must be rejected (returns null) and no orphan outbox operation is created
        assertNull(writeResult)
        assertEquals(0, outboxDao.getUnsyncedOperationsForTeacher(teacherId).size)
    }

    // 1. Database unavailable: prevents logout and returns Result.failure
    @Test
    fun testLogoutFailsClosedWhenDatabaseUnavailable() = runTest {
        val teacherId = "teacher_db_null"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        DatabaseProvider.resetForTesting()

        val result = AccountSessionManager.logout()
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.localizedMessage ?: ""
        assertTrue(msg.contains("قاعدة البيانات"))
    }

    // 2. Outbox query failure: prevents logout
    @Test
    fun testLogoutFailsClosedWhenOutboxQueryFails() = runTest {
        val teacherId = "teacher_query_fail"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        database.close()

        val result = AccountSessionManager.logout()
        assertTrue(result.isFailure)
    }

    // 3. PENDING operations: prevents logout and retains data
    @Test
    fun testLogoutBlockedWhenPendingOperationExists() = runTest {
        val teacherId = "teacher_pending"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        val studentDao = database.studentDao()
        val outboxDao = database.outboxDao()

        studentDao.upsertStudent(
            Student(
                studentId = "s_pending",
                studentCode = "ST-001",
                fullName = "طالب معلق",
                gradeId = "g1",
                teacherId = teacherId
            ).toEntity()
        )

        outboxDao.insertOperation(
            OutboxEntity(
                id = UUID.randomUUID().toString(),
                operationType = "INSERT",
                entityType = "STUDENT",
                entityId = "s_pending",
                payload = "{}",
                createdAt = System.currentTimeMillis(),
                status = "PENDING",
                teacherId = teacherId
            )
        )

        val result = AccountSessionManager.logout()
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.localizedMessage ?: ""
        assertTrue(msg.contains("توجد بيانات تنتظر المزامنة"))

        assertEquals(1, studentDao.getAllStudentsSync(teacherId).size)
        assertEquals(1, outboxDao.getUnsyncedOperationsForTeacher(teacherId).size)
    }

    // 4. FAILED operations (transient vs permanent): prevents logout
    @Test
    fun testLogoutBlockedWhenFailedOperationExists() = runTest {
        val teacherId = "teacher_failed"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        val outboxDao = database.outboxDao()

        outboxDao.insertOperation(
            OutboxEntity(
                id = UUID.randomUUID().toString(),
                operationType = "INSERT",
                entityType = "STUDENT",
                entityId = "s_failed_perm",
                payload = "{}",
                createdAt = System.currentTimeMillis(),
                status = "FAILED",
                lastError = "violates unique constraint key (23505)",
                teacherId = teacherId
            )
        )

        val result = AccountSessionManager.logout()
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.localizedMessage ?: ""
        assertTrue(msg.contains("شكل دائم"))
    }

    // 5. SYNCING operations: prevents logout
    @Test
    fun testLogoutBlockedWhenSyncingOperationExists() = runTest {
        val teacherId = "teacher_syncing"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        val outboxDao = database.outboxDao()
        outboxDao.insertOperation(
            OutboxEntity(
                id = UUID.randomUUID().toString(),
                operationType = "UPDATE",
                entityType = "STUDENT",
                entityId = "s_syncing",
                payload = "{}",
                createdAt = System.currentTimeMillis(),
                status = "SYNCING",
                teacherId = teacherId
            )
        )

        val result = AccountSessionManager.logout()
        assertTrue(result.isFailure)
        assertEquals(1, outboxDao.getUnsyncedOperationsForTeacher(teacherId).size)
    }

    // 6. Empty Outbox: allows successful logout
    @Test
    fun testLogoutWithEmptyOutboxSucceeds() = runTest {
        val teacherId = "teacher_clean"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        val studentDao = database.studentDao()
        studentDao.upsertStudent(
            Student(
                studentId = "s_clean",
                studentCode = "ST-002",
                fullName = "طالب نظيف",
                gradeId = "g1",
                teacherId = teacherId
            ).toEntity()
        )

        val result = AccountSessionManager.logout()
        assertTrue(result.isSuccess)
        assertEquals(0, studentDao.getAllStudentsSync(teacherId).size)
        assertFalse(AccountSessionManager.isSessionActive(teacherId))
    }

    // 7. Arabic error message reaches UI via SafeAuthErrorMapper
    @Test
    fun testArabicErrorMessageReachesUI() = runTest {
        val teacherId = "teacher_ui_msg"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        val outboxDao = database.outboxDao()
        outboxDao.insertOperation(
            OutboxEntity(
                id = UUID.randomUUID().toString(),
                operationType = "INSERT",
                entityType = "STUDENT",
                entityId = "s_ui",
                payload = "{}",
                createdAt = System.currentTimeMillis(),
                status = "PENDING",
                teacherId = teacherId
            )
        )

        val result = AccountSessionManager.logout()
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        val mappedMsg = SafeAuthErrorMapper.getArabicErrorMessage(ex, AuthActionContext.SIGN_OUT)

        assertTrue(mappedMsg.contains("توجد بيانات تنتظر المزامنة"))
    }

    // 8. Successful sync followed by successful logout
    @Test
    fun testSyncCompletionAllowsNormalLogout() = runTest {
        val teacherId = "teacher_sync_done"
        SupabaseClientProvider.mockTeacherId = teacherId
        AccountSessionManager.setActiveTeacherId(teacherId)

        val outboxDao = database.outboxDao()
        val opId = UUID.randomUUID().toString()
        val op = OutboxEntity(
            id = opId,
            operationType = "INSERT",
            entityType = "STUDENT",
            entityId = "s_done",
            payload = "{}",
            createdAt = System.currentTimeMillis(),
            status = "PENDING",
            teacherId = teacherId
        )
        outboxDao.insertOperation(op)

        val failResult = AccountSessionManager.logout()
        assertTrue(failResult.isFailure)

        outboxDao.updateOperation(op.copy(status = "SYNCED"))

        val successResult = AccountSessionManager.logout()
        assertTrue(successResult.isSuccess)
    }
}
