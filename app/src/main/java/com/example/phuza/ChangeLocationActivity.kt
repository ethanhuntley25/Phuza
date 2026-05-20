package com.example.phuza

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.phuza.databinding.ActivityChangeLocationBinding


class ChangeLocationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangeLocationBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangeLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.closeBtn.setOnClickListener { finish() }

        if (savedInstanceState == null){
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, ChangeLocationFragment())
                .commit()
        }
    }
}