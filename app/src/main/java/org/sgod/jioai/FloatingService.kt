package org.sgod.jioai

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Floating overlay disabled.
 * MainActivity is responsible for displaying the full-screen app.
 */
class FloatingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        // Do not create a floating window.
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
