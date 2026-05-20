package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.Review
import com.example.phuza.utils.ImageUtils

class FriendReviewAdapter(
    private val items: MutableList<Review> = mutableListOf(),
    private val authorInfo: MutableMap<String, Pair<String?, String?>> = mutableMapOf()
) : RecyclerView.Adapter<FriendReviewAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvReviewer: TextView = v.findViewById(R.id.tvReviewer)
        val ivProfile: ImageView = v.findViewById(R.id.ivProfile)
        val ivPlacePhoto: ImageView = v.findViewById(R.id.ivPlacePhoto)
        val tvBlurb: TextView = v.findViewById(R.id.tvBlurb)
        val ivHeart: ImageView = v.findViewById(R.id.ivHeart)
        val stars: List<ImageView> = listOf(
            v.findViewById(R.id.rStar1),
            v.findViewById(R.id.rStar2),
            v.findViewById(R.id.rStar3),
            v.findViewById(R.id.rStar4),
            v.findViewById(R.id.rStar5),
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]

        val (nameFromMap, avatarFromMap) = authorInfo[r.authorId.orEmpty()] ?: (null to null)
        val name = r.authorName ?: nameFromMap ?: "Someone"
        val avatar = r.authorAvatar ?: avatarFromMap

        h.tvReviewer.text = "$name’s review:"

        // Avatar
        if (avatar.isNullOrBlank()) {
            h.ivProfile.setImageResource(R.drawable.avatar_no_avatar)
        } else {
            val ctx = h.itemView.context
            val resId = ctx.resources.getIdentifier(avatar, "drawable", ctx.packageName)
            if (resId != 0) h.ivProfile.setImageResource(resId)
            else ImageUtils.decodeBase64ToBitmap(avatar)?.let { h.ivProfile.setImageBitmap(it) }
                ?: h.ivProfile.setImageResource(R.drawable.avatar_no_avatar)
        }

        // Place photo
        val placeBmp = r.imageBase64?.let(ImageUtils::decodeBase64ToBitmap)
        if (placeBmp != null) {
            h.ivPlacePhoto.visibility = View.VISIBLE
            h.ivPlacePhoto.setImageBitmap(placeBmp)
        } else {
            h.ivPlacePhoto.visibility = View.GONE
        }

        // Blurb
        if (!r.description.isNullOrBlank()) {
            h.tvBlurb.visibility = View.VISIBLE
            h.tvBlurb.text = r.description
            h.tvBlurb.setTextColor(ContextCompat.getColor(h.tvBlurb.context, R.color.milk))
        } else {
            h.tvBlurb.visibility = View.GONE
        }



        // Stars
        h.stars.forEachIndexed { i, iv ->
            val tint = if (i < r.rating) R.color.yellow else R.color.star_inactive
            iv.imageTintList = ContextCompat.getColorStateList(iv.context, tint)
        }

        // Heart icon
        val heartIcon = if (r.liked) R.drawable.ic_heart else R.drawable.ic_heart_outline
        h.ivHeart.setImageResource(heartIcon)
        h.ivHeart.imageTintList = ContextCompat.getColorStateList(h.ivHeart.context, R.color.lavender)
    }

    fun submit(newItems: List<Review>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun putAuthor(uid: String, name: String?, avatar: String?) {
        authorInfo[uid] = name to avatar
        notifyDataSetChanged()
    }
}
