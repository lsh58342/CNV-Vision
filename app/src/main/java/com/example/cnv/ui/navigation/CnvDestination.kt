package com.example.cnv.ui.navigation

import androidx.fragment.app.Fragment
import com.example.cnv.ui.screen.building.BuildingScreen
import com.example.cnv.ui.screen.dashboard.ZoneDashboardScreen
import com.example.cnv.ui.screen.developer.DeveloperScreen
import com.example.cnv.ui.screen.drawing.DrawingWorkspaceScreen
import com.example.cnv.ui.screen.floor.FloorScreen
import com.example.cnv.ui.screen.heatmap.HeatMapScreen
import com.example.cnv.ui.screen.history.HistoryScreen
import com.example.cnv.ui.screen.inspection.InspectionScreen
import com.example.cnv.ui.screen.result.InspectionResultScreen
import com.example.cnv.ui.screen.settings.SettingsScreen
import com.example.cnv.ui.screen.splash.SplashScreen
import com.example.cnv.ui.screen.zone.ZoneListScreen

/**
 * App destinations — Drawing Workspace is the Drawing management hub.
 */
enum class CnvDestination {
    SPLASH,
    @Deprecated("Factory select removed — LGES Poland only")
    FACTORY_SELECT,
    BUILDING_SELECT,
    FLOOR_SELECT,
    DRAWING_WORKSPACE,
    @Deprecated("Merged into FloorScreen")
    DRAWING_LIST,
    @Deprecated("Merged into DrawingWorkspaceScreen")
    DRAWING_DASHBOARD,
    @Deprecated("Merged into DrawingWorkspaceScreen")
    OPEN_DRAWING,
    ZONE_LIST,
    ZONE_DASHBOARD,
    INSPECTION,
    INSPECTION_RESULT,
    HEATMAP_VIEWER,
    INSPECTION_HISTORY,
    SETTINGS,
    DEVELOPER,
    COORDINATE_VALIDATION,
    @Deprecated("Commissioning lives in Drawing Workspace tab")
    COMMISSIONING,
    ;

    fun createFragment(): Fragment = when (this) {
        SPLASH -> SplashScreen()
        FACTORY_SELECT -> BuildingScreen()
        BUILDING_SELECT -> BuildingScreen()
        FLOOR_SELECT -> FloorScreen()
        DRAWING_WORKSPACE -> DrawingWorkspaceScreen()
        DRAWING_LIST -> FloorScreen()
        DRAWING_DASHBOARD -> DrawingWorkspaceScreen()
        OPEN_DRAWING -> DrawingWorkspaceScreen()
        ZONE_LIST -> ZoneListScreen()
        ZONE_DASHBOARD -> ZoneDashboardScreen()
        INSPECTION -> InspectionScreen()
        INSPECTION_RESULT -> InspectionResultScreen()
        HEATMAP_VIEWER -> HeatMapScreen()
        INSPECTION_HISTORY -> HistoryScreen()
        SETTINGS -> SettingsScreen()
        DEVELOPER -> DeveloperScreen()
        COORDINATE_VALIDATION -> com.example.cnv.ui.screen.developer.CoordinateValidationScreen()
        COMMISSIONING -> DrawingWorkspaceScreen()
    }
}
