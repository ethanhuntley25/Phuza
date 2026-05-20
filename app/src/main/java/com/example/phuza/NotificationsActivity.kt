package com.example.phuza

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phuza.adapters.NotificationsAdapter
import com.example.phuza.data.AppNotification
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NotificationsActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: NotificationsAdapter
    private var reg: com.google.firebase.firestore.ListenerRegistration? = null

    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }

        markAllRead()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Notifications"

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvNotifications)
        val empty = findViewById<View>(R.id.empty)

        adapter = NotificationsAdapter { n ->
            markAsRead(n)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Live query: most recent first
        val uid = auth.currentUser?.uid ?: return
        val col = db.collection("Notifications").document(uid).collection("items")

        reg?.remove()
        reg = col.orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if ((err != null) || (snap == null)) return@addSnapshotListener
                val items = snap.documents.map { AppNotification.from(it) }
                adapter.submitList(items)
                if (items.isEmpty()) {
                    empty.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                } else {
                    empty.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                }

            }
    }

    override fun onDestroy() {
        reg?.remove()
        reg = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Mark all read")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish(); return true
        }
        if (item.itemId == 1) {
            markAllRead()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun markAsRead(n: AppNotification) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("Notifications").document(uid)
            .collection("items").document(n.id)
            .update("read", true)
    }

    private fun markAllRead() {
        val uid = auth.currentUser?.uid ?: return
        val col = db.collection("Notifications").document(uid).collection("items")
        uiScope.launch(Dispatchers.IO) {
            val snap = col.whereEqualTo("read", false).get().awaitOrNull() ?: return@launch
            val batch = db.batch()
            snap.documents.forEach { batch.update(it.reference, "read", true) }
            batch.commit()
        }
    }
}

// tiny await helper (no coroutines-ktx import requirement change)
private fun <T> Task<T>.awaitOrNull(): T? =
    try { com.google.android.gms.tasks.Tasks.await(this); this.result } catch (_: Exception) { null }
