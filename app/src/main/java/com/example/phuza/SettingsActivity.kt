package com.example.phuza

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.phuza.databinding.ActivitySettingsBinding
import com.example.phuza.utils.ImageUtils
import com.example.phuza.utils.AvatarUtil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts


class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var userListener: ListenerRegistration? = null

    // For Gallery Upload
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var requestMediaPermsLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialise view binding
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup image pickers for gallery upload (copied from Onboarding1Activity)
        setupImagePickers()

        // Setup ui elements
        setupActionBar()
        setupListItemTitles()
        setupOnClickListeners()
        // This listener is crucial for real-time updates when the data changes in Firestore
        observeUserProfile()
    }

    private fun setupImagePickers() {
        // Registers the launcher to pick an image from the gallery
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadAvatarFromUri(uri)
        }
        // Registers the launcher to request media permissions
        requestMediaPermsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted = result.values.any { it }
            if (granted) pickImageLauncher.launch("image/*") else toast("Permission required to choose a photo")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
    }

    private fun observeUserProfile() {
        val userId = auth.currentUser?.uid ?: return

        userListener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) {
                    binding.avatar.setImageResource(R.drawable.avatar_no_avatar)
                    return@addSnapshotListener
                }

                val avatarStr = snapshot.getString("avatar")
                // This line updates the avatar every time the data changes in Firestore
                displayAvatar(avatarStr, binding.avatar)
            }
    }

    private fun displayAvatar(avatarStr: String?, imageView: ImageView) {
        val mappedRes = avatarStr?.let { key ->
            AvatarUtil.avatarList.firstOrNull { it.first == key }?.second
        }
        when {
            mappedRes != null -> {
                imageView.setImageResource(mappedRes)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            }
            !avatarStr.isNullOrBlank() -> {
                // Handle Base64 (uploaded image) or URL (Glide)
                val base64 = avatarStr.substringAfter(",", avatarStr)
                val bmp = ImageUtils.decodeBase64ToBitmap(base64)

                if (bmp != null) {
                    imageView.setImageBitmap(bmp)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    // Fallback to Glide for potential URLs (e.g., if you later implement storage URLs)
                    Glide.with(this)
                        .load(avatarStr)
                        .placeholder(R.drawable.avatar_no_avatar)
                        .error(R.drawable.avatar_no_avatar)
                        .into(imageView)
                }
            }
            else -> {
                imageView.setImageResource(R.drawable.avatar_no_avatar)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    private fun showAvatarSelectionBottomSheet() {
        AvatarUtil.showAvatarSelector(this) { selectedAvatarKey ->
            saveSelectedAvatarToFirebase(selectedAvatarKey)
        }
    }

    private fun saveSelectedAvatarToFirebase(avatarKey: String) {

        // get current users UID
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val firestoreRef = firestore.collection("users").document(userId)
        val rtdbRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(userId)
        val updateMap = mapOf("avatar" to avatarKey)

        // Use set() with merge option to create the document if it doesn't exist,
        // or update only the 'avatar' field if it does exist.
        firestoreRef.set(updateMap, SetOptions.merge()).addOnSuccessListener {
            // Update Realtime Database only after Firestore is successful
            rtdbRef.child("avatar").setValue(avatarKey).addOnSuccessListener {
                Toast.makeText(this, "Avatar updated successfully!", Toast.LENGTH_SHORT).show()
                // Explicitly update UI for immediate feedback
                displayAvatar(avatarKey, binding.avatar)
            }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Avatar updated to settings (Firestore), but failed to update Profile (RTDB). Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating avatar in Firestore: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupListItemTitles() {
//        setSettingTitle(binding.settingsChangeLocation.root, "Change location")
        setSettingTitle(binding.settingsChangePassword.root, "Change password")
//        setSettingTitle(binding.settingsNotificationPrefs.root, "Notification preferences")
//        setSettingTitle(binding.settingsLanguagePrefs.root, "Language preferences")
    }

    // helper function to find and set the title on the included layout
    private fun setSettingTitle(itemView: android.view.View, title: String) {
        itemView.findViewById<TextView>(R.id.settings_item_title)?.text = title
    }

    private fun setupActionBar() {
        // Handle back button
        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupOnClickListeners() {

        // select a pre-defined avatar
        binding.btnChooseAvatar.setOnClickListener {
            showAvatarSelectionBottomSheet()
        }

        //  upload from gallery
        binding.btnUploadGallery.setOnClickListener {
            val perms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                @Suppress("DEPRECATION")
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestMediaPermsLauncher.launch(perms)
        }

        binding.settingsChangePassword.root.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

//        binding.settingsNotificationPrefs.root.setOnClickListener {
//            startActivity(Intent(this, NotificationsPreferencesActivity::class.java))
//        }

//        binding.settingsLanguagePrefs.root.setOnClickListener {
//            startActivity(Intent(this, LanguagePreferenceActivity::class.java))
//        }
    }

    private fun loadAvatarFromUri(uri: android.net.Uri) {
        try {
            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(contentResolver, uri)
                )
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            val maxSide = 640
            val scaled = scaleBitmapIfNeeded(bitmap, maxSide)

            // Convert to Base64
            val pickedImageBase64 = ImageUtils.encodeBitmapToBase64(scaled, quality = 70)

            // Save Base64 to Firebase
            // the Base64 string gets saved as the 'avatar' value.
            saveSelectedAvatarToFirebase(pickedImageBase64)

        } catch (e: Exception) {
            toast("Failed to load image: ${e.message}")
        }
    }

    private fun scaleBitmapIfNeeded(src: android.graphics.Bitmap, maxSide: Int): android.graphics.Bitmap {
        val w = src.width
        val h = src.height
        val largest = maxOf(w, h)
        if (largest <= maxSide) return src
        val scale = maxSide.toFloat() / largest
        val nw = (w * scale).toInt()
        val nh = (h * scale).toInt()
        return android.graphics.Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
