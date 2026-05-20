package com.example.phuza

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phuza.adapters.VisitedBarsAdapter
import com.example.phuza.data.Review
import com.example.phuza.databinding.ActivityVisitedBarsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class VisitedBarsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVisitedBarsBinding
    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().reference

    private lateinit var adapter: VisitedBarsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVisitedBarsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        fetchUserReviews()
    }

    private fun setupRecyclerView(){
        adapter = VisitedBarsAdapter()
        binding.rvVisitedBars.layoutManager = LinearLayoutManager(this)
        binding.rvVisitedBars.adapter = adapter
    }

    private fun fetchUserReviews(){
        val uid = auth.currentUser?.uid ?: return

        rtdb.child("users").child(uid).child("reviews")
            .orderByChild("timestamp")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val reviews = snapshot.children.asSequence().mapNotNull {
                       try{
                           it.getValue(Review::class.java)
                       } catch(e: Exception){
                           Log.e("VisitedBars", "Failed to map review: ${it.key}, Error: ${e.message}")
                           null
                       }
                    }.sortedByDescending { it.timestamp }.toList()

                    adapter.submitList(reviews)
                    if(reviews.isEmpty()){
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.rvVisitedBars.visibility = View.GONE
                    } else{
                        binding.tvEmptyState.visibility = View.GONE
                        binding.rvVisitedBars.visibility = View.VISIBLE
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    binding.tvEmptyState.text = getString(R.string.error_loading_bars)
                    binding.tvEmptyState.visibility = View.VISIBLE
                }
            })
    }
}