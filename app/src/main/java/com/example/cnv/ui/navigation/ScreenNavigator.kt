package com.example.cnv.ui.navigation

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.example.cnv.R

/**
 * Fragment back-stack navigator hosted by [MainActivity].
 * No business / sensor logic.
 */
class ScreenNavigator(
    private val activity: FragmentActivity,
    private val containerId: Int = R.id.nav_host_container,
) : NavHost {

    private val fm: FragmentManager get() = activity.supportFragmentManager

    override fun navigate(to: CnvDestination, addToBackStack: Boolean) {
        val tx = fm.beginTransaction()
            .setReorderingAllowed(true)
            .replace(containerId, to.createFragment(), to.name)
        if (addToBackStack) {
            tx.addToBackStack(to.name)
        }
        tx.commit()
    }

    override fun navigateBack(): Boolean {
        if (fm.backStackEntryCount <= 0) return false
        fm.popBackStack()
        return true
    }

    override fun navigateClearTo(to: CnvDestination) {
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fm.beginTransaction()
            .setReorderingAllowed(true)
            .replace(containerId, to.createFragment(), to.name)
            .commit()
    }
}

fun FragmentActivity.requireNavHost(): NavHost {
    val host = this as? NavHost
        ?: error("Activity must implement NavHost")
    return host
}
