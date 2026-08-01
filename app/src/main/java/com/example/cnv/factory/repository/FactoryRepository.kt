package com.example.cnv.factory.repository

import com.example.cnv.factory.context.CurrentContext
import com.example.cnv.factory.model.Factory

/**
 * Factory store. Reads/writes scoped conceptually to site catalog (top level).
 */
class FactoryRepository {

    private val lock = Any()
    private val items = LinkedHashMap<String, Factory>()

    fun upsert(factory: Factory) {
        synchronized(lock) { items[factory.id] = factory }
    }

    fun get(id: String): Factory? = synchronized(lock) { items[id] }

    fun all(): List<Factory> = synchronized(lock) { items.values.toList() }

    fun current(context: CurrentContext = CurrentContext.get()): Factory? {
        val id = context.factoryId ?: return null
        return get(id)
    }

    fun clear() {
        synchronized(lock) { items.clear() }
    }
}
