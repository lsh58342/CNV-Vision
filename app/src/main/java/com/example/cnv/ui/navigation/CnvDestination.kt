package com.example.cnv.ui.navigation

import androidx.fragment.app.Fragment
import com.example.cnv.ui.screen.building.BuildingScreen
import com.example.cnv.ui.screen.commissioning.CommissioningScreen
import com.example.cnv.ui.screen.dashboard.ZoneDashboardScreen
import com.example.cnv.ui.screen.developer.DeveloperScreen
import com.example.cnv.ui.screen.factory.FactoryScreen
import com.example.cnv.ui.screen.floor.FloorScreen
import com.example.cnv.ui.screen.heatmap.HeatMapScreen
import com.example.cnv.ui.screen.history.HistoryScreen
import com.example.cnv.ui.screen.inspection.InspectionScreen
import com.example.cnv.ui.screen.result.InspectionResultScreen
import com.example.cnv.ui.screen.settings.SettingsScreen
import com.example.cnv.ui.screen.splash.SplashScreen
import com.example.cnv.ui.screen.zone.ZoneListScreen

/**
 * App destinations for UI Rebuild Navigation Host.
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
        SPLASH -> SplashScreen()
        FACTORY_SELECT -> FactoryScreen()
        BUILDING_SELECT -> BuildingScreen()
        FLOOR_SELECT -> FloorScreen()
        ZONE_LIST -> ZoneListScreen()
        ZONE_DASHBOARD -> ZoneDashboardScreen()
        INSPECTION -> InspectionScreen()
        INSPECTION_RESULT -> InspectionResultScreen()
        HEATMAP_VIEWER -> HeatMapScreen()
        INSPECTION_HISTORY -> HistoryScreen()
        SETTINGS -> SettingsScreen()
        DEVELOPER -> DeveloperScreen()
        COMMISSIONING -> CommissioningScreen()
    }
}
