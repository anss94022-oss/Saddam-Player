package com.local.videoplayer.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FloatingPlayerService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // يمكن إضافة منطق النافذة العائمة هنا (Floating Window / PiP)
        return START_STICKY
    }
}
