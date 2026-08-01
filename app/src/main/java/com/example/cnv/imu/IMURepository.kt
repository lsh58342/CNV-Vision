package com.example.cnv.imu

/**
 * Holds the latest processed IMU snapshot for debug / future fusion subscribers.
 */
class IMURepository {

    @Volatile
    private var latest: IMUData = IMUData()

    fun update(data: IMUData) {
        latest = data
    }

    fun latest(): IMUData = latest

    fun clear() {
        latest = IMUData()
    }
}
