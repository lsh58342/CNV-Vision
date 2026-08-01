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
 * Screen 1 — Factory select (Operation entry).
 */
class FactorySelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_list)
        FactorySeedData.ensureSeeded()

        findViewById<TextView>(R.id.select_title).setText(R.string.nav_factory_title)
        findViewById<TextView>(R.id.select_subtitle).text =
            CurrentContext.get().summary()

        val factories = FactoryCatalog.get().factories.all()
        val labels = factories.map { it.name }
        val list = findViewById<ListView>(R.id.select_list)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.setOnItemClickListener { _, _, position, _ ->
            CurrentContext.get().selectFactory(factories[position].id)
            AppNavigator.openBuildingSelect(this)
        }

        findViewById<MaterialButton>(R.id.button_select_settings).setOnClickListener {
            AppNavigator.openSettings(this)
        }
        findViewById<MaterialButton>(R.id.button_select_back).apply {
            text = getString(R.string.nav_exit_hint)
            setOnClickListener { finish() }
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.select_subtitle).text = CurrentContext.get().summary()
    }
}
