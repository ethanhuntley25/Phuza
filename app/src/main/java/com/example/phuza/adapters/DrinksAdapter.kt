package com.example.phuza.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.data.Drink

class DrinksAdapter(private var drinks: List<Drink>) :
    RecyclerView.Adapter<DrinksAdapter.DrinkViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION

    inner class DrinkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDrinkName: TextView = itemView.findViewById(R.id.tvDrinkName)
        val viewIndicator: View = itemView.findViewById(R.id.viewIndicator)

        init {
            itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = bindingAdapterPosition

                if (selectedPosition != RecyclerView.NO_POSITION) {
                    if (previousPosition != RecyclerView.NO_POSITION) {
                        notifyItemChanged(previousPosition)
                    }
                    notifyItemChanged(selectedPosition)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrinkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drink, parent, false)
        return DrinkViewHolder(view)
    }

    override fun onBindViewHolder(holder: DrinkViewHolder, position: Int) {
        val drink = drinks[position]
        holder.tvDrinkName.text = drink.name
        holder.viewIndicator.isSelected = (position == selectedPosition)
    }

    override fun getItemCount() = drinks.size

    fun getSelectedDrink(): Drink? {
        return if (selectedPosition != RecyclerView.NO_POSITION) drinks[selectedPosition] else null
    }
    fun updateData(newDrinks: List<Drink>) {
        drinks = newDrinks
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }
}


