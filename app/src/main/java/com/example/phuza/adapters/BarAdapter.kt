package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.BarUi

class BarAdapter(
    private val onSelectionChanged: (List<BarUi>) -> Unit = {},
) : ListAdapter<BarUi, BarAdapter.VH>(DIFF) {
    private val selectedKeys = LinkedHashSet<String>()


    private val knownByKey = HashMap<String, BarUi>()

    private fun keyOf(item: BarUi) = "${item.name}|${item.latitude},${item.longitude}"

    fun getSelectedBars(): List<BarUi> = selectedKeys.mapNotNull { knownByKey[it] }

    override fun submitList(list: List<BarUi>?) {
        list?.forEach { knownByKey[keyOf(it)] = it }
        super.submitList(list)
        onSelectionChanged(getSelectedBars())
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BarUi>() {
            override fun areItemsTheSame(oldItem: BarUi, newItem: BarUi): Boolean =
                (oldItem.name == newItem.name) &&
                        (oldItem.latitude == newItem.latitude) &&
                        (oldItem.longitude == newItem.longitude)

            override fun areContentsTheSame(oldItem: BarUi, newItem: BarUi): Boolean = oldItem == newItem
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val imgSelector: ImageView = view.findViewById(R.id.imgSelector)

        fun bind(item: BarUi) {
            tvName.text = item.name

            val selected = selectedKeys.contains(keyOf(item))
            imgSelector.setBackgroundResource(
                if (selected) R.drawable.selector_circle_filled
                else R.drawable.selector_circle_bg
            )

            itemView.setOnClickListener {
                val key = keyOf(item)
                if (selectedKeys.contains(key)) {
                    selectedKeys.remove(key)
                    notifyItemChanged(bindingAdapterPosition)
                    itemView.rootView.findViewById<TextView>(R.id.tvMaxSelectionWarning)
                        ?.visibility = View.GONE
                } else {
                    if (selectedKeys.size >= 3) {
                        itemView.rootView.findViewById<TextView>(R.id.tvMaxSelectionWarning)
                            ?.visibility = View.VISIBLE
                    } else {
                        selectedKeys.add(key)
                        // record in known map in case this item disappears due to filtering
                        knownByKey[key] = item
                        notifyItemChanged(bindingAdapterPosition)
                        itemView.rootView.findViewById<TextView>(R.id.tvMaxSelectionWarning)
                            ?.visibility = View.GONE
                    }
                }
                // Fire callback with full selection (not just visible)
                onSelectionChanged(getSelectedBars())
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bar, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }
}
