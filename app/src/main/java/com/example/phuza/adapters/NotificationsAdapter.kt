package com.example.phuza.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.AppNotification
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import kotlin.math.abs

class NotificationsAdapter(
    private val onClick: (AppNotification) -> Unit = {}
) : ListAdapter<AppNotification, NotificationsAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(a: AppNotification, b: AppNotification) = a.id == b.id
        override fun areContentsTheSame(a: AppNotification, b: AppNotification) = a == b
    }

    // Realtime DB:
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val avatarCache = HashMap<String, String?>()

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val img: ImageView = v.findViewById(R.id.imgAvatar)
        private val title: TextView = v.findViewById(R.id.txtTitle)
        private val sub: TextView = v.findViewById(R.id.txtSub)
        private val time: TextView = v.findViewById(R.id.txtTime)

        fun bind(n: AppNotification) {
            // Placeholder while loading
            img.setImageResource(R.drawable.avatar_no_avatar)

            loadAvatar(n.fromUid, img)

            val name = n.fromName ?: "Someone"
            val username = n.fromUsername?.takeIf { it.isNotBlank() } ?: ""
            val who = if (username.isNotEmpty()) "$name" else name

            val verb = when (n.type) {
                "follow_request" -> "added you!"
                "follow_accept"  -> "accepted your follow request"
                "checkin"        -> n.message
                else             -> n.message.ifBlank { "sent a notification" }
            }

            title.text = when (n.type) {
                "checkin" -> verb
                else      -> "$who $verb"
            }

            sub.text = "@${username}".takeIf { username.isNotEmpty() } ?: n.fromUid

            val millis = n.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            val now = System.currentTimeMillis()
            val diff = abs(now - millis)

            time.text = if (diff < DateUtils.MINUTE_IN_MILLIS) {
                "now"
            } else {
                DateUtils.getRelativeTimeSpanString(
                    millis,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            }

            itemView.alpha = if (n.read) 0.65f else 1f

            itemView.setOnClickListener { onClick(n) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, vt: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_notification, p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.itemView.findViewById<ImageView>(R.id.imgAvatar)
            .setImageResource(R.drawable.avatar_no_avatar)
        holder.itemView.findViewById<ImageView>(R.id.imgAvatar).tag = null
    }

    private fun loadAvatar(uid: String, into: ImageView) {
        into.tag = uid

        val cached = avatarCache[uid]
        if (cached != null) {
            applyAvatarName(into, cached)
            return
        }

        usersRef.child(uid).child("avatar")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val avatarName = snapshot.getValue(String::class.java) // e.g. "avatar_5" or maybe null
                    avatarCache[uid] = avatarName
                    if (into.tag == uid) applyAvatarName(into, avatarName)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Ignore to keep placeholder
                }
            })
    }

    private fun applyAvatarName(view: ImageView, avatarName: String?) {
        if (!avatarName.isNullOrBlank() && !avatarName.startsWith("http", ignoreCase = true)) {
            val resId = view.resources.getIdentifier(
                avatarName, "drawable", view.context.packageName
            )
            if (resId != 0) {
                view.setImageResource(resId)
                return
            }
        }

        view.setImageResource(R.drawable.avatar_no_avatar)
    }
}
