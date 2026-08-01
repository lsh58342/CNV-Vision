package com.example.cnv.ui.commissioning

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cnv.R
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.seed.FactorySeedData
import com.example.cnv.ui.navigation.AppNavigator
import com.google.android.material.button.MaterialButton

/**
 * Commissioning home — Admin/Developer only.
 * Workflow: DWG → Route → Calibration → Zone → Save
 */
class CommissioningHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_commissioning_home)
        FactorySeedData.ensureSeeded()

        findViewById<TextView>(R.id.commissioning_context).text =
            CurrentContext.get().summary()

        findViewById<MaterialButton>(R.id.button_comm_dwg).setOnClickListener {
            Toast.makeText(this, R.string.comm_dwg_structure, Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.button_comm_route).setOnClickListener {
            Toast.makeText(this, R.string.comm_route_structure, Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.button_comm_calibration).setOnClickListener {
            Toast.makeText(this, R.string.comm_calibration_structure, Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.button_comm_zone_editor).setOnClickListener {
            if (!AppNavigator.openZoneEditor(this)) {
                Toast.makeText(this, R.string.comm_denied, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.button_comm_leave).setOnClickListener {
            AppNavigator.leaveCommissioning(this)
        }
    }
}
