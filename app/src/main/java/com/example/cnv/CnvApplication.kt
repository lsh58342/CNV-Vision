package com.example.cnv

import android.app.Application
import com.example.cnv.inspection.db.CnvInspectionDatabase
import com.example.cnv.inspection.InspectionRepository

/**
 * Application entry — hosts Room Inspection database (STEP 13).
 */
class CnvApplication : Application() {

    lateinit var inspectionDatabase: CnvInspectionDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        inspectionDatabase = CnvInspectionDatabase.build(this)
        InspectionRepository.bindDatabase(inspectionDatabase)
        com.example.cnv.imu.ShockThresholdStore.load(this)
        com.example.cnv.camera.ShockClipStorage.bind(this)
        com.example.cnv.report.ReportStorage.bind(this)
        com.example.cnv.report.AutoReportSettingsStore.bind(this)
    }

    companion object {
        @Volatile
        private var instance: CnvApplication? = null

        fun get(): CnvApplication =
            instance ?: error("CnvApplication not initialized")

        fun inspectionDb(): CnvInspectionDatabase = get().inspectionDatabase
    }
}
