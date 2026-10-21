package com.example.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Centralized, testable Coroutine Dispatchers.
 * Defaults to standard Kotlin Coroutines dispatchers in production.
 * Tests can override [default], [io], and [main] using test dispatchers.
 */
object AppDispatchers {
    @Volatile
    var default: CoroutineDispatcher = Dispatchers.Default

    @Volatile
    var io: CoroutineDispatcher = Dispatchers.IO

    @Volatile
    var main: CoroutineDispatcher = Dispatchers.Main

    fun reset() {
        default = Dispatchers.Default
        io = Dispatchers.IO
        main = Dispatchers.Main
    }
}
