package com.example.cnv.ui.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.cnv.R
import com.example.cnv.ui.feature.FeatureRuntime
import com.example.cnv.ui.feature.requireFeatureRuntime
import com.google.android.material.button.MaterialButton

/**
 * Developer Screen — debug HUDs only (hidden from Operation flow).
 */
class DeveloperFragment : BaseScreenFragment() {

    private var features: FeatureRuntime? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_developer, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val runtime = requireActivity().requireFeatureRuntime()
        features = runtime
        runtime.attachDeveloper(view)
        view.findViewById<MaterialButton>(R.id.button_developer_back)
            .setOnClickListener { nav().navigateBack() }
    }

    override fun onDestroyView() {
        features?.detachDeveloper()
        features = null
        super.onDestroyView()
    }
}
