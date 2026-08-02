package com.example.cnv

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.navigation.NavHost
import com.example.cnv.ui.navigation.ScreenNavigator

/**
 * Navigation Host only (UI Rebuild).
 * No Camera / HeatMap / Debug / Inspection UI in this Activity.
 */
class MainActivity : AppCompatActivity(), NavHost {

    private lateinit var navigator: ScreenNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_nav_host)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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

    override fun navigate(
        to: CnvDestination,
        addToBackStack: Boolean,
        args: android.os.Bundle?,
    ) {
        navigator.navigate(to, addToBackStack, args)
    }

    override fun navigateBack(): Boolean = navigator.navigateBack()

    override fun navigateClearTo(to: CnvDestination) {
        navigator.navigateClearTo(to)
    }
}
