package com.example.cnv.factory.repository

import com.example.cnv.core.common.AppDispatchers
import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.db.ConveyorProfileEntity
import com.example.cnv.inspection.db.InspectionDbGate

/**
 * Persists Drawing Conveyor Profiles via Room (STEP 15-4).
 * All Room I/O runs on [InspectionDbGate] background executor.
 */
class ConveyorProfileRepository {

    fun saveAsync(drawingId: String, profile: ConveyorProfile, onDone: (() -> Unit)? = null) {
        InspectionDbGate.execute {
            InspectionRepository.database()?.conveyorProfileDao()
                ?.upsert(ConveyorProfileEntity.from(drawingId, profile))
            if (onDone != null) {
                AppDispatchers.mainExecutor.execute(onDone)
            }
        }
    }

    fun loadAsync(drawingId: String, onResult: (ConveyorProfile?) -> Unit) {
        InspectionDbGate.submit(
            block = {
                InspectionRepository.database()?.conveyorProfileDao()?.get(drawingId)?.toModel()
            },
            onMain = onResult,
        )
    }

    fun loadAllAsync(onResult: (Map<String, ConveyorProfile>) -> Unit) {
        InspectionDbGate.submit(
            block = {
                InspectionRepository.database()?.conveyorProfileDao()?.all()
                    ?.associate { it.drawingId to it.toModel() }
                    .orEmpty()
            },
            onMain = onResult,
        )
    }

    fun deleteAsync(drawingId: String) {
        InspectionDbGate.execute {
            deleteSync(drawingId)
        }
    }

    /** Background-thread only. */
    fun deleteSync(drawingId: String) {
        InspectionRepository.ensureBackground()
        InspectionRepository.database()?.conveyorProfileDao()?.delete(drawingId)
    }
}
