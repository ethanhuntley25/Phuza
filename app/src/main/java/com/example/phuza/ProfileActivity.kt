package com.example.phuza

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.phuza.R
import com.example.phuza.data.BarUi
import com.example.phuza.data.UserDto
import com.example.phuza.databinding.ActivityProfileBinding
import com.example.phuza.databinding.ItemBarBinding
import com.example.phuza.utils.AvatarUtil
import com.example.phuza.utils.ImageUtils
import com.example.phuza.data.ProfileViewModel
import kotlin.collections.forEachIndexed
import kotlin.getValue
import android.content.Intent
import android.view.View
import android.widget.TextView
import com.example.phuza.SettingsActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // initialise view binding
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets(R.id.main)
        setupBottomNav(binding.bottomNavBar.root.findViewById(R.id.bottomNav), R.id.nav_profile)

        observeUserProfile()
        observeFavoriteBars()
        setupListItemTitles()
        setupClickListeners()

    }

    private fun setupClickListeners(){
        binding.settingsIcon.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            // start new activity
            startActivity(intent)
        }
        binding.itemBarsVisited.root.setOnClickListener {
            startActivity(Intent(this, VisitedBarsActivity::class.java))
        }

        binding.itemFriends.root.setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }

//      WILL BE ADDED IN PART THREE
//        binding.itemLikedBars.root.setOnClickListener {
//            startActivity(Intent(this, ChangePasswordActivity::class.java))
//        }
//        binding.itemBarlist.root.setOnClickListener {
//            startActivity(Intent(this, NotificationsPreferencesActivity::class.java))
//        }
//        binding.itemReviews.root.setOnClickListener {
//            startActivity(Intent(this, LanguagePreferenceActivity::class.java))
//        }
//      WILL BE ADDED IN PART THREE

    }
    private fun observeUserProfile(){
        viewModel.userProfile.observe(this){ user ->
            user?.let {
                binding.profileName.text = it.name ?: it.firstName ?: it.username?: "user"

                val avatarStr = it.avatar

                val mappedRes = avatarStr?.let { key ->
                    AvatarUtil.avatarList.firstOrNull{ it.first == key }?.second
                }
                when {
                    mappedRes != null -> {
                        binding.avatar.setImageResource(mappedRes)
                    }
                    !avatarStr.isNullOrBlank() -> {
                        val base64 = avatarStr.substringAfter(",", avatarStr)
                        val bmp = ImageUtils.decodeBase64ToBitmap(base64)

                        if(bmp != null){
                            binding.avatar.setImageBitmap(bmp)
                        }
                        else{
                            Glide.with(this)
                                .load(avatarStr)
                                .placeholder(R.drawable.avatar_placeholder)
                                .error(R.drawable.avatar_no_avatar)
                                .into(binding.avatar)
                        }
                    }
                    else -> binding.avatar.setImageResource(R.drawable.avatar_no_avatar)
                }

                binding.profileLocation.text = it.location ?: this.getString(R.string.set_your_location)
                binding.myDop.text = it.favoriteDrink ?: this.getString(R.string.select_your_dop)

            }
        }
    }

    private fun observeFavoriteBars(){
        viewModel.favoriteBars.observe(this){ bars ->
            val barIncludes = listOf(
                binding.bar1Include,
                binding.bar2Include,
                binding.bar3Include
            )

            barIncludes.forEachIndexed { index, includeBinding ->
                if(index < bars.size){
                    val bar = bars[index]
                    includeBinding.name.text = bar.name   // directly through binding
                    includeBinding.root.visibility = View.VISIBLE
                } else {
                    includeBinding.root.visibility = View.GONE
                }
            }
        }
    }
    private fun setupListItemTitles(){

        setProfileTitle(binding.itemBarsVisited.root, "Bars Visited")

        setProfileTitle(binding.itemFriends.root, "Friends")
//      WILL BE ADDED IN PART THREE
//        setProfileTitle(binding.itemReviews.root, "My Reviews")
//        setProfileTitle(binding.itemLikedBars.root, "Liked Bars")
//        setProfileTitle(binding.itemBarlist.root, "Barlist")
    }
    
    private fun setProfileTitle(itemView: android.view.View, title:String){
        itemView.findViewById<TextView>(R.id.list_item_title)?.text = title
    }
}
