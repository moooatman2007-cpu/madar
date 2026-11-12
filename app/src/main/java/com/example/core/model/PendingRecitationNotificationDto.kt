package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO representing an unapproved recitation notification event
 * returned by Supabase RPC: get_pending_recitation_notifications().
 */
@Serializable
data class PendingRecitationNotificationDto(
    @SerialName("recitation_id") val recitationId: String,
    @SerialName("event_id") val eventId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("student_name") val studentName: String? = null,
    @SerialName("recitation_title") val recitationTitle: String? = null,
    @SerialName("recitation_date") val recitationDate: String? = null,
    @SerialName("score") val score: Double = 0.0,
    @SerialName("max_score") val maxScore: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("event_status") val eventStatus: String = "PENDING",
    @SerialName("teacher_approved") val teacherApproved: Boolean = false
)
