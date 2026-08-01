package com.example.cnv.ui.legacy.feature

import androidx.fragment.app.FragmentActivity

/**
 * Legacy FeatureRuntime access — isolated in UI Rebuild Phase 1.
 * Rebuild MainActivity is Navigation Host only and does not host FeatureRuntime.
 */
fun FragmentActivity.requireFeatureRuntime(): FeatureRuntime {
    error(
        "Legacy FeatureRuntime is isolated; rebuild MainActivity does not host it (UI Rebuild Phase 1)",
    )
}
