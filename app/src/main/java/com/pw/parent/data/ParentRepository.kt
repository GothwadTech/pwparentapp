package com.pw.parent.data

import kotlinx.coroutines.flow.Flow

class ParentRepository(private val parentDao: ParentDao) {

    val allOfflineDrafts: Flow<List<OfflineDraft>> = parentDao.getAllOfflineDrafts()
    val allNotifications: Flow<List<NotificationItem>> = parentDao.getAllNotifications()

    suspend fun saveOfflineDraft(content: String, recipient: String = "General") {
        parentDao.insertOfflineDraft(OfflineDraft(content = content, recipient = recipient))
    }

    suspend fun deleteOfflineDraft(draft: OfflineDraft) {
        parentDao.deleteOfflineDraft(draft)
    }

    suspend fun clearAllDrafts() {
        parentDao.clearAllDrafts()
    }

    suspend fun saveNotification(title: String, message: String) {
        parentDao.insertNotification(NotificationItem(title = title, message = message))
    }

    suspend fun markNotificationAsRead(id: Int) {
        parentDao.markNotificationAsRead(id)
    }

    suspend fun deleteNotification(id: Int) {
        parentDao.deleteNotification(id)
    }

    suspend fun clearAllNotifications() {
        parentDao.clearAllNotifications()
    }
}
