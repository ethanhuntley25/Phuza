package com.example.phuza

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.phuza.data.Review
import com.example.phuza.databinding.ActivityAddReviewBinding
import com.example.phuza.utils.ImageUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AddReviewActivity : BaseActivity() {

    private lateinit var binding: ActivityAddReviewBinding
    private var selectedRating = 0
    private var currentBitmap: Bitmap? = null
    private var isSaving = false
    private var isLiked = false // track heart status

    // Camera preview
    private val takePicturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap -> if (bitmap != null) onBitmapChosen(bitmap) else toast("No photo captured") }

    // Gallery picker
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onImageUriChosen(it) } ?: toast("No image selected") }

    // Camera permission
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takePicturePreview.launch(null)
        else toast("Camera permission is required")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets(binding.main.id)

        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_add_review)

        val tvCancel = findViewById<TextView>(R.id.tvCancel)

        setupStars()
        setupImageActions()
        setupHeart()
        setupSave()

        tvCancel.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            finish()
        }
    }

    // ---------------------- STARS ----------------------
    private fun setupStars() {
        val stars = listOf(binding.rStar1, binding.rStar2, binding.rStar3, binding.rStar4, binding.rStar5)
        stars.forEachIndexed { idx, iv -> iv.setOnClickListener { setRating(idx + 1, stars) } }
    }

    private fun setRating(rating: Int, stars: List<ImageView>) {
        selectedRating = rating
        stars.forEachIndexed { i, iv ->
            val color = if (i < rating) R.color.yellow else R.color.star_inactive
            iv.imageTintList = ContextCompat.getColorStateList(this, color)
        }
    }

    // ---------------------- IMAGE ----------------------
    private fun setupImageActions() {
        binding.btnAddImage.setOnClickListener { showImageChooser() }
        binding.btnAddImage.setOnLongClickListener {
            currentBitmap = null
            binding.btnAddImage.setImageResource(R.drawable.ic_camera)
            binding.btnAddImage.setColorFilter(ContextCompat.getColor(this, R.color.lavender))
            binding.btnAddImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
            toast("Image removed")
            true
        }
    }

    private fun showImageChooser() {
        val options = arrayOf("Take photo", "Choose from gallery")
        AlertDialog.Builder(this)
            .setTitle("Add photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermission.launch(Manifest.permission.CAMERA)
                    1 -> pickImage.launch("image/*")
                }
            }
            .show()
    }

    private fun onImageUriChosen(uri: Uri) {
        try {
            val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            onBitmapChosen(bmp)
        } catch (e: Exception) {
            toast("Failed to load image: ${e.message}")
        }
    }

    private fun onBitmapChosen(bitmap: Bitmap) {
        currentBitmap = bitmap
        binding.btnAddImage.setImageBitmap(bitmap)
        binding.btnAddImage.clearColorFilter()
        binding.btnAddImage.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    // ---------------------- HEART ----------------------
    private fun setupHeart() {
        binding.ivHeart.setOnClickListener {
            isLiked = !isLiked
            val heartIcon = if (isLiked) R.drawable.ic_heart else R.drawable.ic_heart_outline
            binding.ivHeart.setImageResource(heartIcon)
        }
    }

    // ---------------------- SAVE ----------------------
    private fun setupSave() {
        binding.tvSave.setOnClickListener {
            if (isSaving) return@setOnClickListener

            val place = binding.etPlace.text?.toString()?.trim().orEmpty()
            val desc = binding.etDescription.text?.toString()?.trim().orEmpty()

            if (place.isEmpty()) {
                toast("Please enter a place")
                return@setOnClickListener
            }
            if (selectedRating == 0) {
                toast("Please select a rating")
                return@setOnClickListener
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                toast("Please sign in again")
                return@setOnClickListener
            }

            val imageB64 = currentBitmap?.let { ImageUtils.encodeBitmapToBase64(it, quality = 70) }
            val reviewId = UUID.randomUUID().toString()
            val baseReview = Review(
                id = reviewId,
                place = place,
                description = desc,
                rating = selectedRating,
                imageBase64 = imageB64,
                timestamp = System.currentTimeMillis(),
                authorId = uid,
                authorName = null,
                authorAvatar = null,
                liked = isLiked
            )

            isSaving = true
            setSavingUi(saving = true)

            lifecycleScope.launch {
                try {
                    val userSnap = FirebaseDatabase.getInstance()
                        .reference.child("users").child(uid)
                        .get().await()

                    val authorName = userSnap.child("firstName").getValue(String::class.java)
                        ?: userSnap.child("name").getValue(String::class.java)
                    val authorAvatar = userSnap.child("avatar").getValue(String::class.java)

                    val review = baseReview.copy(authorName = authorName, authorAvatar = authorAvatar)

                    val userRef = FirebaseDatabase.getInstance().reference.child("users").child(uid)
                    userRef.child("reviews").child(review.id).setValue(review).await()

                    if (isLiked) {
                        userRef.child("likedBars").child(review.place).setValue(true).await()
                    }

                    toast("Review saved")
                    clearFields()
                    val intent = Intent(this@AddReviewActivity, DashboardActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                    finish()
                } catch (e: Exception) {
                    toast("Failed to save: ${e.message}")
                } finally {
                    isSaving = false
                    setSavingUi(false)
                }
            }
        }
    }

    private fun setSavingUi(saving: Boolean) {
        binding.tvSave.isEnabled = !saving
        binding.tvSave.text = if (saving) "Saving…" else "Review Saved"
        binding.tvSave.alpha = if (saving) 0.7f else 1f
    }

    // ---------------------- BOTTOM NAV ----------------------
    override fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_left, R.anim.slide_out_right)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                    finish()
                    true
                }
                R.id.nav_friends -> {
                    startActivity(Intent(this, FriendsActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_left, R.anim.slide_out_right)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                    finish()
                    true
                }
                R.id.nav_add_review -> true
                R.id.nav_invitations -> {
                    startActivity(Intent(this, InvitationsActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                    finish()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    // ---------------------- HELPERS ----------------------
    private fun clearFields() {
        binding.etPlace.text?.clear()
        binding.etDescription.text?.clear()

        selectedRating = 0
        val stars = listOf(binding.rStar1, binding.rStar2, binding.rStar3, binding.rStar4, binding.rStar5)
        stars.forEach {
            it.imageTintList = ContextCompat.getColorStateList(this, R.color.star_inactive)
        }

        currentBitmap = null
        binding.btnAddImage.setImageResource(R.drawable.ic_camera)
        binding.btnAddImage.setColorFilter(ContextCompat.getColor(this, R.color.lavender))
        binding.btnAddImage.scaleType = ImageView.ScaleType.CENTER_INSIDE

        isLiked = false
        binding.ivHeart.setImageResource(R.drawable.ic_heart_outline)
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}
