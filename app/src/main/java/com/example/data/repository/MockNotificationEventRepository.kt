package com.example.data.repository

import com.example.core.model.NotificationEvent

typealias NotificationEventRepositoryMock = MockNotificationEventRepository

class MockNotificationEventRepository : NotificationEventRepository {
    private val createdEvents = mutableListOf<NotificationEvent>()

    override suspend fun createNotificationEvent(event: NotificationEvent): NotificationEventCreationResult {
        createdEvents.add(event)
        return NotificationEventCreationResult.Created(event)
    }

    override suspend fun triggerNotificationWorker(eventType: String?): NotificationWorkerResult {
        return NotificationWorkerResult.Success("Mock worker trigger success")
    }

    fun getCreatedEvents(): List<NotificationEvent> = createdEvents.toList()
    fun clear() = createdEvents.clear()
}
