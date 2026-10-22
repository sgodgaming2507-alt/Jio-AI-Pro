package org.sgod.jioai

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var container: FrameLayout
    private lateinit var bubbleView: TextView
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var webView: WebView

    private var isPanelVisible = true
    private var isServiceDestroyed = false

    // Drag State
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val mainHandler = Handler(Looper.getMainLooper())

    // Dimensions (Must match HTML CSS)
    private val PANEL_WIDTH = 380
    private val PANEL_HEIGHT = 420
    private val DRAG_HANDLE_HEIGHT = 48

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        isServiceDestroyed = false
        try {
            // Check Overlay Permission (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w("FloatingService", "Overlay Permission Missing")
                stopSelf()
                return
            }

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // 1. CONTAINER SETUP
            container = FrameLayout(this).apply {
                // Solid background to prevent see-through artifacts
                setBackgroundColor(Color.parseColor("#030711"))
                // Optional: Elevation for shadow (API 21+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    elevation = 16f
                }
            }

            // 2. WEBVIEW SETUP
            webView = WebView(this).apply {
                // Enable Hardware Acceleration for smooth rendering
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                setBackgroundColor(Color.parseColor("#030711"))
                
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                // Critical: Prevent WebView from eating touch events meant for drag handle
                // We handle this via the handle, but ensure WebView scrolls correctly inside
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = true

                addJavascriptInterface(WebAppInterface(), "Android")
                
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.e("FloatingService", "WebView Error: ${error?.description}")
                    }
                }

                // LayoutParams for WebView: Fill the container
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                loadUrl("file:///android_asset/index.html")
            }
            container.addView(webView)

            // 3. DRAG HANDLE (Top Bar)
            // This is the ONLY area that initiates drag
            val dragHandle = View(this).apply {
                // Subtle color to indicate it's a handle
                setBackgroundColor(Color.parseColor("#1a2938"))
                // Height 48dp is comfortable for touch
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    DRAG_HANDLE_HEIGHT
                )
                // Bring to front so it's above WebView
                bringToFront()
            }
            container.addView(dragHandle)

            // 4. BUBBLE VIEW
            bubbleView = TextView(this).apply {
                text = "⚡"
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#cc000000"))
                setPadding(18, 18, 18, 18)
                // Rounded corners for bubble
                val bg = android.graphics.drawable.GradientDrawable()
                bg.cornerRadius = 30f
                bg.setColor(Color.parseColor("#cc000000"))
                background = bg
            }

            // 5. PARAMS SETUP
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // Panel Params: Fixed Size
            panelParams = WindowManager.LayoutParams(
                PANEL_WIDTH,
                PANEL_HEIGHT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 76
                y = 98
            }

            // Bubble Params: Wrap Content
            bubbleParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 76
                y = 98
            }

            // 6. TOUCH LOGIC (Drag Handle Only)
            dragHandle.setOnTouchListener { _, event ->
                if (isServiceDestroyed) return@setOnTouchListener false

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = panelParams.x
                        initialY = panelParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false // Allow click-through if no move
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        
                        val newX = initialX + dx
                        val newY = initialY + dy

                        // Clamp to Screen
                        val metrics = resources.displayMetrics
                        val clampedX = newX.coerceIn(0, metrics.widthPixels - PANEL_WIDTH)
                        val clampedY = newY.coerceIn(0, metrics.heightPixels - PANEL_HEIGHT)

                        panelParams.x = clampedX
                        panelParams.y = clampedY

                        try {
                            windowManager.updateViewLayout(container, panelParams)
                        } catch (e: Exception) {
                            Log.w("FloatingService", "Drag Update Failed", e)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        false
                    }
                    else -> false
                }
            }

            // 7. BUBBLE CLICK (Expand)
            bubbleView.setOnClickListener {
                mainHandler.post {
                    if (!isServiceDestroyed) expandPanel()
                }
            }

            // 8. ADD VIEWS
            windowManager.addView(container, panelParams)
            isPanelVisible = true

        } catch (e: Exception) {
            Log.e("FloatingService", "Service Creation Failed", e)
            stopSelf()
        }
    }

    private fun expandPanel() {
        if (isServiceDestroyed) return
        try {
            if (!isPanelVisible) {
                // Sync position: Bubble goes where panel was
                bubbleParams.x = panelParams.x
                bubbleParams.y = panelParams.y
                
                windowManager.removeView(bubbleView)
                windowManager.addView(container, panelParams)
                isPanelVisible = true
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Expand Failed", e)
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun minimizePanel() {
            mainHandler.post {
                if (isServiceDestroyed) return
                try {
                    if (isPanelVisible) {
                        // Sync position: Bubble takes panel's spot
                        bubbleParams.x = panelParams.x
                        bubbleParams.y = panelParams.y
                        
                        windowManager.removeView(container)
                        windowManager.addView(bubbleView, bubbleParams)
                        isPanelVisible = false
                    }
                } catch (e: Exception) {
                    Log.e("FloatingService", "Minimize Failed", e)
                }
            }
        }

        @JavascriptInterface
        fun closePanel() {
            mainHandler.post {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceDestroyed = true

        // Cleanup WebView
        try {
            if (::webView.isInitialized) {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        } catch (e: Exception) {}

        // Remove Views
        try {
            if (::windowManager.isInitialized) {
                if (::container.isInitialized) {
                    try { windowManager.removeView(container) } catch (_: Exception) {}
                }
                if (::bubbleView.isInitialized) {
                    try { windowManager.removeView(bubbleView) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {}
    }
}
