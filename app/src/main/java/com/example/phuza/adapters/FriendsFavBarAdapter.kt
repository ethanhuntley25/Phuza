package com.example.phuza.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.FriendsFavBar
import com.example.phuza.utils.ImageUtils

import androidx.core.graphics.createBitmap
import kotlin.math.min

class FriendsFavBarAdapter(
    private val bars: List<FriendsFavBar>,
) : RecyclerView.Adapter<FriendsFavBarAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.name)
        private val avatar1: ImageView = view.findViewById(R.id.avatar1)
        private val avatar2: ImageView = view.findViewById(R.id.avatar2)
        private val avatar3: ImageView = view.findViewById(R.id.avatar3)

        private val avatarMore: TextView = view.findViewById(R.id.avatar_more)

        fun bind(item: FriendsFavBar) {
            name.text = item.barName

            val targets = listOf(avatar1, avatar2, avatar3)
            val avatarsToShow = item.friendAvatars.take(3)

            // Show avatars
            targets.forEachIndexed { i, img ->
                if (i < avatarsToShow.size) {
                    setAvatar(img, avatarsToShow[i])
                    img.visibility = View.VISIBLE
                } else {
                    img.visibility = View.GONE
                }
            }

            val extra = item.friendAvatars.size - 2
            if (extra > 0) {
                avatarMore.visibility = View.VISIBLE
                avatarMore.text = itemView.context.getString(R.string.plus_format, extra)
            } else {
                avatarMore.visibility = View.GONE
            }
        }
        private fun setAvatar(view: ImageView, value: String) {
            val bmp: Bitmap? = if (looksLikeBase64Image(value)) {
                ImageUtils.decodeBase64ToBitmap(value)
            } else {
                val resId = view.context.resources.getIdentifier(
                    value, "drawable", view.context.packageName
                )
                if (resId != 0) BitmapFactory.decodeResource(view.context.resources, resId)
                else null
            }

            if (bmp != null) {
                view.setImageBitmap(getRoundedBitmap(bmp))
            } else {
                view.setImageResource(R.drawable.avatar_no_avatar)
            }
        }

        /** Crop a square bitmap into a circle */
        private fun getRoundedBitmap(src: Bitmap): Bitmap {
            val size = min(src.width, src.height)
            val output = createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, size, size)
            val rectF = RectF(rect)
            canvas.drawOval(rectF, paint) // draw a circle/oval mask
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

            val left = (src.width - size) / 2
            val top = (src.height - size) / 2
            canvas.drawBitmap(src, -left.toFloat(), -top.toFloat(), paint)
            return output
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friends_bars, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(bars[position])
    }

    override fun getItemCount() = bars.size

    private fun looksLikeBase64Image(value: String): Boolean {
        if (value.startsWith("data:image")) return true
        if (value.length < 100) return false
        return value.all { it.isLetterOrDigit() || (it in "+/=\n\r") }
    }
}

