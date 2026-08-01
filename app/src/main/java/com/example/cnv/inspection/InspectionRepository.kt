package com.example.cnv.inspection

/**
 * In-memory store for completed [InspectionResult] (no DB / cloud in this STEP).
 */
class InspectionRepository(
    private val limit: Int = InspectionConfig.DEFAULT_CACHE_LIMIT,
) {

    private val lock = Any()
    private val results = ArrayDeque<InspectionResult>()

    fun save(result: InspectionResult) {
        synchronized(lock) {
            results.addLast(result)
            while (results.size > limit) {
                results.removeFirst()
            }
        }
    }

    fun latest(): InspectionResult? = synchronized(lock) { results.lastOrNull() }

    fun all(): List<InspectionResult> = synchronized(lock) { results.toList() }

    fun clear() {
        synchronized(lock) {
            results.clear()
        }
    }
}
