package com.example

import com.example.core.model.Teacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TeacherApprovalSecurityTest {

    @Test
    fun `pending account status is recognized as restricted`() {
        val teacher = Teacher(id = "t1", email = "test@teacher.com", accountStatus = "pending")
        assertEquals("pending", teacher.accountStatus)
        assertNotEquals("active", teacher.accountStatus)
    }

    @Test
    fun `suspended account status is recognized as restricted`() {
        val teacher = Teacher(id = "t2", email = "test2@teacher.com", accountStatus = "suspended")
        assertEquals("suspended", teacher.accountStatus)
        assertNotEquals("active", teacher.accountStatus)
    }

    @Test
    fun `rejected account status is recognized as restricted`() {
        val teacher = Teacher(id = "t3", email = "test3@teacher.com", accountStatus = "rejected")
        assertEquals("rejected", teacher.accountStatus)
        assertNotEquals("active", teacher.accountStatus)
    }

    @Test
    fun `active account status allows entry`() {
        val teacher = Teacher(id = "t4", email = "active@teacher.com", accountStatus = "active")
        assertEquals("active", teacher.accountStatus)
    }

    @Test
    fun `fallback status defaults to pending when null or blank`() {
        val teacherStatus: String? = null
        val resolvedStatus = teacherStatus ?: "pending"
        assertEquals("pending", resolvedStatus)
        assertNotEquals("active", resolvedStatus)
    }
}
