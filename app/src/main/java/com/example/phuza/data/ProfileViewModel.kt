package com.example.phuza.data

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore

class ProfileViewModel : ViewModel() {
    private val rtdb = FirebaseDatabase.getInstance().reference

    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    // LiveData for user profile
    private val _userProfile = MutableLiveData<UserDto?>()
    val userProfile: LiveData<UserDto?> = _userProfile
    private lateinit var rtdbListener: ValueEventListener

    init{
        fetchUserProfileFromRtdb()
    }

    private fun fetchUserProfileFromRtdb(){
        if(userId.isNullOrEmpty())
            return
        rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userNode = snapshot.child("users").child(userId!!)

                val userDto = UserDto(
                    uid = userId,
                    firstName = userNode.child("firstName").getValue(String::class.java),
                    username = userNode.child("username").getValue(String::class.java),
                    name = userNode.child("name").getValue(String::class.java),
                    avatar = userNode.child("avatar").getValue(String::class.java),
                    location = userNode.child("location").getValue(String::class.java),
                    favoriteDrink = userNode.child("favoriteDrink").getValue(String::class.java)
                )
                Log.d("ProfileVM", "RTDB User Fetched: $userDto")
                _userProfile.value = userDto
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileVM", "RTDB Profile fetch failed: ${error.message}")
                _userProfile.value = null
            }
        }
        rtdb.addValueEventListener(rtdbListener)
    }

    override fun onCleared() {
        super.onCleared()
        rtdb.removeEventListener(rtdbListener)
    }
    // LiveData for favourite bars
    val _favoriteBars = MutableLiveData<List<BarUi>>()
    val favoriteBars: LiveData<List<BarUi>> = _favoriteBars

    init{
        fetchFavoriteBarsFromRtdb()
    }

    private fun fetchFavoriteBarsFromRtdb(){
        if(userId.isNullOrEmpty())
            return
        rtdb.child("users").child(userId!!).child("favoriteBars")
            .limitToLast(3)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val bars = snapshot.children.mapNotNull { barSnapshot ->

                        val barName = barSnapshot.getValue(String::class.java)
                        if(barName != null){
                            BarUi(name = barName)
                        }
                        else{
                            null
                        }
                    }
                    Log.d("ProfileVM", "RTDB Favorite Bars fetched: ${bars.size}")
                    _favoriteBars.value = bars
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileVM", "RTDB Favourite bars fetch failed: ${error.message}")
                    _favoriteBars.value = emptyList()
                }
            })
    }
}