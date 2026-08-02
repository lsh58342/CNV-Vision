package com.example.cnv.ui.navigation

/**
 * Host-only navigation API. Screens never own Activity transitions.
 */
interface NavHost {
    fun navigate(
        to: CnvDestination,
        addToBackStack: Boolean = true,
        args: android.os.Bundle? = null,
    )

    fun navigateBack(): Boolean
    fun navigateClearTo(to: CnvDestination)
}
