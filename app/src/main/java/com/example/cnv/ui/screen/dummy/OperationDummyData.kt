package com.example.cnv.ui.screen.dummy

/**
 * UI-only dummy data for Operation screens (Phase 2).
 * Not connected to Repository / Core.
 */
object OperationDummyData {

    data class FactoryItem(
        val id: String,
        val name: String,
        val location: String,
    )

    data class BuildingItem(
        val id: String,
        val name: String,
        val factoryId: String,
    )

    data class FloorItem(
        val id: String,
        val name: String,
        val buildingId: String,
    )

    data class ZoneItem(
        val id: String,
        val name: String,
        val colorHex: String,
        val lastInspection: String,
        val floorId: String,
    )

    data class DashboardDummy(
        val zoneName: String,
        val routeStatus: String,
        val dwgStatus: String,
        val calibrationStatus: String,
        val lastInspection: String,
        val historyCount: Int,
        val heatMapCount: Int,
    )

    val factories: List<FactoryItem> = listOf(
        FactoryItem("f_lges_pl", "LGES Poland", "Wroclaw, Poland"),
        FactoryItem("f_demo_kr", "CNV Demo Korea", "Seoul, Korea"),
        FactoryItem("f_demo_us", "CNV Demo USA", "Michigan, USA"),
    )

    val recentFactoryId: String = "f_lges_pl"

    val buildings: List<BuildingItem> = listOf(
        BuildingItem("b_wa1", "WA1", "f_lges_pl"),
        BuildingItem("b_wa2", "WA2", "f_lges_pl"),
        BuildingItem("b_wa3", "WA3", "f_lges_pl"),
    )

    val floors: List<FloorItem> = listOf(
        FloorItem("fl_1f", "1F", "b_wa1"),
        FloorItem("fl_2f", "2F", "b_wa1"),
        FloorItem("fl_3f", "3F", "b_wa1"),
    )

    val zones: List<ZoneItem> = listOf(
        ZoneItem("z_formation", "Formation", "#42A5F5", "2026-08-02", "fl_2f"),
        ZoneItem("z_aging", "Aging", "#66BB6A", "2026-07-28", "fl_2f"),
        ZoneItem("z_packing", "Packing", "#FFA726", "2026-07-20", "fl_2f"),
        ZoneItem("z_test", "Test Line", "#EF5350", "—", "fl_2f"),
    )

    fun dashboardFor(zone: ZoneItem?): DashboardDummy {
        val z = zone ?: zones.first()
        return DashboardDummy(
            zoneName = z.name,
            routeStatus = "OK",
            dwgStatus = "OK",
            calibrationStatus = "OK",
            lastInspection = z.lastInspection,
            historyCount = 12,
            heatMapCount = 3,
        )
    }
}

/** In-memory UI selection for Operation navigation (not Repository). */
object OperationUiSelection {
    var factoryId: String? = OperationDummyData.recentFactoryId
    var buildingId: String? = "b_wa1"
    var floorId: String? = "fl_2f"
    var zoneId: String? = "z_formation"

    fun selectedFactory(): OperationDummyData.FactoryItem? =
        OperationDummyData.factories.firstOrNull { it.id == factoryId }

    fun selectedBuilding(): OperationDummyData.BuildingItem? =
        OperationDummyData.buildings.firstOrNull { it.id == buildingId }

    fun selectedFloor(): OperationDummyData.FloorItem? =
        OperationDummyData.floors.firstOrNull { it.id == floorId }

    fun selectedZone(): OperationDummyData.ZoneItem? =
        OperationDummyData.zones.firstOrNull { it.id == zoneId }
}
