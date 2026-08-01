package com.example.cnv.factory.seed

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Factory
import com.example.cnv.factory.repository.FactoryCatalog

/**
 * Single-site bootstrap for Operation / Commissioning.
 * Only ensures LGES Poland exists — no Building / Floor / Zone / Route samples.
 */
object LgesPolandSite {

    const val FACTORY_ID = "factory-lges-poland"
    const val FACTORY_NAME = "LGES Poland"

    /**
     * Ensures the single factory record and selects it in [CurrentContext].
     * Never creates Building / Floor / Zone / Route.
     */
    fun ensure(catalog: FactoryCatalog = FactoryCatalog.get()) {
        if (catalog.factories.get(FACTORY_ID) == null) {
            catalog.factories.upsert(Factory(id = FACTORY_ID, name = FACTORY_NAME))
        }
        val ctx = CurrentContext.get()
        if (ctx.factoryId != FACTORY_ID) {
            ctx.selectFactory(FACTORY_ID)
        }
    }
}

/** @deprecated Use [LgesPolandSite]. Kept name alias for gradual migration. */
@Deprecated("Use LgesPolandSite", ReplaceWith("LgesPolandSite"))
object FactorySeedData {
    const val FACTORY_ID = LgesPolandSite.FACTORY_ID

    fun ensureSeeded(catalog: FactoryCatalog = FactoryCatalog.get()) {
        LgesPolandSite.ensure(catalog)
    }
}
