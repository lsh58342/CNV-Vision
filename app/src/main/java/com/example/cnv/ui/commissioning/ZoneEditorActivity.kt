package com.example.cnv.ui.commissioning

import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.RouteAnchor
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.seed.LgesPolandSite
import com.example.cnv.zone.editor.ZoneEditorController
import com.google.android.material.button.MaterialButton

/**
 * Zone Editor UI (Commissioning only) — requires Drawing + Route in Current Context.
 */
class ZoneEditorActivity : AppCompatActivity() {

    private val editor = ZoneEditorController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zone_editor)
        LgesPolandSite.ensure()

        if (!editor.isAccessible()) {
            Toast.makeText(this, R.string.comm_denied, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!hasDrawingAndRoute()) {
            Toast.makeText(this, R.string.zone_editor_need_context, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        refreshStatus()

        findViewById<MaterialButton>(R.id.button_zone_cad_start).setOnClickListener {
            if (!editor.beginCadCreation()) {
                Toast.makeText(this, R.string.zone_editor_need_context, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            editor.setCadStart(RouteAnchor(nodeId = "N-CAD-START"))
            editor.setCadEnd(RouteAnchor(nodeId = "N-CAD-END"))
            refreshStatus()
            Toast.makeText(this, R.string.zone_editor_cad_demo, Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.button_zone_drive_start).setOnClickListener {
            if (!editor.beginDriveRecording()) {
                Toast.makeText(this, R.string.zone_editor_need_context, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            editor.markDriveStart(
                RouteAnchor(segmentId = "S-DRIVE", distanceFromSegmentStartMm = 0f),
            )
            editor.markDriveEnd(
                RouteAnchor(segmentId = "S-DRIVE", progress = 1f),
            )
            refreshStatus()
            Toast.makeText(this, R.string.zone_editor_drive_demo, Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.button_zone_save).setOnClickListener {
            val name = findViewById<EditText>(R.id.zone_editor_name).text?.toString().orEmpty()
            editor.setName(name)
            editor.setColor("Orange", Color.parseColor("#FF9800"))
            val saved = editor.save()
            if (saved == null) {
                Toast.makeText(this, R.string.zone_editor_save_failed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.zone_editor_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
            refreshStatus()
        }

        findViewById<MaterialButton>(R.id.button_zone_editor_back).setOnClickListener { finish() }
    }

    private fun hasDrawingAndRoute(): Boolean {
        val ctx = CurrentContext.get()
        val catalog = FactoryCatalog.get()
        val drawing = catalog.drawings.current(ctx) ?: return false
        val routeId = drawing.routeId ?: ctx.routeId ?: catalog.routes.currentRouteId()
        if (routeId == null || !catalog.routes.hasRoute()) return false
        if (ctx.routeId == null) ctx.selectRoute(routeId)
        return true
    }

    private fun refreshStatus() {
        val d = editor.draft()
        findViewById<TextView>(R.id.zone_editor_status).text = buildString {
            appendLine("Mode: ${d.mode}")
            appendLine("Drawing: ${d.drawingId.ifBlank { "—" }}")
            appendLine("Route: ${d.routeId.ifBlank { "—" }}")
            appendLine("Start: ${d.start}")
            appendLine("End: ${d.end}")
            appendLine("CanSave: ${d.canSave()}")
        }
    }
}
