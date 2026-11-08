package com.example

import com.example.data.auth.AuthActionContext
import com.example.data.auth.SafeAuthErrorMapper
import com.example.ui.auth.checkPasswordRequirements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PasswordResetFlowTest {

    @Test
    fun checkPasswordRequirements_validComplexPassword_returnsAllTrue() {
        val reqs = checkPasswordRequirements("StrongPass123!@#")
        assertTrue(reqs.hasMinLength)
        assertTrue(reqs.hasUppercase)
        assertTrue(reqs.hasLowercase)
        assertTrue(reqs.hasDigit)
        assertTrue(reqs.hasSpecialChar)
        assertTrue(reqs.isValid)
    }

    @Test
    fun checkPasswordRequirements_missingUppercase_returnsInvalid() {
        val reqs = checkPasswordRequirements("strongpass123!@#")
        assertTrue(reqs.hasMinLength)
        assertFalse(reqs.hasUppercase)
        assertTrue(reqs.hasLowercase)
        assertTrue(reqs.hasDigit)
        assertTrue(reqs.hasSpecialChar)
        assertFalse(reqs.isValid)
    }

    @Test
    fun checkPasswordRequirements_missingSpecialChar_returnsInvalid() {
        val reqs = checkPasswordRequirements("StrongPass1234")
        assertTrue(reqs.hasMinLength)
        assertTrue(reqs.hasUppercase)
        assertTrue(reqs.hasLowercase)
        assertTrue(reqs.hasDigit)
        assertFalse(reqs.hasSpecialChar)
        assertFalse(reqs.isValid)
    }

    @Test
    fun checkPasswordRequirements_shortLength_returnsInvalid() {
        val reqs = checkPasswordRequirements("Ab1!")
        assertFalse(reqs.hasMinLength)
        assertTrue(reqs.hasUppercase)
        assertTrue(reqs.hasLowercase)
        assertTrue(reqs.hasDigit)
        assertTrue(reqs.hasSpecialChar)
        assertFalse(reqs.isValid)
    }

    @Test
    fun safeAuthErrorMapper_resetPasswordContext_returnsSafeArabicMessage() {
        val error = RuntimeException("Auth session missing or expired")
        val message = SafeAuthErrorMapper.getArabicErrorMessage(error, AuthActionContext.RESET_PASSWORD)
        assertTrue(message.isNotBlank())
        assertFalse(message.contains("http://"))
        assertFalse(message.contains("https://"))
        assertFalse(message.contains("RuntimeException"))
    }
}
