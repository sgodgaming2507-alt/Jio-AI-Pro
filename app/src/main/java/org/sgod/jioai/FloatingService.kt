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
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.JavascriptInterface
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
    
    private var isServiceDestroyed = false
    private var isPanelVisible = true
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                stopSelf()
                return
            }

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            container = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#030711"))
            }

            webView = WebView(this).apply {
                setBackgroundColor(Color.parseColor("#030711"))
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                addJavascriptInterface(WebAppInterface(), "Android")
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {}
                    
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.e("FloatingService", "WebView Error: ${error?.description}")
                        view?.loadData("<html><body style='background:#030711;color:white;text-align:center;margin-top:50px'><h2>Failed to Load UI</h2><p>${error?.description}</p></body></html>", "text/html", null)
                    }
                }

                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                loadUrl("file:///android_asset/index.html")
            }
            container.addView(webView)

            bubbleView = TextView(this).apply {
                text = "⚡"
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#cc000000"))
                setPadding(18, 18, 18, 18)
            }

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // Size ko wapas bada kar diya hai: Width 414px, Height 587px
            panelParams = WindowManager.LayoutParams(
                414,
                587,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 200
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            container.setOnTouchListener { _, event ->
                if (isServiceDestroyed) return@setOnTouchListener false
                
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
                        val clampedX = newX.coerceIn(0, metrics.widthPixels - panelParams.width)
                        val clampedY = newY.coerceIn(0, metrics.heightPixels - panelParams.height)
                        
                        panelParams.x = clampedX
                        panelParams.y = clampedY
                        
                        try {
                            windowManager.updateViewLayout(container, panelParams)
                        } catch (e: Exception) {
                            Log.w("FloatingService", "Drag update failed", e)
                        }
                        true
                    }
                    else -> false
                }
            }

            bubbleView.setOnTouchListener { _, event ->
                if (isServiceDestroyed) return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        bubbleParams.x = initialX + dx
                        bubbleParams.y = initialY + dy
                        try {
                            windowManager.updateViewLayout(bubbleView, bubbleParams)
                        } catch (e: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(event.rawX - initialTouchX) < 10 && Math.abs(event.rawY - initialTouchY) < 10) {
                            mainHandler.post {
                                try {
                                    if (!isPanelVisible) {
                                        panelParams.x = bubbleParams.x
                                        panelParams.y = bubbleParams.y
                                        windowManager.removeView(bubbleView)
                                        windowManager.addView(container, panelParams)
                                        isPanelVisible = true
                                    }
                                } catch (e: Exception) {
                                    Log.e("FloatingService", "Restore failed", e)
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(container, panelParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun minimizePanel() {
            if (isServiceDestroyed) return
            
            mainHandler.post {
                if (isServiceDestroyed) return@post
                try {
                    if (isPanelVisible) {
                        bubbleParams.x = panelParams.x
                        bubbleParams.y = panelParams.y
                        windowManager.removeView(container)
                        windowManager.addView(bubbleView, bubbleParams)
                        isPanelVisible = false
                    }
                } catch (e: Exception) {
                    Log.e("FloatingService", "Minimize failed", e)
                }
            }
        }
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        super.onDestroy()
        try {
            if (::windowManager.isInitialized) {
                if (isPanelVisible && ::container.isInitialized) {
                    windowManager.removeView(container)
                } else if (!isPanelVisible && ::bubbleView.isInitialized) {
                    windowManager.removeView(bubbleView)
                }
            }
        } catch (e: Exception) {}
    }
}
