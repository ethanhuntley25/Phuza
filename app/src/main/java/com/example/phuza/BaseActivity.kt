package com.example.phuza

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.os.Build

open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }
    protected fun applyInsets(rootId: Int) {
        val root = findViewById<View>(rootId)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = sys.top) // bottom handled by layout’s own padding/card
            WindowInsetsCompat.CONSUMED
        }
    }

    protected open fun setupBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        // highlight the current tab
        if (bottomNav.selectedItemId != selectedItemId) {
            bottomNav.selectedItemId = selectedItemId
        }

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true

            when (item.itemId) {
                R.id.nav_dashboard -> launchTop(DashboardActivity::class.java)
                R.id.nav_friends -> launchTop(FriendsActivity::class.java)
                R.id.nav_add_review -> launchTop(AddReviewActivity::class.java)
                R.id.nav_invitations -> launchTop(InvitationsActivity::class.java)
                R.id.nav_profile -> launchTop(ProfileActivity::class.java)
                else -> false
            }
        }
    }

    private fun launchTop(target: Class<*>) : Boolean {
        val intent = Intent(this, target).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        startActivity(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0) // no animation between tabs
        }
        finish()
        return true
    }
}
