package com.example.phuza.adapters

import  android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.R
import com.example.phuza.api.MbxFeature
class LocationSearchAdapter (
    private var results: List<MbxFeature>,
    private val onFeatureSelected: (MbxFeature) -> Unit
) : RecyclerView.Adapter<LocationSearchAdapter.SearchViewHolder>(){

    class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view){
        val placeName: TextView = view.findViewById(R.id.tv_place_name)
        val fullAddress: TextView = view.findViewById(R.id.tv_full_address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view  = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_search, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val feature = results[position]
        holder.placeName.text = feature.text
        holder.fullAddress.text = feature.placeName
        holder.itemView.setOnClickListener {
            onFeatureSelected(feature)
        }
    }

    override fun getItemCount(): Int = results.size

    fun updateResults(newResults: List<MbxFeature>){
        results = newResults
        notifyDataSetChanged()
    }

    fun clearResults(){
        results = emptyList()
        notifyDataSetChanged()
    }
}