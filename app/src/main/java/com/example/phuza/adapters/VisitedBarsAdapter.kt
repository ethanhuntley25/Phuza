package com.example.phuza.adapters

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.Review

class VisitedBarsAdapter : ListAdapter<Review, VisitedBarsAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<Review>(){
        override fun areItemsTheSame(oldItem: Review, newItem: Review) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view){
        private val tvBarName: TextView = view.findViewById(R.id.tvBarName)
        private val tvDate: TextView = view.findViewById(R.id.tvDate)
        private val llStarsContainer: LinearLayout = view.findViewById(R.id.llStarsContainer)

        fun bind(item: Review){
            tvBarName.text = item.place

            val date = DateFormat.format("dd MMMM yyyy", item.timestamp).toString()
            tvDate.text = date

            llStarsContainer.removeAllViews()
            val starColor = ContextCompat.getColorStateList(itemView.context, R.color.yellow)
            val inactiveColor = ContextCompat.getColorStateList(itemView.context, R.color.star_inactive)

            for (i in 1..5){
                val star = ImageView(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        width = 40
                        height = 40
                        if (i > 1) marginStart = 8
                    }
                    setImageResource(R.drawable.ic_star)
                    imageTintList = if (i <= item.rating) starColor else inactiveColor
                }
                llStarsContainer.addView(star)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_visited_bar, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}