package com.example.cnv

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cnv.ui.feature.FeatureRuntime
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavHost
import com.example.cnv.ui.navigation.ScreenNavigator

/**
 * Navigation Host. Feature engines live in [FeatureRuntime]; screens attach UI.
 */
class MainActivity : AppCompatActivity(), NavHost {

    private lateinit var navigator: ScreenNavigator
    private lateinit var features: FeatureRuntime

    fun featureRuntime(): FeatureRuntime = features

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_nav_host)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        features = FeatureRuntime(this)
        navigator = ScreenNavigator(this)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!navigator.navigateBack()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )

        if (savedInstanceState == null) {
            navigator.navigate(CnvDestination.SPLASH, addToBackStack = false)
        }
    }

    override fun onDestroy() {
        if (::features.isInitialized) {
            features.releaseAll()
        }
        super.onDestroy()
    }

    override fun navigate(to: CnvDestination, addToBackStack: Boolean) {
        navigator.navigate(to, addToBackStack)
    }

    override fun navigateBack(): Boolean = navigator.navigateBack()

    override fun navigateClearTo(to: CnvDestination) {
        navigator.navigateClearTo(to)
    }
}
