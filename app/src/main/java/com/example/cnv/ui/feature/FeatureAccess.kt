package com.example.cnv.ui.feature

import androidx.fragment.app.FragmentActivity
import com.example.cnv.MainActivity

fun FragmentActivity.requireFeatureRuntime(): FeatureRuntime {
    val main = this as? MainActivity
        ?: error("FeatureRuntime requires MainActivity host")
    return main.featureRuntime()
}
