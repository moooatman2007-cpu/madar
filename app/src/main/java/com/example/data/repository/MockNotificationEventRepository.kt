package com.example.data.repository

import com.example.core.model.NotificationEvent

typealias NotificationEventRepositoryMock = MockNotificationEventRepository

class MockNotificationEventRepository : NotificationEventRepository {
    private val createdEvents = mutableListOf<NotificationEvent>()
    private val pendingRecitations = mutableListOf<com.example.core.model.PendingRecitationNotificationDto>()
    var getPendingFailure: Throwable? = null
    var approveResultBuilder: ((String) -> Result<ApproveRecitationResult>)? = null

    override suspend fun createNotificationEvent(event: NotificationEvent): NotificationEventCreationResult {
        createdEvents.add(event)
        return NotificationEventCreationResult.Created(event)
    }

    override suspend fun getPendingRecitations(): Result<List<com.example.core.model.PendingRecitationNotificationDto>> {
        val failure = getPendingFailure
        if (failure != null) {
            return Result.failure(failure)
        }
        return Result.success(pendingRecitations.toList())
    }

    override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult {
        return NotificationWorkerResult.Success("Mock worker trigger success")
    }

    override suspend fun approveRecitation(recitationId: String): Result<ApproveRecitationResult> {
        val custom = approveResultBuilder?.invoke(recitationId)
        if (custom != null) {
            if (custom.isSuccess) {
                pendingRecitations.removeAll { it.recitationId == recitationId }
            }
            return custom
        }
        pendingRecitations.removeAll { it.recitationId == recitationId }
        return Result.success(ApproveRecitationResult.Success("event_mock", recitationId))
    }

    fun setPendingRecitations(items: List<com.example.core.model.PendingRecitationNotificationDto>) {
        pendingRecitations.clear()
        pendingRecitations.addAll(items)
    }

    fun addPendingRecitation(item: com.example.core.model.PendingRecitationNotificationDto) {
        pendingRecitations.add(item)
    }

    fun getCreatedEvents(): List<NotificationEvent> = createdEvents.toList()
    fun clear() {
        createdEvents.clear()
        pendingRecitations.clear()
        getPendingFailure = null
        approveResultBuilder = null
    }
}
