package com.example.cnv.ui.screen.developer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import com.example.cnv.R
import com.example.cnv.factory.context.AccessRole
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/** Developer — Route Unlock (Developer role only) + Commissioning entry. */
class DeveloperScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_developer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.setRole(AccessRole.DEVELOPER)
        view.findViewById<Button>(R.id.button_unlock_route).setOnClickListener {
            if (siteVm.unlockRouteForCurrentDrawing()) {
                Toast.makeText(requireContext(), R.string.setup_route_unlocked, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.setup_unlock_failed, Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<Button>(R.id.button_coord_validation).setOnClickListener {
            nav().navigate(CnvDestination.COORDINATE_VALIDATION)
        }
        view.findViewById<Button>(R.id.button_screen_next).setOnClickListener {
            if (!siteVm.enterCommissioningMode()) {
                Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nav().navigate(CnvDestination.DRAWING_WORKSPACE)
        }
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener { nav().navigateBack() }
    }
}
