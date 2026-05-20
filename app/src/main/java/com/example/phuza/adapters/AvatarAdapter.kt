package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.databinding.ItemAvatarBinding

class AvatarAdapter(
    private val avatars: List<Pair<String, Int>>,
    private val onAvatarClick: (Pair<String, Int>) -> Unit
) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {

    private var selectedPos = RecyclerView.NO_POSITION

    inner class AvatarViewHolder(val binding: ItemAvatarBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(avatarPair: Pair<String, Int>, position: Int) {
            val (_, resId) = avatarPair

            binding.imgAvatar.setBackgroundResource(resId)

            // Show selection indicator
            binding.imgSelector.setBackgroundResource(
                if (position == selectedPos) R.drawable.selector_circle_filled
                else R.drawable.selector_circle_bg
            )

            binding.root.setOnClickListener {
                val prevPos = selectedPos
                selectedPos = position
                notifyItemChanged(prevPos)
                notifyItemChanged(selectedPos)
                onAvatarClick(avatarPair)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val binding = ItemAvatarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AvatarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        holder.bind(avatars[position], position)
    }

    override fun getItemCount() = avatars.size
}
