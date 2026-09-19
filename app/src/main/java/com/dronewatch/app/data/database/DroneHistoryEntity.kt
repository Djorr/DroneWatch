package com.dronewatch.app.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drone_history")
data class DroneHistoryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val droneId: String,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val speed: Double?,
    val heading: Double?,
    val rssi: Int?,
    val transport: String,
    val timestamp: Long,
    val isDemo: Boolean
)
