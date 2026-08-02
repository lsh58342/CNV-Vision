package com.example.cnv.factory.repository

import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.inspection.InspectionRepository
import com.example.cnv.inspection.db.InspectionDbGate
import com.example.cnv.inspection.db.InspectionProfileEntity
import com.example.cnv.profile.InspectionProfile
import com.example.cnv.profile.InspectionProfileCodec
import com.example.cnv.profile.RuleProfile
import com.example.cnv.rule.DefaultRuleCatalog

/**
 * Drawing-scoped Inspection Profile store (STEP 19-2).
 */
class InspectionProfileRepository {

    fun saveAsync(
        drawingId: String,
        profile: InspectionProfile,
        onDone: (() -> Unit)? = null,
    ) {
        InspectionDbGate.submit(
            block = { saveSync(drawingId, profile) },
            onMain = { onDone?.invoke() },
        )
    }

    /** Background-thread only. */
    fun saveSync(drawingId: String, profile: InspectionProfile) {
        InspectionRepository.database()?.inspectionProfileDao()?.upsert(
            InspectionProfileEntity(
                drawingId = drawingId,
                profileJson = InspectionProfileCodec.encodeProfile(profile),
                updatedAtMs = profile.updatedAtMs.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
            ),
        )
    }

    fun loadAsync(
        drawingId: String,
        conveyorFallback: ConveyorProfile = ConveyorProfile.fromConfig(),
        onResult: (InspectionProfile) -> Unit,
    ) {
        InspectionDbGate.submit(
            block = {
                val entity = InspectionRepository.database()?.inspectionProfileDao()?.get(drawingId)
                InspectionProfileCodec.decodeProfile(entity?.profileJson, conveyorFallback)
            },
            onMain = onResult,
        )
    }

    /** Background-thread only. */
    fun loadSync(
        drawingId: String,
        conveyorFallback: ConveyorProfile = ConveyorProfile.fromConfig(),
    ): InspectionProfile {
        val entity = InspectionRepository.database()?.inspectionProfileDao()?.get(drawingId)
        return InspectionProfileCodec.decodeProfile(entity?.profileJson, conveyorFallback)
    }

    fun deleteAsync(drawingId: String, onDone: (() -> Unit)? = null) {
        InspectionDbGate.submit(
            block = {
                InspectionRepository.database()?.inspectionProfileDao()?.delete(drawingId)
            },
            onMain = { onDone?.invoke() },
        )
    }

    companion object {
        fun buildDefaultRuleProfile(catalogVersion: Int): RuleProfile {
            val entries = DefaultRuleCatalog.defaults().map {
                com.example.cnv.profile.RuleProfileEntry(
                    ruleId = it.ruleId,
                    enabled = it.enabled,
                    ruleVersion = it.version,
                    thresholdOverride = null,
                    severityOverride = null,
                )
            }
            return RuleProfile(catalogVersion = catalogVersion, entries = entries)
        }
    }
}
