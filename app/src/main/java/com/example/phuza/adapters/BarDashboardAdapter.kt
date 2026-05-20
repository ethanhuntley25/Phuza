package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.BarUi

class BarDashboardAdapter(
    private val bars: List<BarUi>
) : RecyclerView.Adapter<BarDashboardAdapter.VH>() {

    override fun getItemCount(): Int = Int.MAX_VALUE

    private fun getRealPosition(position: Int): Int {
        return position % bars.size
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val img: ImageView = view.findViewById(R.id.image)
        private val name: TextView = view.findViewById(R.id.name)

        fun bind(bar: BarUi) {
            name.text = bar.name
            img.setImageResource(R.drawable.img_bar_placeholder)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bar_dashboard, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(bars[getRealPosition(position)])
    }
}
