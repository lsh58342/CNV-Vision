package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.cnv.R
import com.google.android.material.button.MaterialButton

/**
 * Developer skeleton — hidden from Operator via Settings gate.
 * No Debug HUD wiring in UI-3.
 */
class DeveloperFragment : BaseScreenFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_developer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        siteVm.refreshGates()
        if (siteVm.canOpenDeveloper.value != true) {
            Toast.makeText(requireContext(), R.string.comm_denied, Toast.LENGTH_SHORT).show()
            nav().navigateBack()
            return
        }
        view.findViewById<MaterialButton>(R.id.button_developer_back)
            .setOnClickListener { nav().navigateBack() }
    }
}
