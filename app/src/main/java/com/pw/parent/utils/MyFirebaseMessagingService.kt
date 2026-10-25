package com.pw.parent.utils

import android.util.Log
import com.pw.parent.BuildConfig
import com.pw.parent.data.ParentDatabase
import com.pw.parent.data.ParentRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val tag = "ParentFCMService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when a new FCM token is generated or refreshed.
     * This token must be saved to Supabase (via the WebApp client or an direct endpoint)
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(tag, "Refreshed FCM Token: $token")
        
        // Save the token locally so Javascript can fetch it via window.<app.jsBridgeName>.getPushToken()
        val sharedPrefs = getSharedPreferences(BuildConfig.PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit().putString("fcm_token", token).apply()
    }

    /**
     * Called when a message is received while the app is in the background or foreground.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(tag, "From: ${remoteMessage.from}")

        // 1. Extract Title and Message Body from Notification payload or Data payload
        var title = remoteMessage.notification?.title
        var body = remoteMessage.notification?.body

        // Supabase Edge Functions typically send a "data" payload to have maximum control over notifications
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(tag, "Message data payload: ${remoteMessage.data}")
            if (title.isNullOrEmpty()) {
                title = remoteMessage.data["title"]
            }
            if (body.isNullOrEmpty()) {
                body = remoteMessage.data["message"] ?: remoteMessage.data["body"]
            }
        }

        val finalTitle = title ?: "${BuildConfig.APP_NAME} Message"
        val finalBody = body ?: "You have received a new message."

        Log.d(tag, "Displaying notification: Title=$finalTitle, Body=$finalBody")

        // 2. Persist the notification in the local room database so user can see logs inside the app history
        saveNotificationToLocalDb(finalTitle, finalBody)

        // 3. Show native system notification banner
        ParentNotificationHelper.showNotification(applicationContext, finalTitle, finalBody)
    }

    private fun saveNotificationToLocalDb(title: String, message: String) {
        serviceScope.launch {
            try {
                val db = ParentDatabase.getDatabase(applicationContext)
                val repository = ParentRepository(db.parentDao())
                repository.saveNotification(title, message)
            } catch (e: Exception) {
                Log.e(tag, "Failed to persist notification in Room DB", e)
            }
        }
    }
}
