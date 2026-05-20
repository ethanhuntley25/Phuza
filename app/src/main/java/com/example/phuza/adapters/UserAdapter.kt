package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.FriendshipStatus
import com.example.phuza.data.UserDto
import com.example.phuza.databinding.ItemUserBinding
import com.example.phuza.utils.AvatarUtil
import com.example.phuza.utils.ImageUtils

class UserAdapter(
    private val onPrimaryClick: (UserDto, FriendshipStatus?) -> Unit,
    private val onAccept: (UserDto) -> Unit,
    private val onReject: (UserDto) -> Unit
) : ListAdapter<UserDto, UserAdapter.VH>(DIFF) {

    private var statusByUserId: Map<String, FriendshipStatus> = emptyMap()
    private var incomingSet: Set<String> = emptySet()
    fun submitUsers(list: List<UserDto>) = submitList(list)
    fun submitStatuses(map: Map<String, FriendshipStatus>) {
        statusByUserId = map
        notifyDataSetChanged()
    }
    fun submitIncoming(set: Set<String>) {
        incomingSet = set
        notifyDataSetChanged()
    }

    object DIFF : DiffUtil.ItemCallback<UserDto>() {
        override fun areItemsTheSame(oldItem: UserDto, newItem: UserDto): Boolean {
            val oldKey = oldItem.uid ?: oldItem.id ?: oldItem.username
            val newKey = newItem.uid ?: newItem.id ?: newItem.username
            return oldKey == newKey
        }
        override fun areContentsTheSame(oldItem: UserDto, newItem: UserDto): Boolean = oldItem == newItem
    }

    inner class VH(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserDto) = with(binding) {
            btnAdd.visibility = View.VISIBLE
            btnAdd.isEnabled = true
            incomingRow.visibility = View.GONE
            btnAccept.visibility = View.GONE
            btnReject.visibility = View.GONE
            btnAdd.setOnClickListener(null)
            btnAccept.setOnClickListener(null)
            btnReject.setOnClickListener(null)

            tvUsername.text = item.username?.let { "@$it" } ?: "@user"
            tvName.text = item.firstName ?: item.name ?: ""

            val avatarStr = item.avatar
            val mappedRes = avatarStr?.let { key -> AvatarUtil.avatarList.firstOrNull { it.first == key }?.second }
            when {
                mappedRes != null -> imgAvatar.setImageDrawable(ContextCompat.getDrawable(root.context, mappedRes))
                !avatarStr.isNullOrBlank() -> {
                    val base64 = avatarStr.substringAfter(",", avatarStr) // strip possible data-url prefix
                    val bmp = ImageUtils.decodeBase64ToBitmap(base64)
                    if (bmp != null) imgAvatar.setImageBitmap(bmp) else imgAvatar.setImageResource(R.drawable.avatar_no_avatar) //avatar_placeholder
                }
                else -> imgAvatar.setImageResource(R.drawable.avatar_no_avatar) //avatar_placeholder
            }

            val key = item.uid ?: item.id ?: item.username.orEmpty()
            val status = statusByUserId[key]
            val incoming = incomingSet.contains(key)

            if (incoming && status == FriendshipStatus.requested) {
                btnAdd.visibility = View.GONE
                incomingRow.visibility = View.VISIBLE
                btnAccept.visibility = View.VISIBLE
                btnReject.visibility = View.VISIBLE

                btnAccept.contentDescription = root.context.getString(R.string.accept)
                btnReject.contentDescription = root.context.getString(R.string.reject)

                btnAccept.setOnClickListener { onAccept(item) }
                btnReject.setOnClickListener { onReject(item) }
            } else {
                when (status) {
                    null -> {
                        btnAdd.isEnabled = true
                        btnAdd.text = root.context.getString(R.string.add_friend)
                        btnAdd.backgroundTintList = ContextCompat.getColorStateList(root.context, R.color.lavender)
                    }
                    FriendshipStatus.requested -> {
                        btnAdd.isEnabled = true
                        btnAdd.text = root.context.getString(R.string.requested)
                        btnAdd.backgroundTintList = ContextCompat.getColorStateList(root.context, R.color.Dark_Purple) // purple
                    }
                    FriendshipStatus.follow -> {
                        btnAdd.isEnabled = true
                        btnAdd.text = root.context.getString(R.string.following)
                    }
                    FriendshipStatus.following -> {
                        btnAdd.isEnabled = false
                        btnAdd.text = root.context.getString(R.string.they_follow)
                    }
                    FriendshipStatus.block -> {
                        btnAdd.isEnabled = false
                        btnAdd.text = root.context.getString(R.string.blocked)
                    }
                }

                btnAdd.setOnClickListener {
                    onPrimaryClick(item, status)

                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION && key.isNotBlank()) {
                        statusByUserId = when (status) {
                            null -> statusByUserId + (key to FriendshipStatus.requested)
                            FriendshipStatus.follow -> statusByUserId - key
                            else -> statusByUserId
                        }
                        notifyItemChanged(pos)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
