package com.example.data.auth

import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.data.SupabaseClientProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

object DeepLinkAuthHandler {
    private const val TAG = "DeepLinkAuthHandler"
    var dispatcher: CoroutineDispatcher = Dispatchers.IO
    private val scope get() = CoroutineScope(SupervisorJob() + dispatcher)
    private val lastProcessedUri = AtomicReference<String?>(null)

    private const val ACTIVE_SESSION_MSG =
        "أنت مسجّل الدخول حالياً. سجّل الخروج أولاً ثم افتح رابط التأكيد مرة أخرى."

    /**
     * Custom-scheme deep links can be fired by ANY app or web page, so they must never be able to
     * end or wipe a session that is already signed in. Auth links are only processed when no
     * teacher is currently signed in.
     */
    private suspend fun hasExistingSession(): Boolean = try {
        val auth = SupabaseClientProvider.client.auth
        withTimeoutOrNull(3_000) { auth.awaitInitialization() }
        auth.currentSessionOrNull() != null
    } catch (_: Exception) {
        false
    }

    /**
     * Handles an incoming deep link URI safely.
     * Guarantees idempotent execution: the same URI will not be processed twice
     * across onCreate and onNewIntent.
     */
    fun handleUri(uri: Uri?): Job? {
        if (uri == null) return null

        val uriString = uri.toString()
        if (lastProcessedUri.get() == uriString) {
            return null
        }

        val parsed = DeepLinkAuthParser.parse(uri)
        if (parsed is DeepLinkAuthResult.Ignored) {
            return null
        }

        // Atomically set last processed URI to prevent duplicate execution
        if (!lastProcessedUri.compareAndSet(lastProcessedUri.get(), uriString)) {
            // Check again in case of race
            if (lastProcessedUri.get() == uriString) return null
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Processing deep link of type: ${parsed.javaClass.simpleName}")
        }

        return when (parsed) {
            is DeepLinkAuthResult.AuthError -> {
                val raw = "${parsed.errorCode.orEmpty()} ${parsed.errorDescription}"
                val errorMsg = SafeAuthErrorMapper.mapAuthErrorToArabic(raw, AuthActionContext.EMAIL_CONFIRM)
                // Display only. A spoofed error link must not terminate a valid session.
                AuthFeedbackBus.emitError(errorMsg)
                null
            }

            is DeepLinkAuthResult.CodeExchange -> {
                scope.launch {
                    val isRecovery = parsed.type.equals("recovery", ignoreCase = true)
                    if (hasExistingSession()) {
                        AuthFeedbackBus.emitError(ACTIVE_SESSION_MSG)
                        return@launch
                    }
                    try {
                        // 1. Exchange PKCE code for session using supabase-kt 3.1.1 API
                        SupabaseClientProvider.client.auth.exchangeCodeForSession(
                            code = parsed.code,
                            saveSession = false
                        )
                        if (isRecovery) {
                            // Recovery flow: keep session active in memory so updateUser can be called,
                            // and notify UI to route to ResetPasswordScreen.
                            AuthFeedbackBus.emitPasswordRecovery()
                        } else {
                            // 2. Clear any lingering session to prevent auto-login
                            AccountSessionManager.endSessionKeepingLocalData()
                            AuthFeedbackBus.emitSuccess("تم تأكيد البريد الإلكتروني بنجاح، يمكنك الآن تسجيل الدخول.")
                        }
                    } catch (e: Exception) {
                        SafeAuthLogger.logAuthError(TAG, "CodeExchange", e)
                        AccountSessionManager.endSessionKeepingLocalData()
                        val msg = if (isRecovery) {
                            "رابط استعادة كلمة المرور غير صالح أو منتهي الصلاحية، يرجى طلب رابط جديد."
                        } else {
                            SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.EMAIL_CONFIRM)
                        }
                        AuthFeedbackBus.emitError(msg)
                    }
                }
            }

            is DeepLinkAuthResult.EmailOtp -> {
                scope.launch {
                    val isRecovery = parsed.type.equals("recovery", ignoreCase = true)
                    if (hasExistingSession()) {
                        AuthFeedbackBus.emitError(ACTIVE_SESSION_MSG)
                        return@launch
                    }
                    try {
                        // Determine OTP email type
                        val otpType = when (parsed.type.lowercase()) {
                            "signup" -> OtpType.Email.SIGNUP
                            "recovery" -> OtpType.Email.RECOVERY
                            "invite" -> OtpType.Email.INVITE
                            "email_change" -> OtpType.Email.EMAIL_CHANGE
                            else -> OtpType.Email.SIGNUP
                        }

                        // Verify Email OTP via tokenHash using supabase-kt 3.1.1 API
                        SupabaseClientProvider.client.auth.verifyEmailOtp(
                            type = otpType,
                            tokenHash = parsed.tokenHash
                        )

                        if (isRecovery) {
                            // Recovery flow: keep session active in memory so updateUser can be called,
                            // and notify UI to route to ResetPasswordScreen.
                            AuthFeedbackBus.emitPasswordRecovery()
                        } else {
                            // Clear any lingering session to respect the policy:
                            // Signup / email confirm does NOT auto login; user logs in explicitly.
                            AccountSessionManager.endSessionKeepingLocalData()
                            AuthFeedbackBus.emitSuccess("تم تأكيد البريد الإلكتروني بنجاح، يمكنك الآن تسجيل الدخول.")
                        }
                    } catch (e: Exception) {
                        SafeAuthLogger.logAuthError(TAG, "EmailOtp", e)
                        AccountSessionManager.endSessionKeepingLocalData()
                        val msg = if (isRecovery) {
                            "رابط استعادة كلمة المرور غير صالح أو منتهي الصلاحية، يرجى طلب رابط جديد."
                        } else {
                            SafeAuthErrorMapper.getArabicErrorMessage(e, AuthActionContext.EMAIL_CONFIRM)
                        }
                        AuthFeedbackBus.emitError(msg)
                    }
                }
            }

            is DeepLinkAuthResult.FragmentSession -> {
                scope.launch {
                    val isRecovery = parsed.type.equals("recovery", ignoreCase = true)
                    if (hasExistingSession()) {
                        AuthFeedbackBus.emitError(ACTIVE_SESSION_MSG)
                        return@launch
                    }
                    try {
                        if (isRecovery) {
                            val params = parsed.fragment.split("&").associate {
                                val parts = it.split("=", limit = 2)
                                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                            }
                            val accessToken = params["access_token"]
                            val refreshToken = params["refresh_token"]
                            if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                                SupabaseClientProvider.client.auth.importAuthToken(accessToken, refreshToken, autoRefresh = false)
                                AuthFeedbackBus.emitPasswordRecovery()
                            } else {
                                AccountSessionManager.endSessionKeepingLocalData()
                                AuthFeedbackBus.emitError("رابط استعادة كلمة المرور غير صالح أو منتهي الصلاحية.")
                            }
                        } else {
                            AccountSessionManager.endSessionKeepingLocalData()
                            AuthFeedbackBus.emitSuccess("تم تأكيد البريد الإلكتروني بنجاح، يمكنك الآن تسجيل الدخول.")
                        }
                    } catch (e: Exception) {
                        SafeAuthLogger.logAuthError(TAG, "FragmentSession", e)
                        AccountSessionManager.endSessionKeepingLocalData()
                        AuthFeedbackBus.emitError("رابط استعادة كلمة المرور غير صالح أو منتهي الصلاحية.")
                    }
                }
            }

            is DeepLinkAuthResult.Ignored -> null
        }
    }

    /**
     * Resets the cache of last processed URI (useful for unit testing).
     */
    fun resetForTesting() {
        lastProcessedUri.set(null)
    }
}
