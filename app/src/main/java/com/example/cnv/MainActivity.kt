package com.example.cnv

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.ui.navigation.AppNavigator

/**
 * Inspection UI shell only. Feature wiring lives in [MainCompositionRoot].
 * Launched from Zone Dashboard with optional zone id (no algorithm changes).
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
        intent.getStringExtra(AppNavigator.EXTRA_ZONE_ID)?.let { zoneId ->
            CurrentContext.get().selectZone(zoneId)
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
