package com.example.phuza

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.phuza.data.User
import com.example.phuza.databinding.ActivityRegisterBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    private val tag = "RegisterActivity"

    // Firebase (default instance from google-services.json)
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val dbRoot: DatabaseReference by lazy { FirebaseDatabase.getInstance().reference }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvLoginLink.setOnClickListener { finish() }
        binding.btnRegister.setOnClickListener { attemptRegister() }
    }

    //Method that validates and stores the users registration details
    private fun attemptRegister() {
        clearErrors()

        val first = binding.etFirstName.text?.toString()?.trim().orEmpty()
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val pass = binding.etPassword.text?.toString()?.trim().orEmpty()
        //val pass2 = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()

        var ok = true
        if (first.isEmpty()) { binding.tilFirst.error = "Required"; ok = false }
        if (username.isEmpty()) { binding.tilUser.error = "Required"; ok = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.tilEmail.error = "Enter a valid email"; ok = false }
        if (pass.length < 6) { binding.tilPassword.error = "Min 6 characters"; ok = false }
        if (!ok) return

        setLoading(loading = true)

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid
                if (uid.isNullOrEmpty()) {
                    setLoading(false)
                    snack("Registration failed: no UID.")
                    Log.e(tag, "UID missing after createUser")
                    return@addOnSuccessListener
                }

                writeProfile(uid, first, username, email)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                val msg = when (e) {
                    is FirebaseAuthUserCollisionException -> "An account with this email already exists."
                    is FirebaseAuthWeakPasswordException -> "Password is too weak."
                    else -> e.localizedMessage ?: "Registration failed."
                }
                snack(msg)
                Log.e(tag, "Auth createUser failed", e)
            }
    }

    private fun writeProfile(
        uid: String,
        firstName: String,
        username: String,
        email: String,
    ) {
        // Client object
        val profile = User(
            uid = uid,
            firstName = firstName,
            username = username,
            email = email,
            createdAt = 0L
        )

        val updates = hashMapOf(
            "users/$uid/uid" to profile.uid,
            "users/$uid/firstName" to profile.firstName,
            "users/$uid/username" to profile.username,
            "users/$uid/email" to profile.email,
            "users/$uid/createdAt" to ServerValue.TIMESTAMP,
            "users/$uid/onboardingComplete" to false
        )

        dbRoot.updateChildren(updates)
            .addOnSuccessListener {
                setLoading(false)
                goToHome()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                snack(e.localizedMessage ?: "Failed saving profile.")
                Log.e(tag, "Profile write failed at /users/$uid", e)
                auth.currentUser?.delete()
            }
    }

    private fun clearErrors() {
        binding.tilFirst.error = null
        binding.tilUser.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
    }

    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnRegister.text = if (loading) "Creating…" else "Lets Phuza"
    }

    private fun snack(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
    }

    private fun goToHome() {
        startActivity(Intent(this, Onboarding1Activity::class.java))
        finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
