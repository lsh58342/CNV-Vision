package com.example.cnv.report

/**
 * In-memory Work Order store (STEP 19).
 * Future CMMS / SAP PM / MES sync attaches here without changing Report UI.
 */
class WorkOrderRepository {

    private val lock = Any()
    private val orders = LinkedHashMap<String, WorkOrder>()

    fun put(order: WorkOrder): WorkOrder {
        synchronized(lock) {
            orders[order.workOrderId] = order
            return order
        }
    }

    fun get(workOrderId: String): WorkOrder? =
        synchronized(lock) { orders[workOrderId] }

    fun forSession(sessionId: String): List<WorkOrder> =
        synchronized(lock) { orders.values.filter { it.sessionId == sessionId } }

    fun forDrawing(drawingId: String): List<WorkOrder> =
        synchronized(lock) { orders.values.filter { it.drawingId == drawingId } }

    fun all(): List<WorkOrder> =
        synchronized(lock) { orders.values.toList() }

    fun updateStatus(workOrderId: String, status: WorkOrderStatus): WorkOrder? {
        synchronized(lock) {
            val existing = orders[workOrderId] ?: return null
            val updated = existing.copy(
                status = status,
                updatedAtMs = System.currentTimeMillis(),
            )
            orders[workOrderId] = updated
            return updated
        }
    }

    fun removeForSession(sessionId: String) {
        synchronized(lock) {
            val ids = orders.filterValues { it.sessionId == sessionId }.keys.toList()
            ids.forEach { orders.remove(it) }
        }
    }

    fun removeForDrawing(drawingId: String) {
        synchronized(lock) {
            val ids = orders.filterValues { it.drawingId == drawingId }.keys.toList()
            ids.forEach { orders.remove(it) }
        }
    }

    fun clear() {
        synchronized(lock) { orders.clear() }
    }
}
