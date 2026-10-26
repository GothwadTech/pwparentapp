package com.pw.parent.ui

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.pw.parent.BuildConfig
import java.util.UUID

class ParentJavascriptInterface(
    private val context: Context,
    private val viewModel: ParentViewModel
) {
    private val tag = "ParentJavascriptInterface"
    // Fallback token handed to the page when Firebase is not configured. Derived from
    // app.name so a rebrand does not leave the old product name in the payload.
    private val appToken =
        BuildConfig.APP_NAME.lowercase().replace(" ", "_") + "_app_tok_" +
            UUID.randomUUID().toString().substring(0, 8)

    /**
     * Trigger a native Android push/local notification from JavaScript.
     * JavaScript call: window.<app.jsBridgeName>.postNotification("Group Chat", "Alice sent a photo");
     */
    @JavascriptInterface
    fun postNotification(title: String, message: String) {
        Log.d(tag, "postNotification: Title=$title, Message=$message")
        viewModel.triggerLocalNotification(title, message)
    }

    /**
     * Allows website to request a push registration token.
     * JavaScript call: var token = window.<app.jsBridgeName>.getPushToken();
     */
    @JavascriptInterface
    fun getPushToken(): String {
        val sharedPrefs = context.getSharedPreferences(BuildConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val cachedToken = sharedPrefs.getString("fcm_token", null)
        Log.d(tag, "getPushToken requested. Cached token available: ${cachedToken != null}")
        return cachedToken ?: appToken
    }

    /**
     * Check if device is connected to internet.
     * JavaScript call: var online = window.<app.jsBridgeName>.isDeviceOnline();
     */
    @JavascriptInterface
    fun isDeviceOnline(): Boolean {
        return viewModel.isOnline.value
    }

    /**
     * Save an offline draft from the web app client.
     * JavaScript call: window.<app.jsBridgeName>.saveOfflineDraft("Draft text goes here");
     */
    @JavascriptInterface
    fun saveOfflineDraft(content: String) {
        Log.d(tag, "saveOfflineDraft: $content")
        viewModel.saveDraft(content)
    }

    /**
     * Show a simple toast message.
     * JavaScript call: window.<app.jsBridgeName>.showToast("Logged in successfully!");
     */
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Kept for web-app compatibility. The Parent App is LIGHT-ONLY, so this is a no-op:
     * the site cannot switch the native chrome (status bar, navigation bar, splash) to dark.
     * JavaScript call: window.<app.jsBridgeName>.setTheme(true);  // ignored
     */
    @JavascriptInterface
    fun setTheme(isDark: Boolean) {
        Log.d(tag, "setTheme(Boolean) received: isDark=$isDark (ignored — app is light-only)")
    }

    /**
     * Kept for web-app compatibility. LIGHT-ONLY host: dark requests are ignored.
     * JavaScript call: window.<app.jsBridgeName>.setTheme("dark");  // ignored
     */
    @JavascriptInterface
    fun setTheme(theme: String) {
        Log.d(tag, "setTheme(String) received: theme=$theme (ignored — app is light-only)")
    }
}
