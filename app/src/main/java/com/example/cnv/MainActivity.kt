package com.example.cnv

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * UI shell only. Feature wiring lives in [MainCompositionRoot].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var compositionRoot: MainCompositionRoot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        compositionRoot = MainCompositionRoot(this)
        compositionRoot.bind()
    }

    override fun onStart() {
        super.onStart()
        compositionRoot.onStart()
    }

    override fun onStop() {
        compositionRoot.onStop()
        super.onStop()
    }
}
