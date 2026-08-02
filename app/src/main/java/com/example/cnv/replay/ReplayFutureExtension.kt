package com.example.cnv.replay

/**
 * Future consumers (Replay AI / Report / Export / Compare / Sync) must depend on
 * [ReplayEngineApi] only — same contract as Viewer and Analysis (STEP 16-2).
 *
 * Do not import `com.example.cnv.replay.internal` or `facade` implementation details
 * from feature modules; obtain an Engine via [ReplayEngine].
 */
object ReplayFutureExtension {
    // Intentionally empty — documents the extension contract for AI / Report.
}
