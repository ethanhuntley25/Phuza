package com.example.phuza

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.phuza.adapters.UserAdapter
import com.example.phuza.api.FriendsVMFactory
import com.example.phuza.api.FriendsViewModel
import com.example.phuza.api.RetrofitInstance
import com.example.phuza.api.UiState
import com.example.phuza.data.FriendshipStatus
import com.example.phuza.data.UserDto
import com.example.phuza.databinding.ActivityFriendsBinding
import com.example.phuza.utils.NotificationUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

class FriendsActivity : BaseActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private val vm: FriendsViewModel by viewModels { FriendsVMFactory() }

    private lateinit var adapter: UserAdapter
    private var allUsers: List<UserDto> = emptyList()
    private var loadingContainer: FrameLayout? = null
    private var loadingSpinner: ProgressBar? = null
    private var loadingLabel: TextView? = null

    private enum class Mode { FRIENDS, PENDING, DISCOVER }
    private var currentMode: Mode = Mode.FRIENDS

    private var notifReg: com.google.firebase.firestore.ListenerRegistration? = null

    // --- Notification permission request launcher ---
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startNotificationsListener()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.main.id)

        lifecycleScope.launch {
            wakeServer()
        }

        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNav)
        setupBottomNav(bottomNav, R.id.nav_friends)

        setupRecycler()
        setupSearch()
        setupModeToggle()

        binding.toggleModes.check(binding.btnModeFriends.id)

        buildLoadingOverlay()
        observeVm()
        warmUpFriends()
        ensureNotificationPermissionAndStart()
    }


    override fun onDestroy() {
        notifReg?.remove()
        notifReg = null
        super.onDestroy()
    }


    private fun warmUpFriends() {
        if (!vm.hasCache()) {
            showLoading()
            vm.loadUsers(null)
        } else {
            vm.loadUsers(null)
        }
    }

    // --- Notifications ---

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun ensureNotificationPermissionAndStart() {
        if (hasNotificationPermission()) {
            startNotificationsListener()
        } else if (Build.VERSION.SDK_INT >= 33) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun safeShowNotification(title: String, text: String) {
        if (!hasNotificationPermission()) return
        try {
            NotificationUtils.ensureChannel(this)
            NotificationUtils.show(this, title, text)
        } catch (se: SecurityException) {
            toast("Error: $se")
        }
    }

    private fun startNotificationsListener() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val col = db.collection("Notifications").document(uid).collection("items")

        notifReg = col.whereEqualTo("read", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if ((err != null) || (snap == null)) return@addSnapshotListener
                for (dc in snap.documentChanges) {
                    if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val type = dc.document.getString("type") ?: "notification"
                        val msg  = dc.document.getString("message") ?: ""
                        val title = if (type == "follow_request") "New follow request" else "Follow update"

                        val name = dc.document.getString("fromName") ?: "Someone"
                        val username = dc.document.getString("fromUsername") ?: ""
                        val text = if (username.isNotEmpty()) "$name ($username) $msg" else "$name $msg"

                        safeShowNotification(title, text)
                    }
                }
            }
    }

    private fun setupRecycler() {
        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter(
            onPrimaryClick = { user, status ->
                val id = user.uid ?: return@UserAdapter
                val incoming = vm.incomingSet.value?.contains(id) == true
                if (incoming && status == FriendshipStatus.requested) return@UserAdapter
                when (status) {
                    null -> vm.sendRequest(id)
                    FriendshipStatus.follow -> vm.unfollow(id)
                    FriendshipStatus.requested -> {
                        vm.cancelRequest(id)
                    }
                    FriendshipStatus.following,
                    FriendshipStatus.block -> Unit
                }
            },
            onAccept = { user -> user.uid?.let { vm.acceptRequest(it) } },
            onReject = { user -> user.uid?.let { vm.rejectRequest(it) } }
        )
        binding.recycler.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter() }
        })
    }

    private fun setupModeToggle() {
        binding.toggleModes.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentMode = when (checkedId) {
                binding.btnModeFriends.id -> Mode.FRIENDS
                binding.btnModePending.id -> Mode.PENDING
                binding.btnModeDiscover.id -> Mode.DISCOVER
                else -> Mode.FRIENDS
            }
            applyFilter()
        }
    }

    private fun observeVm() {
        vm.usersState.observe(this) { state ->
            when (state) {
                is UiState.Loading -> showLoading()
                is UiState.Success -> {
                    hideLoading()
                    allUsers = state.data
                    applyFilter()
                }
                is UiState.Error -> {
                    hideLoading()
                    allUsers = emptyList()
                    applyFilter()
                    toast(state.message)
                }
                else -> Unit
            }
        }
        vm.statusMap.observe(this) { applyFilter() }
        vm.incomingSet.observe(this) { applyFilter() }
    }

    private fun applyFilter() {
        if (!::adapter.isInitialized) return

        val q = binding.etSearch.text?.toString()?.trim()?.lowercase().orEmpty()
        val statusMap = vm.statusMap.value ?: emptyMap()

        val filtered = when (currentMode) {
            Mode.FRIENDS -> allUsers.filter { u ->
                (statusMap[u.uid.orEmpty()] == FriendshipStatus.follow)
            }
            Mode.PENDING -> allUsers.filter { u ->
                statusMap[u.uid.orEmpty()] == FriendshipStatus.requested
            }
            Mode.DISCOVER -> allUsers.filter { u ->
                when (statusMap[u.uid.orEmpty()]) {
                    FriendshipStatus.follow,
                    FriendshipStatus.requested -> false
                    else -> true
                }
            }
        }.filter { u ->
            if (q.isEmpty()) true
            else {
                val name = (u.firstName ?: u.name ?: "").lowercase()
                val handle = (u.username ?: "").lowercase()
                name.contains(q) || handle.contains(q)
            }
        }

        adapter.submitUsers(filtered)
        vm.statusMap.value?.let { adapter.submitStatuses(it) }
        vm.incomingSet.value?.let { adapter.submitIncoming(it) }

        val isLoading = vm.usersState.value is UiState.Loading
        if (!isLoading) {
            updateEmptyState(filtered.isEmpty(), q)
        }
    }


    private fun updateEmptyState(isEmpty: Boolean, query: String) {
        if (!isEmpty) {
            binding.emptyView.visibility = View.GONE
            binding.recycler.visibility = View.VISIBLE
            return
        }

        val msg = when (currentMode) {
            Mode.FRIENDS ->
                if (query.isEmpty()) "You’re not following anyone yet."
                else "No friends match your search."
            Mode.PENDING ->
                if (query.isEmpty()) "No pending requests."
                else "No pending requests match your search."
            Mode.DISCOVER ->
                if (query.isEmpty()) "No people to discover right now."
                else "No people match your search."
        }

        binding.emptyView.text = msg
        binding.emptyView.visibility = View.VISIBLE
        binding.recycler.visibility = View.GONE
    }


    // ------- Loading overlay -------

    private fun buildLoadingOverlay() {
        val root = binding.root as ViewGroup

        loadingContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x66_000000) // semi-transparent black
            visibility = View.GONE
            isClickable = true
            isFocusable = true
        }

        loadingSpinner = ProgressBar(this).apply {
            isIndeterminate = true
        }

        loadingLabel = TextView(this).apply {
            text = getString(R.string.loading_dots)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(0, dp(12), 0, 0)
            gravity = Gravity.CENTER
        }

        val column = FrameLayout(this).apply {
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            layoutParams = lp
        }

        val inner = FrameLayout(this)
        inner.addView(loadingSpinner, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL
        ))
        inner.addView(loadingLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        ).apply { topMargin = dp(56) })

        column.addView(inner)
        loadingContainer?.addView(column)
        root.addView(loadingContainer)
    }

    private fun showLoading() {
        binding.loadingContainer.visibility = View.VISIBLE
        binding.recycler.visibility = View.GONE
        binding.emptyView.visibility = View.GONE

        binding.toggleModes.isEnabled = false
        binding.etSearch.isEnabled = false
    }

    private fun hideLoading() {
        binding.loadingContainer.visibility = View.GONE

        // Re-enable user interaction
        binding.toggleModes.isEnabled = true
        binding.etSearch.isEnabled = true
    }

    private fun dp(px: Int): Int =
        (px * resources.displayMetrics.density).toInt()

    // Bottom nav helper for BaseActivity
    override fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        bottomNav.selectedItemId = selectedItemId
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard   -> { startActivity(Intent(this, DashboardActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_out_right, R.anim.slide_in_left)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_out_right, R.anim.slide_in_left)
                    }
                    finish()
                    true }

                R.id.nav_friends     -> true
                R.id.nav_add_review  -> { startActivity(Intent(this,AddReviewActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.fade_in_bottom, R.anim.fade_out_bottom)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom)
                    }
                    finish()
                    true }
                R.id.nav_invitations -> { startActivity(Intent(this, InvitationsActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                    finish()
                    true  }
                R.id.nav_profile     -> { startActivity(Intent(this, ProfileActivity::class.java))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.slide_in_right, R.anim.slide_out_left)
                    } else {
                        @Suppress("DEPRECATION")
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                    }
                    true }
                else -> false
            }
        }
    }

    private suspend fun wakeServer() {
        try {
            RetrofitInstance.api.health()
        } catch (e: Exception) {
            toast("Error: $e")
        }
    }
    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

