package com.example.phuza

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnReset: MaterialButton

    private lateinit var successCard: CardView

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tilEmail = findViewById(R.id.tilEmail)
        etEmail  = findViewById(R.id.etEmail)
        btnReset = findViewById(R.id.btnReset)
        successCard = findViewById(R.id.successCard)

        // Prefill email if passed
        intent.getStringExtra("email")?.let { etEmail.setText(it) }

        etEmail.addTextChangedListener {
            val email = it?.toString()?.trim()
            if (isValidEmail(email)) {
                tilEmail.error = null
            }
        }

        btnReset.setOnClickListener {
            val email = etEmail.text?.toString()?.trim().orEmpty()

            if (email.isBlank()) {
                tilEmail.error = "Email is required"
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                tilEmail.error = "Enter a valid email"
                return@setOnClickListener
            }

            hideKeyboard()

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        tilEmail.visibility = View.GONE
                        etEmail.visibility = View.GONE
                        btnReset.visibility = View.GONE
                        findViewById<TextView>(R.id.tvHelper).visibility = View.GONE
                        findViewById<TextView>(R.id.tvTitle).visibility = View.GONE
                        findViewById<ImageView>(R.id.logo_yellow).visibility = View.GONE

                        successCard.visibility = View.VISIBLE

                        // Auto return to login after 2s
                        Handler(Looper.getMainLooper()).postDelayed({
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }, 2000)

                    } else {
                        val ex = task.exception
                        Log.e("ForgotPassword", "Reset error", ex)
                        tilEmail.error = when (ex) {
                            is FirebaseAuthInvalidUserException ->
                                "No account found with that email"
                            is FirebaseTooManyRequestsException ->
                                "Too many requests. Please try again later"
                            else -> ex?.localizedMessage ?: "Failed to send reset link"
                        }
                    }
                }
        }
    }


    private fun isValidEmail(value: String?) =
        !value.isNullOrBlank() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun hideKeyboard() {
        currentFocus?.let { v ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(v.windowToken, 0)
        }
    }
}
