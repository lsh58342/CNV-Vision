package com.example.cnv.replay

import com.example.cnv.factory.repository.FactoryCatalog

/**
 * Creates [ReplayEngine] implementations for injection (STEP 16-3).
 * Call sites depend on [ReplayEngine]; never construct internals directly.
 *
 * Future: MockReplayEngine / RemoteReplayEngine via the same factory API.
 */
object ReplayEngineFactory {

    fun createDefault(
        config: ReplayConfig = ReplayConfig.DEFAULT,
        catalog: FactoryCatalog = FactoryCatalog.get(),
    ): ReplayEngine = DefaultReplayEngine(config, catalog)
}
