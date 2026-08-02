package com.example.cnv.ui.screen.inspection

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.cnv.R
import java.util.Locale

/**
 * Binds [LiveInspectionDashboardState] to Live Dashboard views (STEP 20-1).
 */
class LiveInspectionDashboardBinder(root: View) {

    private val headerBuilding: TextView = root.findViewById(R.id.live_dash_building)
    private val headerFloor: TextView = root.findViewById(R.id.live_dash_floor)
    private val headerDrawing: TextView = root.findViewById(R.id.live_dash_drawing)
    private val headerTime: TextView = root.findViewById(R.id.live_dash_inspection_time)
    private val headerElapsed: TextView = root.findViewById(R.id.live_dash_elapsed)

    private val zone: TextView = root.findViewById(R.id.live_dash_zone)
    private val routeMm: TextView = root.findViewById(R.id.live_dash_route_mm)
    private val coord: TextView = root.findViewById(R.id.live_dash_coord)
    private val speed: TextView = root.findViewById(R.id.live_dash_speed)
    private val nominal: TextView = root.findViewById(R.id.live_dash_nominal)
    private val speedDiff: TextView = root.findViewById(R.id.live_dash_speed_diff)
    private val shock: TextView = root.findViewById(R.id.live_dash_shock)
    private val maxShock: TextView = root.findViewById(R.id.live_dash_max_shock)
    private val avgShock: TextView = root.findViewById(R.id.live_dash_avg_shock)
    private val tracking: TextView = root.findViewById(R.id.live_dash_tracking)
    private val coverage: TextView = root.findViewById(R.id.live_dash_coverage)
    private val validation: TextView = root.findViewById(R.id.live_dash_validation)

    private val stCamera: TextView = root.findViewById(R.id.live_dash_st_camera)
    private val stOpenCv: TextView = root.findViewById(R.id.live_dash_st_opencv)
    private val stTracking: TextView = root.findViewById(R.id.live_dash_st_tracking)
    private val stFusion: TextView = root.findViewById(R.id.live_dash_st_fusion)
    private val stReplay: TextView = root.findViewById(R.id.live_dash_st_replay)
    private val stRoom: TextView = root.findViewById(R.id.live_dash_st_room)

    private val warningPanel: LinearLayout = root.findViewById(R.id.live_dash_warning_panel)
    private val warningList: TextView = root.findViewById(R.id.live_dash_warning_list)

    fun bind(state: LiveInspectionDashboardState) {
        val ctx = headerBuilding.context
        headerBuilding.text = ctx.getString(R.string.live_dash_building_fmt, state.buildingName)
        headerFloor.text = ctx.getString(R.string.live_dash_floor_fmt, state.floorName)
        headerDrawing.text = ctx.getString(R.string.live_dash_drawing_fmt, state.drawingName)
        headerTime.text = ctx.getString(R.string.live_dash_time_fmt, state.inspectionTimeLabel)
        headerElapsed.text = ctx.getString(R.string.live_dash_elapsed_fmt, state.elapsedSec)

        zone.text = ctx.getString(R.string.live_dash_zone_fmt, state.currentZoneName)
        routeMm.text = ctx.getString(R.string.live_dash_route_mm_fmt, state.routePositionMm)
        coord.text = if (state.coordinateX != null && state.coordinateY != null) {
            ctx.getString(R.string.live_dash_coord_fmt, state.coordinateX, state.coordinateY)
        } else {
            ctx.getString(R.string.live_dash_coord_na)
        }
        speed.text = speedLine(ctx.getString(R.string.live_dash_speed_label), state.currentSpeedMPerMin)
        nominal.text = speedLine(ctx.getString(R.string.live_dash_nominal_label), state.nominalSpeedMPerMin)
        speedDiff.text = speedLine(ctx.getString(R.string.live_dash_speed_diff_label), state.speedDifferenceMPerMin)
        shock.text = ctx.getString(R.string.live_dash_shock_fmt, state.currentShock)
        maxShock.text = ctx.getString(R.string.live_dash_max_shock_fmt, state.maximumShock)
        avgShock.text = ctx.getString(R.string.live_dash_avg_shock_fmt, state.averageShock)
        tracking.text = ctx.getString(
            R.string.live_dash_tracking_fmt,
            state.trackingLabel,
            state.trackingConfidence,
        )
        coverage.text = ctx.getString(R.string.live_dash_coverage_fmt, state.coverage * 100f)
        validation.text = ctx.getString(R.string.live_dash_validation_fmt, state.validationScore)

        stCamera.text = moduleLine(ctx.getString(R.string.live_dash_mod_camera), state.system.camera)
        stOpenCv.text = moduleLine(ctx.getString(R.string.live_dash_mod_opencv), state.system.openCv)
        stTracking.text = moduleLine(ctx.getString(R.string.live_dash_mod_tracking), state.system.tracking)
        stFusion.text = moduleLine(ctx.getString(R.string.live_dash_mod_fusion), state.system.fusion)
        stReplay.text = moduleLine(ctx.getString(R.string.live_dash_mod_replay), state.system.replay)
        stRoom.text = moduleLine(ctx.getString(R.string.live_dash_mod_room), state.system.room)

        if (state.warnings.isEmpty()) {
            warningPanel.isVisible = false
        } else {
            warningPanel.isVisible = true
            warningList.text = state.warnings.joinToString("\n") { "• ${it.title}: ${it.detail}" }
        }
    }

    private fun speedLine(label: String, value: Float?): String =
        if (value == null) "$label —"
        else String.format(Locale.US, "%s %.2f m/min", label, value)

    private fun moduleLine(name: String, state: SystemModuleState): String =
        "$name: ${state.name}"
}
