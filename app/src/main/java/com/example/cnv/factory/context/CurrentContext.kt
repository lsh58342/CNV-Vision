package com.example.cnv.factory.context

/**
 * Global navigation context. All Factory repositories read this scope only.
 *
 * Factory → Building → Floor → Route → Zone
 */
class CurrentContext {

    @Volatile var factoryId: String? = null
        private set
    @Volatile var buildingId: String? = null
        private set
    @Volatile var floorId: String? = null
        private set
    @Volatile var routeId: String? = null
        private set
    @Volatile var zoneId: String? = null
        private set

    @Volatile var appMode: AppMode = AppMode.OPERATION
        private set
    @Volatile var accessRole: AccessRole = AccessRole.OPERATOR
        private set

    fun setAccessRole(role: AccessRole) {
        accessRole = role
        if (!role.canAccessCommissioning() && appMode == AppMode.COMMISSIONING) {
            appMode = AppMode.OPERATION
        }
    }

    fun setAppMode(mode: AppMode): Boolean {
        if (mode == AppMode.COMMISSIONING && !accessRole.canAccessCommissioning()) {
            return false
        }
        appMode = mode
        return true
    }

    fun selectFactory(id: String) {
        factoryId = id
        buildingId = null
        floorId = null
        routeId = null
        zoneId = null
    }

    fun selectBuilding(id: String) {
        buildingId = id
        floorId = null
        routeId = null
        zoneId = null
    }

    fun selectFloor(id: String) {
        floorId = id
        routeId = null
        zoneId = null
    }

    fun selectRoute(id: String) {
        routeId = id
        zoneId = null
    }

    fun selectZone(id: String) {
        zoneId = id
    }

    fun clear() {
        factoryId = null
        buildingId = null
        floorId = null
        routeId = null
        zoneId = null
    }

    fun summary(): String = buildString {
        append("mode=$appMode role=$accessRole ")
        append("F=${factoryId ?: "—"} ")
        append("B=${buildingId ?: "—"} ")
        append("Fl=${floorId ?: "—"} ")
        append("R=${routeId ?: "—"} ")
        append("Z=${zoneId ?: "—"}")
    }

    companion object {
        @Volatile
        private var instance: CurrentContext? = null

        fun get(): CurrentContext =
            instance ?: synchronized(this) {
                instance ?: CurrentContext().also { instance = it }
            }
    }
}
