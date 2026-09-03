package org.sgod.jioai

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var container: FrameLayout
    private lateinit var bubbleView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var webView: WebView

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            container = FrameLayout(this)

            webView = WebView(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                addJavascriptInterface(WebAppInterface(), "Android")
                layoutParams = FrameLayout.LayoutParams(340, 330)
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
                visibility = View.GONE
            }

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 80
                y = 150
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f

            container.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (e: Exception) {}
                        true
                    }
                    else -> false
                }
            }

            windowManager.addView(container, params)
            windowManager.addView(bubbleView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun minimizePanel() {
            android.os.Handler(mainLooper).post {
                try {
                    container.visibility = View.GONE
                    bubbleView.visibility = View.VISIBLE
                } catch (e: Exception) {}
            }
        }

        @JavascriptInterface
        fun closeApp() {
            android.os.Handler(mainLooper).post {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::windowManager.isInitialized) {
                if (::container.isInitialized) windowManager.removeView(container)
                if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
            }
        } catch (e: Exception) {}
    }
}
