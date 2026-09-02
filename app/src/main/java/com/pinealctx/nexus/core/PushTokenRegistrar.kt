package com.pinealctx.nexus.core

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    fun registerCurrentTokenIfAvailable() {
        if (!secureStorage.hasTokens()) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.d(TAG, "Firebase is not configured; skipping FCM registration")
            return
        }
        FirebaseMessaging.getInstance().register()
            .addOnFailureListener { error -> Log.w(TAG, "FCM registration failed", error) }
    }

    private companion object {
        const val TAG = "NexusPush"
    }
}
