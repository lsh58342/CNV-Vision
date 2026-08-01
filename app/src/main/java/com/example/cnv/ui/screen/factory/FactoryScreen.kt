package com.example.cnv.ui.screen.factory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/** Removed — LGES Poland only; no Factory select. */
@Deprecated("Factory select removed")
class FactoryScreen : BaseScreen() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = View(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nav().navigateClearTo(CnvDestination.BUILDING_SELECT)
    }
}
