package com.example.cnv.ui.operation

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.seed.FactorySeedData
import com.example.cnv.ui.navigation.AppNavigator
import com.google.android.material.button.MaterialButton

/**
 * Screen 4 — Zone list for current Floor.
 */
class ZoneListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_list)
        FactorySeedData.ensureSeeded()

        findViewById<TextView>(R.id.select_title).setText(R.string.nav_zone_list_title)
        findViewById<TextView>(R.id.select_subtitle).text =
            CurrentContext.get().summary()

        val zones = FactoryCatalog.get().zones.listForCurrentFloor()
        val labels = zones.map { "${it.name} (${it.colorLabel})" }
        val list = findViewById<ListView>(R.id.select_list)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.setOnItemClickListener { _, _, position, _ ->
            AppNavigator.openZoneDashboard(this, zones[position].id)
        }

        findViewById<MaterialButton>(R.id.button_select_settings).setOnClickListener {
            AppNavigator.openSettings(this)
        }
        findViewById<MaterialButton>(R.id.button_select_back).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        val zones = FactoryCatalog.get().zones.listForCurrentFloor()
        val labels = zones.map { "${it.name} (${it.colorLabel})" }
        findViewById<ListView>(R.id.select_list).adapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
    }
}
