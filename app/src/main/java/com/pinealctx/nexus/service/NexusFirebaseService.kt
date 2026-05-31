package com.pinealctx.nexus.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

// Firebase Messaging Service placeholder.
// Requires google-services.json to compile with actual Firebase SDK.
// For now, this is a plain Service stub.
class NexusFirebaseService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }
}
