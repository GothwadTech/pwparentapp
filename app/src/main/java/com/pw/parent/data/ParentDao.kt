package com.pw.parent.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "offline_drafts")
data class OfflineDraft(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val recipient: String = "General",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notification_items")
data class NotificationItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Dao
interface ParentDao {
    // Operations for Offline Drafts
    @Query("SELECT * FROM offline_drafts ORDER BY timestamp DESC")
    fun getAllOfflineDrafts(): Flow<List<OfflineDraft>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOfflineDraft(draft: OfflineDraft)

    @Delete
    suspend fun deleteOfflineDraft(draft: OfflineDraft)

    @Query("DELETE FROM offline_drafts")
    suspend fun clearAllDrafts()

    // Operations for Notifications
    @Query("SELECT * FROM notification_items ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationItem)

    @Query("UPDATE notification_items SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Int)

    @Query("DELETE FROM notification_items WHERE id = :id")
    suspend fun deleteNotification(id: Int)

    @Query("DELETE FROM notification_items")
    suspend fun clearAllNotifications()
}
