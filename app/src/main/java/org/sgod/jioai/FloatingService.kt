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
    private var isMinimized = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(WebAppInterface(), "Android")
            layoutParams = FrameLayout.LayoutParams(650, 650)
            loadUrl("file:///android_asset/index.html")
        }
        container.addView(webView)

        bubbleView = TextView(this).apply {
            text = "⚡"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#cc000000"))
            setPadding(20, 20, 20, 20)
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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
                    windowManager.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var bX = 0
            private var bY = 0
            private var bTouchX = 0f
            private var bTouchY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        bX = params.x
                        bY = params.y
                        bTouchX = event.rawX
                        bTouchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - bTouchX).toInt()
                        val dy = (event.rawY - bTouchY).toInt()
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            moved = true
                            params.x = bX + dx
                            params.y = bY + dy
                            windowManager.updateViewLayout(bubbleView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) expandPanel()
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(container, params)
        windowManager.addView(bubbleView, params)
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun minimizePanel() {
            android.os.Handler(mainLooper).post {
                container.visibility = View.GONE
                bubbleView.visibility = View.VISIBLE
                isMinimized = true
            }
        }

        @JavascriptInterface
        fun closeApp() {
            android.os.Handler(mainLooper).post {
                stopSelf()
            }
        }
    }

    private fun expandPanel() {
        bubbleView.visibility = View.GONE
        container.visibility = View.VISIBLE
        isMinimized = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::container.isInitialized) {
            try { windowManager.removeView(container) } catch (e: Exception) {}
        }
        if (::bubbleView.isInitialized) {
            try { windowManager.removeView(bubbleView) } catch (e: Exception) {}
        }
    }
}
