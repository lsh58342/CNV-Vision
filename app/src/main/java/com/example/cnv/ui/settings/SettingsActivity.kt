package com.example.cnv.ui.settings

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cnv.R
import com.example.cnv.factory.context.AccessRole
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.ui.navigation.AppNavigator
import com.google.android.material.button.MaterialButton

/**
 * Settings — Developer / Admin gates for Commissioning.
 * Operation users stay on OPERATOR role.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        refresh()

        findViewById<MaterialButton>(R.id.button_role_operator).setOnClickListener {
            CurrentContext.get().setAccessRole(AccessRole.OPERATOR)
            refresh()
        }
        findViewById<MaterialButton>(R.id.button_role_admin).setOnClickListener {
            CurrentContext.get().setAccessRole(AccessRole.ADMIN)
            refresh()
        }
        findViewById<MaterialButton>(R.id.button_role_developer).setOnClickListener {
            CurrentContext.get().setAccessRole(AccessRole.DEVELOPER)
            refresh()
        }
        findViewById<MaterialButton>(R.id.button_open_commissioning).setOnClickListener {
            if (!AppNavigator.openCommissioningHome(this)) {
                Toast.makeText(this, R.string.comm_denied, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.button_settings_back).setOnClickListener { finish() }
    }

    private fun refresh() {
        findViewById<TextView>(R.id.settings_status).text = CurrentContext.get().summary()
    }
}
