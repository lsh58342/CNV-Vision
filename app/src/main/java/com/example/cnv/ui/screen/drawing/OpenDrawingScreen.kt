package com.example.cnv.ui.screen.drawing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/** Removed — Commissioning CAD lives in Drawing Workspace Commissioning tab. */
@Deprecated("Merged into DrawingWorkspaceScreen")
class OpenDrawingScreen : BaseScreen() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = View(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        nav().navigateClearTo(CnvDestination.DRAWING_WORKSPACE)
    }
}
