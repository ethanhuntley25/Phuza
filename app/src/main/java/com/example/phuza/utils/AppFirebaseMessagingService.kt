package com.example.phuza.utils

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Log.w("AppFMS", "onNewToken: No user logged in; skipping token save")
            return
        }

        try {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("fcmTokens")
                .document(token)
                .set(mapOf("createdAt" to System.currentTimeMillis()))
                .addOnSuccessListener {
                    Log.d("AppFMS", "Token saved for user=$uid")
                }
                .addOnFailureListener { e ->
                    Log.e("AppFMS", "Failed to save token", e)
                }
        } catch (se: SecurityException) {
            Log.e("AppFMS", "SecurityException while saving token", se)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: "Notification"
        val body = message.notification?.body ?: message.data["body"] ?: ""

        // Only try showing if permission exists
        val canPost = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!canPost) {
            Log.w("AppFMS", "POST_NOTIFICATIONS not granted; skipping foreground notification")
            return
        }

        try {
            NotificationUtils.show(this, title, body)
        } catch (se: SecurityException) {
            Log.e("AppFMS", "SecurityException while showing notification", se)
        }
    }
}
