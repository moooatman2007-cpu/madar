package com.example.data.auth

import android.util.Log
import com.example.data.SupabaseClientProvider
import com.example.data.local.DatabaseProvider
import com.example.data.repository.RepositoryProvider
import com.example.data.repository.SupabaseGradeRepository
import com.example.data.repository.SupabaseStudentRepository
import com.example.data.repository.SupabaseTeacherRepository
import com.example.data.sync.OutboxSyncScheduler
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates secure account session teardown and multi-tenant data isolation.
 *
 * Two distinct teardown modes:
 *
 * [logout] - the teacher explicitly signs out:
 * 1. Background WorkManager synchronization is immediately cancelled.
 * 2. In-flight background fetch and sync writes are rejected immediately.
 * 3. All cached Room tables scoped to that teacher are atomically deleted via Room transaction.
 * 4. Repository in-memory caches are reset to empty.
 * 5. Pending Outbox operations of that teacher are purged.
 * 6. Supabase Auth session is terminated.
 *
 * [onSessionTerminated] - the session ended involuntarily (expiry, revocation, no stored session,
 * or a spoofed deep link). Steps 1, 2 and 4 run, but local data and the Outbox are KEPT:
 * they are tenant-scoped (every query and the sync worker filter by teacher_id), so they can
 * never be read or executed under another account, and they are synced after the same
 * teacher signs in again. Wiping them here silently destroyed unsynced offline work.
 */
object AccountSessionManager {

    private const val TAG = "AccountSessionManager"
    private val mutex = Mutex()
    @Volatile
    private var activeTeacherId: String? = null

    fun setActiveTeacherId(teacherId: String?) {
        activeTeacherId = teacherId
        if (com.example.BuildConfig.DEBUG) Log.d(TAG, "Active teacher set")
    }

    fun getActiveTeacherId(): String? {
        return activeTeacherId
            ?: SupabaseClientProvider.mockTeacherId
            ?: try { SupabaseClientProvider.client.auth.currentUserOrNull()?.id } catch (_: Exception) { null }
    }

    fun isSessionActive(teacherId: String?): Boolean {
        if (teacherId.isNullOrBlank()) return false

        val mockId = SupabaseClientProvider.mockTeacherId
        if (mockId != null) {
            return mockId == teacherId
        }

        val remoteId = try { SupabaseClientProvider.client.auth.currentUserOrNull()?.id } catch (_: Exception) { null }
        if (remoteId == null || remoteId != teacherId) {
            return false
        }

        // If explicitly tracked, must match tracked ID
        val tracked = activeTeacherId
        return tracked == null || tracked == teacherId
    }

    /** Explicit sign-out: wipes this teacher's local data and Outbox, then signs out remotely. */
    suspend fun logout(): Result<Unit> =
        handleSessionTermination(remoteSignOut = true, wipeLocalData = true)

    /**
     * Signs out remotely but keeps local data and the Outbox. Used right after e-mail confirmation
     * (the user must log in explicitly), where there is no prior teacher data to protect or wipe.
     */
    suspend fun endSessionKeepingLocalData(): Result<Unit> =
        handleSessionTermination(remoteSignOut = true, wipeLocalData = false)

    /**
     * Involuntary end of session. Never deletes Room data or pending Outbox operations.
     */
    suspend fun onSessionTerminated(): Result<Unit> =
        handleSessionTermination(remoteSignOut = false, wipeLocalData = false)

    private suspend fun handleSessionTermination(
        remoteSignOut: Boolean,
        wipeLocalData: Boolean
    ): Result<Unit> = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            try {
                val targetTeacherId = getActiveTeacherId()
                Log.d(TAG, "Starting session teardown (remoteSignOut=$remoteSignOut, wipeLocalData=$wipeLocalData)")

                // 1. Immediately cancel active and queued WorkManager synchronization jobs
                try {
                    OutboxSyncScheduler.cancelSync()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cancel sync scheduler", e)
                }

                // 2. Invalidate active teacher tracking to reject any in-flight coroutine writes immediately
                activeTeacherId = null
                SupabaseClientProvider.mockTeacherId = null

                // 3. Wipe tenant-scoped Room data + Outbox ONLY on explicit logout.
                //    (Unknown teacher => do NOT fall back to clearAllData(): that would delete
                //    other accounts' unsynced work.)
                if (wipeLocalData && !targetTeacherId.isNullOrBlank()) {
                    try {
                        DatabaseProvider.clearDataForTeacher(targetTeacherId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to clear Room database during logout", e)
                    }
                }

                // 4. Clear in-memory repository caches
                try {
                    (RepositoryProvider.studentRepository as? SupabaseStudentRepository)?.clearCache()
                    (RepositoryProvider.gradeRepository as? SupabaseGradeRepository)?.clearCache()
                    (RepositoryProvider.teacherRepository as? SupabaseTeacherRepository)?.clearCache()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear repository in-memory caches", e)
                }

                // 5. Terminate remote Supabase Auth session if requested
                if (remoteSignOut) {
                    try {
                        SupabaseClientProvider.client.auth.signOut()
                    } catch (e: Exception) {
                        Log.w(TAG, "Remote signOut failed or device offline. Local tenant data cleared successfully.", e)
                    }
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error during account session teardown", e)
                Result.failure(e)
            }
        }
    }
}
