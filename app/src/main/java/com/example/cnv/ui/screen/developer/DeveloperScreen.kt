package com.example.cnv.ui.screen.developer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.example.cnv.R
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/** Developer — UI Rebuild Phase 1 skeleton (no feature wiring). */
class DeveloperScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_developer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_screen_next).setOnClickListener {
            nav().navigate(CnvDestination.COMMISSIONING)
        }
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener { nav().navigateBack() }
    }
}
