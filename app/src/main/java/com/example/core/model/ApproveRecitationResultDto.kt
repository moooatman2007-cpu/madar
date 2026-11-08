package com.example.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApproveRecitationResultDto(
    @SerialName("success") val success: Boolean = false,
    @SerialName("already_approved") val alreadyApproved: Boolean = false,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("recitation_id") val recitationId: String? = null,
    @SerialName("status") val status: String? = null
)
