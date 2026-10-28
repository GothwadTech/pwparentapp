package com.pw.parent

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pw.parent.data.ParentDatabase
import com.pw.parent.data.ParentRepository
import com.pw.parent.ui.ParentViewModel
import com.pw.parent.ui.ParentViewModelFactory
import com.pw.parent.ui.ParentJavascriptInterface
import com.pw.parent.ui.theme.MyApplicationTheme
import com.pw.parent.utils.ParentNotificationHelper

/**
 * One-shot handshake between the WebView and the launch splash.
 *
 * The splash stays on screen only while the web app has not painted its first frame, so the
 * user never sees a blank white window between the brand splash and the site.
 * [markContentVisible] is called from WebViewClient as soon as the first frame is committed
 * (or when the main frame fails, so the retry panel can appear), and the hard timeout in
 * [MainActivity] guarantees the splash can never hang forever.
 */
internal object SplashGate {

    @Volatile private var contentVisible: Boolean = false

    @Volatile private var timedOut: Boolean = false

    /** True once the splash may be removed (content painted, error shown, or timeout hit). */
    val isReady: Boolean
        get() = contentVisible || timedOut

    fun markContentVisible() {
        contentVisible = true
    }

    fun markTimedOut() {
        timedOut = true
    }
}

class MainActivity : ComponentActivity() {

    private val splashHandler = Handler(Looper.getMainLooper())
    private val splashTimeout = Runnable { SplashGate.markTimedOut() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash FIRST (before super.onCreate): androidX core-splashscreen keeps the branded
        // splash on screen until SplashGate.isReady, which the WebView flips as soon as the
        // first page frame is committed — no blank screen in between, on every device.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { SplashGate.isReady }
        super.onCreate(savedInstanceState)

        // Safety net: never let a slow/hanging network trap the user on the splash.
        splashHandler.postDelayed(splashTimeout, 12_000L)

        // Light-only chrome: white status bar + white navigation bar with DARK system icons
        // (clock, battery, wifi, gesture/3-button icons) on every API level. The Compose
        // surfaces behind the bars are pure white too, so the bars never look tinted.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))

        // Setup global WebView ServiceWorker preferences for robust offline background caching
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val swController = ServiceWorkerController.getInstance()
                val swSettings = swController.serviceWorkerWebSettings
                
                val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val capabilities = cm.getNetworkCapabilities(activeNetwork)
                val actuallyOnline = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                swSettings.cacheMode = if (actuallyOnline) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
                // Remote-content app: service workers have no business reading file:// or content://
                swSettings.allowContentAccess = false
                swSettings.allowFileAccess = false
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error configuring ServiceWorkerController on launch", e)
            }
        }

        // Create the notification channels on launch
        ParentNotificationHelper.createNotificationChannel(applicationContext)

        // Local scheduled reminders (Monday, every 2 hours, 06:00–18:00). These need no
        // Firebase and no backend: WorkManager fires them on-device. Recomputing the next slot
        // on every launch keeps the schedule accurate after reboots, time changes or kills.
        com.pw.parent.utils.ReminderScheduler.ensureScheduled(applicationContext)

        // Setup repository
        val database = ParentDatabase.getDatabase(applicationContext)
        val repository = ParentRepository(database.parentDao())

        // Fetch the FCM registration token on launch — but ONLY if Firebase is really configured.
        // Previously this block fabricated a placeholder FirebaseApp ("placeholder-api-key-to-allow-init")
        // when google-services.json was absent, so token retrieval silently failed forever while the
        // service, helper and JS bridge all looked wired up. Better to fail loudly.
        // To enable push: add google-services.json and apply the com.google.gms.google-services plugin.
        try {
            if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
                android.util.Log.w(
                    "MainActivity",
                    "Firebase is not configured (no google-services.json / google-services plugin). " +
                        "Push notifications are DISABLED."
                )
            } else {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        android.util.Log.d("MainActivity", "Successfully retrieved initial FCM token: $token")
                        val sharedPrefs = getSharedPreferences(BuildConfig.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString("fcm_token", token).apply()
                    } else {
                        android.util.Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Firebase initialization or token fetch error", e)
        }

        setContent {
            val parentViewModel: ParentViewModel = viewModel(
                factory = ParentViewModelFactory(application, repository)
            )

            // Light-only: the container never follows the system dark theme.
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ParentAppScreen(viewModel = parentViewModel)
                }
            }
        }
    }
}

/** Forces light UA styling (form controls, scrollbars, `prefers-color-scheme`) on the page. */
private const val LIGHT_MODE_JS = """
(function() {
  try {
    var doc = document.documentElement;
    if (!doc) return;
    doc.style.colorScheme = 'light';
    if (!doc.querySelector('meta[name="color-scheme"]')) {
      var meta = document.createElement('meta');
      meta.name = 'color-scheme';
      meta.content = 'light';
      (document.head || doc).appendChild(meta);
    }
  } catch (e) {}
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ParentAppScreen(viewModel: ParentViewModel) {
    val context = LocalContext.current
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isError by viewModel.isWebViewError.collectAsStateWithLifecycle()
    val progress by viewModel.loadProgress.collectAsStateWithLifecycle()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Passed into the AndroidView factory as a plain callback (state writes stay out of the factory)
    val onWebViewReady: (WebView) -> Unit = { webViewInstance = it }

    var customFilePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            var results: Array<Uri>? = null
            if (data != null) {
                val dataString = data.dataString
                val clipData = data.clipData
                if (clipData != null) {
                    results = Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
            customFilePathCallback?.onReceiveValue(results)
        } else {
            customFilePathCallback?.onReceiveValue(null)
        }
        customFilePathCallback = null
    }

    // Intercept back actions so page history goes back rather than exiting app
    BackHandler(enabled = webViewInstance != null) {
        val webView = webViewInstance
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            (context as? Activity)?.finish()
        }
    }

    // Ask only for what the app actually uses: notifications always, camera/mic lazily
    // (WebView/WebRTC only triggers them when the page needs them).
    // Contacts, phone state and CALL_PHONE were requested here but never used anywhere in the
    // codebase — those are Play Store "sensitive permission without core functionality" rejects.
    // A WebView permission request that is waiting for the OS runtime permission dialog.
    // Granting it inside onPermissionRequest alone does nothing: the app-level permission
    // must be granted first, so the request is parked here until the user answers.
    var pendingWebPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingGeoCallback by remember { mutableStateOf<GeolocationPermissions.Callback?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.isNotEmpty() && results.values.all { it }

        pendingWebPermissionRequest?.let { request ->
            pendingWebPermissionRequest = null
            try {
                if (allGranted) request.grant(request.resources ?: emptyArray()) else request.deny()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Completing deferred WebView permission failed", e)
            }
        }

        pendingGeoCallback?.let { callback ->
            val origin = pendingGeoOrigin
            pendingGeoCallback = null
            pendingGeoOrigin = null
            callback.invoke(origin, allGranted, false)
        }

        val deniedPermissions = results.filter { !it.value }.keys
        if (deniedPermissions.isNotEmpty()) {
            android.util.Log.d("MainActivity", "User denied some permissions: $deniedPermissions")
        }
    }

    LaunchedEffect(Unit) {
        val list = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Filter out already granted permissions to avoid redundant prompts
        val ungranted = list.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Solid Status Bar with theme matching background so the web content does not overlap
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background)
            )

            // Web view / offline core screen area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // The WebView stays mounted for the whole screen lifetime. It used to be inside
                // `if (!isError)`, so an error tore it out of composition and Retry reloaded an
                // already-detached instance — which did nothing while a brand new WebView was
                // created on top of it. The error panel is now an overlay instead.
                // Full-screen WebView sitting precisely in the safe frame
                AndroidView(
                    factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                
                                // Enable hardware acceleration for high-end rendering (e.g. CSS glassmorphism, backdrop filters)
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                // Light-only: a pure white surface behind the page, so a repaint or a
                                // navigation never flashes the default grey/black WebView canvas.
                                setBackgroundColor(android.graphics.Color.WHITE)

                                // Synchronously compute current connectivity to bypass race conditions on first frame
                                val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                                val activeNetwork = connectivityManager.activeNetwork
                                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                                val actuallyOnline = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

                                // Performance and database caching parameters
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    // Remote-content WebView loading a third-party page: file:// and
                                    // content:// access turn any XSS into a local file read. Keep off.
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    setGeolocationEnabled(true) // Enable Web Geolocation support
                                    loadsImagesAutomatically = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = false // Prevent forced zoom-out overview scaling which breaks mobile responsive styling
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    textZoom = 100 // Enforce default font scale; prevents system accessibility settings from scrambling column layout
                                    mediaPlaybackRequiresUserGesture = false // Crucial: allows WebRTC / camera video stream to play automatically without showing a play button
                                    
                                    // LIGHT-ONLY: the web app has no dark theme, so the WebView must
                                    // never auto-darken it — not from the system dark setting, not from
                                    // algorithmic darkening on Android 13+.
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        try {
                                            isAlgorithmicDarkeningAllowed = false
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "Failed to disable algorithmic darkening", e)
                                        }
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        try {
                                            @Suppress("DEPRECATION")
                                            forceDark = WebSettings.FORCE_DARK_OFF
                                        } catch (e: Exception) {
                                            android.util.Log.e("MainActivity", "Failed to disable force dark", e)
                                        }
                                    }
                                    // Faster first paint: prerender offscreen while the splash is up
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        offscreenPreRaster = true
                                    }

                                    cacheMode = if (actuallyOnline) {
                                        WebSettings.LOAD_DEFAULT
                                    } else {
                                        WebSettings.LOAD_CACHE_ELSE_NETWORK
                                    }

                                    // Bypass Google OAuth "disallowed_useragent" block
                                    // By removing "Version/X.X" and "; wv", we simulate a clean, standard Chrome mobile browser.
                                    val defaultUA = userAgentString
                                    val customizedUA = defaultUA
                                        .replace("; wv", "")
                                        .replace("Version/\\d+\\.\\d+\\s".toRegex(), "")
                                        .replace("Version/\\d+\\.\\d+".toRegex(), "")
                                    userAgentString = if (customizedUA.isNotEmpty() && customizedUA != defaultUA) {
                                        customizedUA
                                    } else {
                                        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    }
                                }

                                // Enable Cookies including third-party cookies (essential for Google Auth / iframe identity tools)
                                val webViewCurrent = this
                                try {
                                    CookieManager.getInstance().apply {
                                        setAcceptCookie(true)
                                        setAcceptThirdPartyCookies(webViewCurrent, true)
                                    }
                                } catch (e: Exception) {
                                    // Guard against rare system webview cookie manager failures
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        viewModel.setLoadProgress(15)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        viewModel.setLoadProgress(100)

                                        // Light-only container: force the page's own color-scheme to light so
                                        // the browser never applies OS dark-mode form controls / scrollbars,
                                        // then tell the splash it can go away — the page is visible now.
                                        view?.evaluateJavascript(LIGHT_MODE_JS, null)
                                        SplashGate.markContentVisible()

                                        // Second call after first layout: some SPAs set their <meta
                                        // name="color-scheme"> after hydration, which would re-enable dark UA styles.
                                        view?.postDelayed({ view.evaluateJavascript(LIGHT_MODE_JS, null) }, 350L)
                                    }

                                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                                        super.onPageCommitVisible(view, url)
                                        // Fires when the first frame of the new document is on screen —
                                        // the exact moment the splash can be dismissed without a blank flash.
                                        SplashGate.markContentVisible()
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            viewModel.setWebViewError(true)
                                            // Show the retry panel instead of leaving the splash up forever.
                                            SplashGate.markContentVisible()
                                        }
                                    }

                                    @Suppress("OVERRIDE_DEPRECATION")
                                    override fun onReceivedError(
                                        view: WebView?,
                                        errorCode: Int,
                                        description: String?,
                                        failingUrl: String?
                                    ) {
                                        if (failingUrl != null && failingUrl.trimEnd('/').equals(viewModel.targetUrl.trimEnd('/'), ignoreCase = true)) {
                                            viewModel.setWebViewError(true)
                                            SplashGate.markContentVisible()
                                        }
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        viewModel.setLoadProgress(newProgress)
                                    }

                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        // Two rules here:
                                        //  1. Only OUR OWN origin may use the camera/mic. Previously every
                                        //     origin was granted unconditionally, so any third-party page
                                        //     (OAuth redirect, shared link) got instant access with no prompt.
                                        //  2. Granting the WebView request does NOT grant the Android runtime
                                        //     permission — the OS dialog has to run first, otherwise the page
                                        //     silently fails. So ask for it and finish the grant in the callback.
                                        val req = request ?: return
                                        val origin = req.origin?.toString().orEmpty()

                                        if (!viewModel.isTrustedOrigin(origin)) {
                                            android.util.Log.w("MainActivity", "Denied camera/mic for untrusted origin: $origin")
                                            try {
                                                req.deny()
                                            } catch (e: Exception) {
                                                android.util.Log.e("MainActivity", "WebRTC deny error", e)
                                            }
                                            return
                                        }

                                        val resources = req.resources?.toList().orEmpty()
                                        val needed = buildList {
                                            if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                                                add(Manifest.permission.CAMERA)
                                            }
                                            if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                                                add(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                        val missing = needed.filter {
                                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                        }

                                        if (missing.isEmpty()) {
                                            try {
                                                req.grant(req.resources ?: emptyArray())
                                            } catch (e: Exception) {
                                                android.util.Log.e("MainActivity", "WebRTC grant permission error", e)
                                            }
                                        } else {
                                            // Drop any stale request so it cannot be granted by mistake
                                            pendingWebPermissionRequest?.let { runCatching { it.deny() } }
                                            pendingWebPermissionRequest = req
                                            permissionsLauncher.launch(missing.toTypedArray())
                                        }
                                    }

                                    override fun onGeolocationPermissionsShowPrompt(
                                        origin: String?,
                                        callback: GeolocationPermissions.Callback?
                                    ) {
                                        // Same rule as camera/mic: never auto-approve an arbitrary origin,
                                        // and make sure the OS location permission is actually held before
                                        // telling the page that geolocation is available.
                                        val cb = callback ?: return
                                        if (!viewModel.isTrustedOrigin(origin.orEmpty())) {
                                            cb.invoke(origin, false, false)
                                            return
                                        }

                                        val missing = listOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        ).filter {
                                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                                        }

                                        if (missing.isEmpty()) {
                                            cb.invoke(origin, true, false)
                                        } else {
                                            pendingGeoCallback = cb
                                            pendingGeoOrigin = origin
                                            permissionsLauncher.launch(missing.toTypedArray())
                                        }
                                    }

                                     override fun onShowFileChooser(
                                        webView: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri>>?,
                                        fileChooserParams: FileChooserParams?
                                    ): Boolean {
                                        customFilePathCallback?.onReceiveValue(null)
                                        customFilePathCallback = filePathCallback
                                        
                                        try {
                                            val intent = fileChooserParams?.createIntent()
                                            if (intent != null) {
                                                fileChooserLauncher.launch(intent)
                                            } else {
                                                filePathCallback?.onReceiveValue(null)
                                                customFilePathCallback = null
                                                return false
                                            }
                                        } catch (e: Exception) {
                                            filePathCallback?.onReceiveValue(null)
                                            customFilePathCallback = null
                                            return false
                                        }
                                        return true
                                    }
                                }

                                // Inject JS push notification / token channel to match website capabilities.
                                // The bridge name is configurable (app.jsBridgeName) so the web app and
                                // the container cannot drift apart.
                                addJavascriptInterface(
                                    ParentJavascriptInterface(ctx, viewModel),
                                    BuildConfig.JS_BRIDGE_NAME
                                )

                                // Hidden QA gesture: two horizontal swipes in the strip just below
                                // the status bar post one reminder, so the notification can be
                                // checked without waiting for Monday. Never consumes the touch,
                                // so the page keeps scrolling normally.
                                com.pw.parent.utils.ReminderSwipeDetector.attach(this) {
                                    android.util.Log.d("MainActivity", "Test reminder fired by swipe")
                                }

                                loadUrl(viewModel.targetUrl)

                                // Mirror the live instance into Compose state for BackHandler,
                                // without writing state from inside the factory lambda.
                                post { onWebViewReady(this) }
                            }
                        },
                        update = { webView ->
                            webView.settings.cacheMode = if (isOnline) {
                                WebSettings.LOAD_DEFAULT
                            } else {
                                WebSettings.LOAD_CACHE_ELSE_NETWORK
                            }
                            
                             // Light-only: keep auto-darkening switched off on every recomposition.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                try {
                                    webView.settings.isAlgorithmicDarkeningAllowed = false
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                try {
                                    @Suppress("DEPRECATION")
                                    webView.settings.forceDark = WebSettings.FORCE_DARK_OFF
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                try {
                                    val swController = ServiceWorkerController.getInstance()
                                    swController.serviceWorkerWebSettings.cacheMode = if (isOnline) {
                                        WebSettings.LOAD_DEFAULT
                                    } else {
                                        WebSettings.LOAD_CACHE_ELSE_NETWORK
                                    }
                                } catch (e: Exception) {
                                    // Ignore failures in updating process-global SW settings
                                }
                            }
                            if (isOnline && isError) {
                                viewModel.setWebViewError(false)
                                webView.loadUrl(viewModel.targetUrl)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("parent_webview_panel")
                    )

                    // Tear the WebView down when this screen really goes away.
                    // Nothing destroyed it before, so the WebView (and the Activity behind it)
                    // was retained for the whole process lifetime.
                    DisposableEffect(Unit) {
                        onDispose {
                            webViewInstance?.let { stale ->
                                stale.stopLoading()
                                stale.webChromeClient = null
                                stale.webViewClient = WebViewClient()
                                stale.removeJavascriptInterface(BuildConfig.JS_BRIDGE_NAME)
                                stale.destroy()
                            }
                            webViewInstance = null
                        }
                    }

                    // Loading feedback WITHOUT covering the page.
                    // The old 85%-opaque full-screen scrim (plus a centered spinner) made the app
                    // look like a blank white screen while the site loaded. A 2dp progress bar at
                    // the top keeps the previous/current page fully visible instead, and it stays
                    // out of the way once the document is committed (progress == 100).
                    androidx.compose.animation.AnimatedVisibility(
                        visible = progress < 100 && !isError,
                        enter = fadeIn(animationSpec = tween(120)),
                        exit = fadeOut(animationSpec = tween(300)),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0, 100) / 100f },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .testTag("page_loader_bar")
                        )
                    }

                // Error panel overlays the still-mounted WebView, so Retry can talk to a
                // live instance and the page state survives a temporary network drop.
                if (isError) {
                    // Elegant offline recovery screen - minimalist theme matching
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Offline Mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Text(
                                text = "Connection Offline",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Make sure your Wi-Fi or cellular network is active and try reloading.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    viewModel.setWebViewError(false)
                                    webViewInstance?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("offline_retry_button")
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry Connection")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Solid Navigation Bar spacer so the bottom bar isn't transparently cutting off WebView
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.safeDrawing)
                    .background(MaterialTheme.colorScheme.background)
            )
        }
    }
