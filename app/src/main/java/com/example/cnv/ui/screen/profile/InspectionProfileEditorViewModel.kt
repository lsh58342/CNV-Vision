package com.example.cnv.ui.screen.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.ConveyorProfile
import com.example.cnv.factory.repository.FactoryCatalog
import com.example.cnv.factory.repository.InspectionProfileRepository
import com.example.cnv.profile.InspectionProfile
import com.example.cnv.profile.RuleProfile
import com.example.cnv.profile.RuleProfileEntry
import com.example.cnv.profile.SensorProfile
import com.example.cnv.rule.DefaultRuleCatalog
import com.example.cnv.rule.RuleSeverity

/**
 * Inspection Profile Editor — Drawing-scoped save only (STEP 20-2).
 * Does not alter running Inspection Session snapshots.
 */
class InspectionProfileEditorViewModel(
    private val catalog: FactoryCatalog = FactoryCatalog.get(),
) : ViewModel() {

    data class UiState(
        val drawingId: String = "",
        val drawingName: String = "—",
        val profile: InspectionProfile = InspectionProfile.default(),
        val loading: Boolean = true,
        val saved: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    fun load(preferredDrawingId: String? = null) {
        val ctx = CurrentContext.get()
        val drawing = preferredDrawingId?.let { catalog.drawings.get(it) }
            ?: catalog.drawings.current(ctx)
        if (drawing == null) {
            _state.value = UiState(loading = false, errorMessage = "No Drawing selected")
            return
        }
        val conveyorFallback = drawing.conveyorProfile
        catalog.inspectionProfiles.loadAsync(drawing.id, conveyorFallback) { stored ->
            val rule = if (stored.rule.entries.isEmpty()) {
                InspectionProfileRepository.buildDefaultRuleProfile(catalog.rules.catalogVersion())
            } else {
                mergeWithCatalog(stored.rule)
            }
            _state.value = UiState(
                drawingId = drawing.id,
                drawingName = drawing.name,
                profile = stored.copy(
                    conveyor = conveyorFallback,
                    rule = rule,
                ),
                loading = false,
            )
        }
    }

    fun save(
        conveyor: ConveyorProfile,
        sensor: SensorProfile,
        rules: List<RuleProfileEntry>,
    ): Boolean {
        val current = _state.value ?: return false
        val drawingId = current.drawingId
        if (drawingId.isBlank()) return false
        val drawing = catalog.drawings.get(drawingId) ?: return false
        val now = System.currentTimeMillis()
        val updatedConveyor = conveyor.copy(lastUpdatedMs = now)
        val profile = InspectionProfile(
            conveyor = updatedConveyor,
            sensor = sensor,
            rule = RuleProfile(
                catalogVersion = catalog.rules.catalogVersion(),
                entries = rules,
            ),
            export = current.profile.export,
            updatedAtMs = now,
        )
        catalog.drawings.upsert(
            drawing.copy(
                conveyorProfile = updatedConveyor,
                updatedAtMs = now,
            ),
        )
        catalog.conveyorProfiles.saveAsync(drawingId, updatedConveyor)
        catalog.inspectionProfiles.saveAsync(drawingId, profile)
        _state.value = current.copy(profile = profile, saved = true, errorMessage = null)
        return true
    }

    private fun mergeWithCatalog(existing: RuleProfile): RuleProfile {
        val byId = existing.entries.associateBy { it.ruleId }
        val merged = DefaultRuleCatalog.defaults().map { def ->
            byId[def.ruleId] ?: RuleProfileEntry(
                ruleId = def.ruleId,
                enabled = def.enabled,
                ruleVersion = def.version,
            )
        }
        return existing.copy(
            catalogVersion = catalog.rules.catalogVersion(),
            entries = merged,
        )
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InspectionProfileEditorViewModel::class.java)) {
                return InspectionProfileEditorViewModel() as T
            }
            error("Unknown ViewModel: ${modelClass.name}")
        }
    }

    companion object {
        val SEVERITY_OPTIONS: List<String> =
            listOf("") + RuleSeverity.entries.map { it.name }
    }
}
