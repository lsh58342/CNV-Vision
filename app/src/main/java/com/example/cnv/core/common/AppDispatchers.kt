package com.example.cnv.core.common

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Shared executors / main handler. Features should not create ad-hoc pools unnecessarily.
 */
object AppDispatchers {
    val mainHandler: Handler = Handler(Looper.getMainLooper())

    val mainExecutor: Executor = Executor { command ->
        mainHandler.post(command)
    }

    val backgroundExecutor: Executor = Executors.newSingleThreadExecutor()
}
