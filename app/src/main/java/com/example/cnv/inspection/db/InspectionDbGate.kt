package com.example.cnv.inspection.db

import android.os.Looper
import com.example.cnv.core.common.AppDispatchers
import com.example.cnv.production.ProductionLog
import com.example.cnv.production.RecoveryCoordinator

/**
 * Gates all Room work onto [AppDispatchers.backgroundExecutor] (STEP 15-4 / 20).
 * Callers must not invoke DAO / Room APIs on the main thread.
 * Retries transient Room failures without crashing the process.
 */
object InspectionDbGate {

    fun execute(block: () -> Unit) {
        AppDispatchers.backgroundExecutor.execute {
            runCatching {
                assertBackgroundThread()
                RecoveryCoordinator.withRoomRetry(block = block)
            }.onFailure { err ->
                ProductionLog.error("CNV.Room", "Background execute failed", err)
            }
        }
    }

    fun <T> submit(block: () -> T, onMain: (T) -> Unit) {
        AppDispatchers.backgroundExecutor.execute {
            val result = runCatching {
                assertBackgroundThread()
                RecoveryCoordinator.withRoomRetry(block = block)
            }
            AppDispatchers.mainExecutor.execute {
                result.onSuccess(onMain)
                    .onFailure { ProductionLog.error("CNV.Room", "Submit failed", it) }
            }
        }
    }

    fun <T> submit(
        block: () -> T,
        onMain: (T) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        AppDispatchers.backgroundExecutor.execute {
            val result = runCatching {
                assertBackgroundThread()
                RecoveryCoordinator.withRoomRetry(block = block)
            }
            AppDispatchers.mainExecutor.execute {
                result.fold(onSuccess = onMain, onFailure = onError)
            }
        }
    }

    private fun assertBackgroundThread() {
        if (Looper.getMainLooper().thread === Thread.currentThread()) {
            ProductionLog.error("CNV.Room", "Main-thread Room access blocked")
            error("Room must not run on the main thread")
        }
    }
}
