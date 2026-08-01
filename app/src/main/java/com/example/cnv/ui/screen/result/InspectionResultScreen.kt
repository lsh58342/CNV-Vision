package com.example.cnv.ui.screen.result

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.cnv.R
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/**
 * Inspection Result — Phase 3: navigation shell only (summary in a later phase).
 */
class InspectionResultScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_result, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.screen_title).text = getString(R.string.screen_result)
        view.findViewById<TextView>(R.id.screen_body).text =
            getString(R.string.insp_result_placeholder)

        view.findViewById<Button>(R.id.button_screen_next).visibility = View.GONE
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener {
            nav().navigateClearTo(CnvDestination.ZONE_DASHBOARD)
        }
    }
}
