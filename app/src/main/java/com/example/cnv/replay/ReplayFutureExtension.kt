package com.example.cnv.replay

/**
 * Future consumers (AI / Report / Export / Compare / Sync) depend on [ReplayEngine] only (STEP 16-3).
 *
 * Planned implementations (not in this STEP):
 * - MockReplayEngine
 * - RemoteReplayEngine
 * - ProductionReplayEngine (alias of [DefaultReplayEngine] if needed)
 *
 * Obtain engines via [ReplayEngineFactory]; do not import `replay.internal`.
 */
object ReplayFutureExtension
