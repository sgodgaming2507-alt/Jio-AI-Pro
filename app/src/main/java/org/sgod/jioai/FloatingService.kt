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

    companion object {
        private const val TAG = "S_GodFloating"
        private const val PANEL_WIDTH = 380
        private const val PANEL_HEIGHT = 420
    }

    private lateinit var windowManager: WindowManager
    private lateinit var container: FrameLayout
    private lateinit var bubbleView: TextView
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var webView: WebView

    private var isPanelVisible = true
    private var isServiceDestroyed = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        isServiceDestroyed = false

        try {
            // 1. Overlay Permission Check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.e(TAG, "Overlay Permission Missing")
                stopSelf()
                return
            }

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            // 2. Container
            container = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#030711"))
                elevation = 16f
            }

            // 3. WebView
            webView = WebView(this).apply {
                setBackgroundColor(Color.parseColor("#030711"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                // Safe JS Interface
                addJavascriptInterface(WebAppInterface(), "Android")

                // Error Handling
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "WebView Loaded: $url")
                    }
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.e(TAG, "WebView Error: ${error?.description}")
                    }
                }

                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            container.addView(webView)

            // 4. Drag Handle (Top 48px)
            val dragHandle = View(this).apply {
                setBackgroundColor(Color.parseColor("#1a2938"))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    48
                )
                bringToFront()
            }
            container.addView(dragHandle)

            // 5. Bubble
            bubbleView = TextView(this).apply {
                text = "⚡"
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#cc000000"))
                setPadding(18, 18, 18, 18)
                val bg = android.graphics.drawable.GradientDrawable()
                bg.cornerRadius = 30f
                bg.setColor(Color.parseColor("#cc000000"))
                background = bg
            }

            // 6. Params
            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

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

            // 7. Drag Logic
            dragHandle.setOnTouchListener { _, event ->
                if (isServiceDestroyed) {
                    return@setOnTouchListener false
                }

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = panelParams.x
                        initialY = panelParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        val newX = initialX + dx
                        val newY = initialY + dy

                        val metrics = resources.displayMetrics
                        val clampedX = newX.coerceIn(0, metrics.widthPixels - PANEL_WIDTH)
                        val clampedY = newY.coerceIn(0, metrics.heightPixels - PANEL_HEIGHT)

                        panelParams.x = clampedX
                        panelParams.y = clampedY

                        try {
                            windowManager.updateViewLayout(container, panelParams)
                        } catch (e: Exception) {
                            Log.w(TAG, "Drag Update Failed", e)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        false
                    }
                    else -> {
                        false
                    }
                }
            }

            // 8. Bubble Click
            bubbleView.setOnClickListener {
                mainHandler.post {
                    if (!isServiceDestroyed) {
                        expandPanel()
                    }
                }
            }

            // 9. Add Views
            Log.d(TAG, "Adding Views to WindowManager")
            windowManager.addView(container, panelParams)
            isPanelVisible = true

            // 10. Load HTML
            Log.d(TAG, "Loading HTML")
            webView.loadUrl("file:///android_asset/index.html")
            Log.d(TAG, "Service Initialized Successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Service Initialization Failed", e)
            stopSelf()
        }
    }

    private fun expandPanel() {
        if (isServiceDestroyed) {
            return
        }
        try {
            if (!isPanelVisible) {
                bubbleParams.x = panelParams.x
                bubbleParams.y = panelParams.y
                windowManager.removeView(bubbleView)
                windowManager.addView(container, panelParams)
                isPanelVisible = true
                Log.d(TAG, "Panel Expanded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Expand Failed", e)
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun minimizePanel() {
            mainHandler.post {
                if (isServiceDestroyed) {
                    // Do nothing
                } else {
                    try {
                        if (isPanelVisible) {
                            bubbleParams.x = panelParams.x
                            bubbleParams.y = panelParams.y
                            windowManager.removeView(container)
                            windowManager.addView(bubbleView, bubbleParams)
                            isPanelVisible = false
                            Log.d(TAG, "Panel Minimized")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Minimize Failed", e)
                    }
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
        Log.d(TAG, "Service Destroying")

        try {
            if (::webView.isInitialized) {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebView Cleanup Failed", e)
        }

        try {
            if (::windowManager.isInitialized) {
                if (::container.isInitialized) {
                    try { windowManager.removeView(container) } catch (_: Exception) {}
                }
                if (::bubbleView.isInitialized) {
                    try { windowManager.removeView(bubbleView) } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Window Cleanup Failed", e)
        }
    }
}
