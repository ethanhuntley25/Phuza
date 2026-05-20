package com.example.phuza

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.phuza.adapters.BarDashboardAdapter
import com.example.phuza.adapters.FriendReviewAdapter
import com.example.phuza.adapters.FriendsFavBarAdapter
import com.example.phuza.data.*
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.gestures
import kotlin.math.abs

class DashboardActivity : BaseActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var btnNotifications: ImageButton
    private var txtGreeting: TextView? = null
    private var imgAvatar: ImageView? = null

    private var rvFriendReviews: RecyclerView? = null
    private var tvNoFriendReviews: TextView? = null
    private lateinit var friendAdapter: FriendReviewAdapter

    private val firestore = FirebaseFirestore.getInstance()
    private var friendshipsListener: ListenerRegistration? = null

    private val activeReviewListeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    private var mapSnippetView: com.mapbox.maps.MapView? = null
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) centerSnippetOnUser() else Log.w("Dashboard", "Location permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        applyInsets(R.id.root)

        bottomNav = findViewById(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_dashboard)

        rvFriendReviews = findViewById(R.id.rvFriendReviews)
        tvNoFriendReviews = findViewById(R.id.tvNoFriendReviews)

        txtGreeting = findViewById(R.id.txtGreeting)
        imgAvatar = findViewById(R.id.avatar)
        mapSnippetView = findViewById(R.id.mapSnippetView)

        btnNotifications = findViewById(R.id.btnBell)
        btnNotifications.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }

        setupSnippetMap()

        findViewById<View>(R.id.mapSnippetTapTarget)?.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
            }
        }

        bindUserHeader()
        setupAvatarMenu()
        popularBars()
        setupFriendsFavBars()
        observeNotificationsBell()

        friendAdapter = FriendReviewAdapter()
        rvFriendReviews?.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = friendAdapter
        }

        loadFriendsReviews()
    }

    private fun loadFriendsReviews() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        friendshipsListener?.remove()

        friendshipsListener = firestore.collection("friendships")
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    showNoFriendReviews()
                    detachReviewListeners()
                    return@addSnapshotListener
                }

                val iFollow = mutableListOf<String>()
                for (doc in snap.documents) {
                    val f = doc.toObject(Friendship::class.java) ?: continue
                    if (f.status == "accepted" && f.followerId == uid) {
                        val friendId = if (f.user1Id == uid) f.user2Id else f.user1Id
                        if (friendId.isNotBlank()) iFollow += friendId
                    }
                }

                if (iFollow.isEmpty()) {
                    showNoFriendReviews()
                    detachReviewListeners()
                } else {
                    tvNoFriendReviews?.visibility = View.GONE
                    rvFriendReviews?.visibility = View.VISIBLE
                    fetchAuthorsFromRtdb(iFollow)
                    attachRealtimeReviewsListeners(iFollow)
                }
            }
    }

    private fun fetchAuthorsFromRtdb(userIds: List<String>) {
        val rtdb = FirebaseDatabase.getInstance().reference
        userIds.forEach { id ->
            rtdb.child("users").child(id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(s: DataSnapshot) {
                        val name = s.child("firstName").getValue(String::class.java)
                            ?: s.child("name").getValue(String::class.java)
                        val avatar = s.child("avatar").getValue(String::class.java)
                        friendAdapter.putAuthor(id, name, avatar)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.w("Dashboard", "author fetch cancelled for $id: ${error.message}")
                    }
                })
        }
    }

    private fun attachRealtimeReviewsListeners(friendIds: List<String>) {
        detachReviewListeners()
        val db = FirebaseDatabase.getInstance().reference
        val merged = mutableListOf<Review>()

        friendIds.forEach { fid ->
            val ref = db.child("users").child(fid).child("reviews")
            val q = ref.orderByChild("timestamp").limitToLast(5)

            val l = object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val chunk = s.children.mapNotNull {
                        it.getValue(Review::class.java)?.copy(authorId = fid)
                    }

                    val dedup = (merged + chunk).associateBy { it.id }.values
                        .sortedByDescending { it.timestamp }

                    merged.clear()
                    merged.addAll(dedup)

                    if (merged.isEmpty()) showNoFriendReviews()
                    else {
                        tvNoFriendReviews?.visibility = View.GONE
                        rvFriendReviews?.visibility = View.VISIBLE
                        friendAdapter.submit(merged)
                    }
                }

                override fun onCancelled(e: DatabaseError) {
                    Log.w("Dashboard", "reviews cancelled for $fid: ${e.message}")
                    showNoFriendReviews()
                }
            }

            q.addValueEventListener(l)
            activeReviewListeners += (ref to l)
        }
    }

    private fun detachReviewListeners() {
        activeReviewListeners.forEach { (ref, l) -> ref.removeEventListener(l) }
        activeReviewListeners.clear()
    }

    private fun showNoFriendReviews() {
        rvFriendReviews?.visibility = View.GONE
        tvNoFriendReviews?.visibility = View.VISIBLE
    }

    private fun bindUserHeader() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val first = s.child("firstName").getValue(String::class.java) ?: "there"
                    val avatarValue = s.child("avatar").getValue(String::class.java)
                    setHeader(first, avatarValue)
                }
                override fun onCancelled(e: DatabaseError) {
                    Log.e("Dashboard", "DB error: ${e.message}")
                    setHeader("there", null)
                }
            })
    }

    private fun setupAvatarMenu() {
        imgAvatar?.setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            popup.menuInflater.inflate(R.menu.menu_sign_out, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_sign_out -> {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
                        } else {
                            @Suppress("DEPRECATION")
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        }
                        finish()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun setHeader(firstName: String, avatarValue: String?) {
        txtGreeting?.text = getString(R.string.hi_format, firstName)
        when {
            avatarValue.isNullOrBlank() -> imgAvatar?.setImageResource(R.drawable.avatar_no_avatar)
            looksLikeBase64Image(avatarValue) -> {
                val bmp = com.example.phuza.utils.ImageUtils.decodeBase64ToBitmap(avatarValue)
                if (bmp != null) {
                    imgAvatar?.setImageBitmap(bmp)
                    imgAvatar?.scaleType = ImageView.ScaleType.CENTER_CROP
                } else imgAvatar?.setImageResource(R.drawable.avatar_no_avatar)
            }
            else -> {
                val resId = resources.getIdentifier(avatarValue, "drawable", packageName)
                imgAvatar?.setImageResource(if (resId != 0) resId else R.drawable.avatar_no_avatar)
            }
        }
    }

    private fun looksLikeBase64Image(value: String): Boolean {
        if (value.startsWith("data:image")) return true
        if (value.length < 100) return false
        return value.all { it.isLetterOrDigit() || it in "+/=\n\r" }
    }

    private fun setupSnippetMap() {
        val mv = mapSnippetView ?: return
        mv.mapboxMap.loadStyle(Style.MAPBOX_STREETS) {
            mv.gestures.updateSettings {
                scrollEnabled = false; rotateEnabled = false
                pinchToZoomEnabled = false; quickZoomEnabled = false
                pitchEnabled = false
            }
            permLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerSnippetOnUser() {
        fused.lastLocation.addOnSuccessListener { loc ->
            val mv = mapSnippetView ?: return@addOnSuccessListener
            if (loc != null) {
                mv.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(loc.longitude, loc.latitude))
                        .zoom(14.5)
                        .build()
                )
                findViewById<View>(R.id.snippetCenterDot)?.visibility = View.VISIBLE
            }
        }
    }

    private fun popularBars() {
        val db = FirebaseFirestore.getInstance()
        db.collection("locations")
            .limit(9)
            .get()
            .addOnSuccessListener { snapshot ->
                val bars: List<BarUi> = snapshot.toObjects(BarUi::class.java)

                val recyclerView = findViewById<RecyclerView>(R.id.rvPopularBars)
                val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                recyclerView.layoutManager = layoutManager
                recyclerView.adapter = BarDashboardAdapter(bars)

                val snapHelper = LinearSnapHelper()
                snapHelper.attachToRecyclerView(recyclerView)

                recyclerView.post {
                    recyclerView.scrollToPosition(Int.MAX_VALUE / 2)
                    val view = snapHelper.findSnapView(layoutManager)
                    view?.let {
                        val distance = snapHelper.calculateDistanceToFinalSnap(layoutManager, it)
                        recyclerView.smoothScrollBy(distance?.get(0) ?: 0, distance?.get(1) ?: 0)
                    }
                }

                recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        val center = recyclerView.width / 2
                        for (i in 0 until recyclerView.childCount) {
                            val child = recyclerView.getChildAt(i)
                            val childCenter = (child.left + child.right) / 2
                            val distanceFromCenter = abs(center - childCenter)
                            val scale = 1.0f - (distanceFromCenter.toFloat() / recyclerView.width) * 0.8f
                            child.scaleX = scale.coerceIn(0.3f, 1.0f)
                            child.scaleY = scale.coerceIn(0.3f, 1.0f)
                            val params = child.layoutParams as ViewGroup.MarginLayoutParams
                            params.marginEnd = 20
                            params.marginStart = 20
                            child.layoutParams = params
                        }
                    }
                })
            }
    }

    private fun setupFriendsFavBars() {
        val currentUserId = FirebaseAuth.getInstance().uid ?: return
        val usersRef = FirebaseDatabase.getInstance().getReference("users")
        val firestore = FirebaseFirestore.getInstance()

        val rvFriendsFav: RecyclerView = findViewById(R.id.rvFriendsFav)
        val tvNoFriendsFavs: TextView = findViewById(R.id.tvNoFriendsFavs)

        firestore.collection("friendships")
            .get()
            .addOnSuccessListener { snap ->
                val friendIds = mutableListOf<String>()
                for (doc in snap.documents) {
                    val f = doc.toObject(Friendship::class.java) ?: continue
                    if (f.status == "accepted" && f.followerId == currentUserId) {
                        val friendId = if (f.user1Id == currentUserId) f.user2Id else f.user1Id
                        if (friendId.isNotBlank()) friendIds += friendId
                    }
                }

                if (friendIds.isEmpty()) {
                    tvNoFriendsFavs.visibility = View.VISIBLE
                    rvFriendsFav.visibility = View.GONE
                    return@addOnSuccessListener
                }

                usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val barToAvatars = mutableMapOf<String, MutableList<String>>()

                        for (userSnap in snapshot.children) {
                            val uid = userSnap.child("uid").getValue(String::class.java) ?: continue
                            if (uid !in friendIds) continue
                            val avatarValue = userSnap.child("avatar").getValue(String::class.java) ?: continue
                            val favBars = userSnap.child("favoriteBars").children
                                .mapNotNull { it.getValue(String::class.java) }
                            for (barName in favBars) {
                                val list = barToAvatars.getOrPut(barName) { mutableListOf() }
                                if (list.size < 3) list.add(avatarValue)
                            }
                        }

                        val result = barToAvatars.map { (barName, avatars) ->
                            FriendsFavBar(barName = barName, friendAvatars = avatars)
                        }

                        if (result.isEmpty()) {
                            tvNoFriendsFavs.visibility = View.VISIBLE
                            rvFriendsFav.visibility = View.GONE
                        } else {
                            tvNoFriendsFavs.visibility = View.GONE
                            rvFriendsFav.visibility = View.VISIBLE
                            rvFriendsFav.layoutManager = LinearLayoutManager(
                                this@DashboardActivity,
                                LinearLayoutManager.HORIZONTAL,
                                false
                            )
                            rvFriendsFav.adapter = FriendsFavBarAdapter(result)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        tvNoFriendsFavs.visibility = View.VISIBLE
                        rvFriendsFav.visibility = View.GONE
                    }
                })
            }
            .addOnFailureListener {
                tvNoFriendsFavs.visibility = View.VISIBLE
                rvFriendsFav.visibility = View.GONE
            }
    }

    private fun observeNotificationsBell() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val bell = findViewById<ImageButton>(R.id.btnBell)

        FirebaseFirestore.getInstance()
            .collection("Notifications")
            .document(uid)
            .collection("items")
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ ->
                bell.setImageResource(
                    if (snap != null && !snap.isEmpty)
                        R.drawable.ic_bell_notification
                    else
                        R.drawable.ic_bell
                )
            }
    }

    // ---------------------- BOTTOM NAV ----------------------
    override fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_friends -> {
                    startActivity(Intent(this, FriendsActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                    finish()
                    true
                }
                R.id.nav_add_review -> {
                    startActivity(Intent(this, AddReviewActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in_bottom, R.anim.fade_out_bottom)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom)
                    }
                    finish()
                    true
                }
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
}
