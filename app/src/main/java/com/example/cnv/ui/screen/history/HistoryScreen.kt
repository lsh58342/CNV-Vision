package com.example.cnv.ui.screen.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.example.cnv.R
import com.example.cnv.ui.navigation.CnvDestination
import com.example.cnv.ui.screen.BaseScreen

/** History — UI Rebuild Phase 1 skeleton (no feature wiring). */
class HistoryScreen : BaseScreen() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_screen_next).setOnClickListener {
            nav().navigate(CnvDestination.SETTINGS)
        }
        view.findViewById<Button>(R.id.button_screen_back).setOnClickListener { nav().navigateBack() }
    }
}
