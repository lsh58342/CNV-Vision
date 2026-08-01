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
 * Screen 2 — Building select (WA1 / WA2 / WA3).
 */
class BuildingSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_list)
        FactorySeedData.ensureSeeded()

        findViewById<TextView>(R.id.select_title).setText(R.string.nav_building_title)
        findViewById<TextView>(R.id.select_subtitle).text =
            CurrentContext.get().summary()

        val buildings = FactoryCatalog.get().buildings.listForCurrentFactory()
        val labels = buildings.map { it.name }
        val list = findViewById<ListView>(R.id.select_list)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.setOnItemClickListener { _, _, position, _ ->
            CurrentContext.get().selectBuilding(buildings[position].id)
            AppNavigator.openFloorSelect(this)
        }

        findViewById<MaterialButton>(R.id.button_select_settings).setOnClickListener {
            AppNavigator.openSettings(this)
        }
        findViewById<MaterialButton>(R.id.button_select_back).setOnClickListener { finish() }
    }
}
