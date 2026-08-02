package com.example.cnv.inspection.db

import com.example.cnv.core.common.AppDispatchers

/**
 * Gates all Room work onto [AppDispatchers.backgroundExecutor] (STEP 15-4).
 * Callers must not invoke DAO / Room APIs on the main thread.
 */
object InspectionDbGate {

    fun execute(block: () -> Unit) {
        AppDispatchers.backgroundExecutor.execute {
            runCatching(block)
        }
    }

    fun <T> submit(block: () -> T, onMain: (T) -> Unit) {
        AppDispatchers.backgroundExecutor.execute {
            val result = runCatching(block)
            AppDispatchers.mainExecutor.execute {
                result.onSuccess(onMain)
            }
        }
    }

    fun <T> submit(
        block: () -> T,
        onMain: (T) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        AppDispatchers.backgroundExecutor.execute {
            val result = runCatching(block)
            AppDispatchers.mainExecutor.execute {
                result.fold(onSuccess = onMain, onFailure = onError)
            }
        }
    }
}
