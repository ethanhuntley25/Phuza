package com.example.phuza

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.phuza.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Nav
        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Email/password login
        binding.btnLogin.setOnClickListener { attemptEmailLogin() }
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptEmailLogin(); true } else false
        }

        // Google SSO
        binding.btnGoogleLogin.setOnClickListener { signInWithGoogle() }
    }

    override fun onStart() {
        super.onStart()
        auth.currentUser?.let { routeBasedOnOnboarding(it.uid) }
    }

    // ---------------- Email/Password ----------------
    private fun attemptEmailLogin() {
        clearErrors()

        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (!isEmailValid(email)) {
            binding.emailLayout.error = "Enter a valid email"
            hasError = true
        }
        if (password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            hasError = true
        }
        if (hasError) return

        showLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser!!.uid
                    routeBasedOnOnboarding(uid)
                } else {
                    showLoading(false)
                    val msg = when (val e = task.exception) {
                        is FirebaseAuthInvalidUserException -> "No account found for this email."
                        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
                        else -> e?.localizedMessage ?: "Login failed. Try again."
                    }
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                }
            }
    }

    // ---------------- Google SSO ----------------
    private fun signInWithGoogle() {
        val serverClientId = getString(R.string.default_web_client_id)

        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val cm = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                val result = cm.getCredential(this@LoginActivity, request)
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                when (e) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException -> { /* user canceled */ }
                    else -> {
                        Snackbar.make(binding.root, "Google sign-in failed (${e::class.java.simpleName})", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun handleGoogleCredential(credential: Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
            showLoading(true)
            auth.signInWithCredential(firebaseCred)
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        showLoading(false)
                        Snackbar.make(
                            binding.root,
                            task.exception?.localizedMessage ?: "Google sign-in failed.",
                            Snackbar.LENGTH_LONG
                        ).show()
                        return@addOnCompleteListener
                    }
                    ensureUserProfileThenRoute()
                }
        } else {
            Snackbar.make(binding.root, "Selected credential is not a Google account.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun ensureUserProfileThenRoute() {
        val fUser = auth.currentUser ?: run { showLoading(false); return }
        val uid = fUser.uid
        val email = fUser.email.orEmpty()
        val displayName = fUser.displayName.orEmpty()

        val ref = db.child("users").child(uid)
        ref.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                routeBasedOnOnboarding(uid)
                return@addOnSuccessListener
            }

            val firstName = displayName.trim().takeIf { it.isNotEmpty() }?.substringBefore(' ')
                ?: email.substringBefore('@')
            val username = email.substringBefore('@').lowercase()

            val updates = hashMapOf<String, Any>(
                "users/$uid/uid" to uid,
                "users/$uid/firstName" to firstName,
                "users/$uid/username" to username,
                "users/$uid/email" to email,
                "users/$uid/createdAt" to ServerValue.TIMESTAMP,
                "users/$uid/onboardingComplete" to false
            )

            db.updateChildren(updates)
                .addOnSuccessListener {
                    showLoading(false)
                    routeToOnboarding()
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Snackbar.make(binding.root, e.localizedMessage ?: "Failed to save profile.", Snackbar.LENGTH_LONG).show()
                }
        }.addOnFailureListener { e ->
            showLoading(false)
            Snackbar.make(binding.root, e.localizedMessage ?: "Could not verify account.", Snackbar.LENGTH_LONG).show()
        }
    }

    // ---------------- Routing helpers ----------------
    private fun routeBasedOnOnboarding(uid: String) {
        db.child("users").child(uid).child("onboardingComplete").get()
            .addOnSuccessListener { snap ->
                showLoading(false)
                val done = snap.getValue(Boolean::class.java) == true
                if (done) routeToDashboard() else routeToOnboarding()
            }
            .addOnFailureListener {
                showLoading(false)
                routeToOnboarding()
            }
    }

    private fun routeToOnboarding() {
        startActivity(Intent(this, Onboarding1Activity::class.java))
        finish()
        overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom)
    }

    private fun routeToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
        overridePendingTransition(R.anim.fade_in_bottom, R.anim.fade_out_bottom)
    }

    // ---------------- UI helpers ----------------
    private fun isEmailValid(email: String?): Boolean =
        !email.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun showLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Logging in…" else "Login"
        binding.btnGoogleLogin.isEnabled = !loading
    }

    private fun clearErrors() {
        binding.emailLayout.error = null
        binding.passwordLayout.error = null
    }
}
