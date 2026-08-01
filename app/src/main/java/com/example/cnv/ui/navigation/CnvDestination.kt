package com.example.cnv.ui.navigation

import androidx.fragment.app.Fragment
import com.example.cnv.ui.screens.BuildingSelectFragment
import com.example.cnv.ui.screens.CommissioningFragment
import com.example.cnv.ui.screens.DeveloperFragment
import com.example.cnv.ui.screens.FactorySelectFragment
import com.example.cnv.ui.screens.FloorSelectFragment
import com.example.cnv.ui.screens.HeatMapViewerFragment
import com.example.cnv.ui.screens.InspectionFragment
import com.example.cnv.ui.screens.InspectionHistoryFragment
import com.example.cnv.ui.screens.InspectionResultFragment
import com.example.cnv.ui.screens.SettingsFragment
import com.example.cnv.ui.screens.SplashFragment
import com.example.cnv.ui.screens.ZoneDashboardFragment
import com.example.cnv.ui.screens.ZoneListFragment

/**
 * App destinations for STEP UI-3 Navigation Skeleton.
 */
enum class CnvDestination {
    SPLASH,
    FACTORY_SELECT,
    BUILDING_SELECT,
    FLOOR_SELECT,
    ZONE_LIST,
    ZONE_DASHBOARD,
    INSPECTION,
    INSPECTION_RESULT,
    HEATMAP_VIEWER,
    INSPECTION_HISTORY,
    SETTINGS,
    DEVELOPER,
    COMMISSIONING,
    ;

    fun createFragment(): Fragment = when (this) {
        SPLASH -> SplashFragment()
        FACTORY_SELECT -> FactorySelectFragment()
        BUILDING_SELECT -> BuildingSelectFragment()
        FLOOR_SELECT -> FloorSelectFragment()
        ZONE_LIST -> ZoneListFragment()
        ZONE_DASHBOARD -> ZoneDashboardFragment()
        INSPECTION -> InspectionFragment()
        INSPECTION_RESULT -> InspectionResultFragment()
        HEATMAP_VIEWER -> HeatMapViewerFragment()
        INSPECTION_HISTORY -> InspectionHistoryFragment()
        SETTINGS -> SettingsFragment()
        DEVELOPER -> DeveloperFragment()
        COMMISSIONING -> CommissioningFragment()
    }
}
