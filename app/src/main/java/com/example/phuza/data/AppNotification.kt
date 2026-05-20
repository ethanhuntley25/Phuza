package com.example.phuza.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class AppNotification(
    val id: String,
    val type: String,
    val fromUid: String,
    val fromName: String?,
    val fromUsername: String?,
    val message: String,
    val createdAt: Timestamp?,
    val read: Boolean
) {
    companion object {
        fun from(doc: DocumentSnapshot): AppNotification =
            AppNotification(
                id = doc.id,
                type = doc.getString("type") ?: "notification",
                fromUid = doc.getString("fromUid") ?: "",
                fromName = doc.getString("fromName"),
                fromUsername = doc.getString("fromUsername"),
                message = doc.getString("message") ?: "",
                createdAt = doc.getTimestamp("createdAt"),
                read = doc.getBoolean("read") == true
            )
    }
}
