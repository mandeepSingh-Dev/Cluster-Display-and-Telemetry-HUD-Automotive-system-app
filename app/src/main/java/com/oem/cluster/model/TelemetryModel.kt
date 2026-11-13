package com.oem.cluster.data

enum class DriveMode(val label: String) {
    ECO("ECO"),
    COMFORT("COMFORT"),
    SPORT("SPORT")
}

enum class TurnSignal(val vhalCode: Int) {
    NONE(0),
    LEFT(1),
    RIGHT(2),
    HAZARD(3);

    companion object {
        fun fromVhal(raw: Int): TurnSignal = entries.firstOrNull { it.vhalCode == raw } ?: NONE
    }
}

data class TirePressuresPsi(
    val frontLeft: Float = 33.0f,
    val frontRight: Float = 33.2f,
    val rearLeft: Float = 32.1f,
    val rearRight: Float = 32.0f
) {
    val hasLowPressureWarning: Boolean
        get() = frontLeft < LOW_PRESSURE_THRESHOLD ||
                frontRight < LOW_PRESSURE_THRESHOLD ||
                rearLeft < LOW_PRESSURE_THRESHOLD ||
                rearRight < LOW_PRESSURE_THRESHOLD

    companion object {
        const val LOW_PRESSURE_THRESHOLD = 28.0f
    }
}

data class SensorReadiness(
    val speedReady: Boolean = false,
    val rpmReady: Boolean = false,
    val gearReady: Boolean = false,
    val turnSignalReady: Boolean = false,
    val tpmsReady: Boolean = false
)

data class ClusterTelemetryState(
    val speedKmh: Int = 0,
    val rpm: Int = 0,
    val gearLabel: String = "P",
    val turnSignal: TurnSignal = TurnSignal.NONE,
    val tirePressures: TirePressuresPsi = TirePressuresPsi(),
    val driveMode: DriveMode = DriveMode.COMFORT,
    val isVhalConnected: Boolean = false,
    val isBenchOverrideActive: Boolean = false,
    val sensorReadiness: SensorReadiness = SensorReadiness()
)