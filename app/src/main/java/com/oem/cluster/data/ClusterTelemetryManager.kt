package com.oem.cluster.data

import android.car.Car
import android.car.VehicleAreaWheel
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * Manages communication with the Android Automotive OS (AAOS) Vehicle Hardware Abstraction Layer (VHAL)
 * via [CarPropertyManager].
 *
 * Exposes real-time vehicle telemetry data as a reactive [StateFlow] of [ClusterTelemetryState],
 * and provides test bench / simulator override capabilities for non-AAOS environments or development benches.
 *
 * @param context The application context used to establish the car connection.
 */
class ClusterTelemetryManager(context: Context) {

    /** Application context used to connect to the car service without leaking activity references. */
    private val appContext = context.applicationContext

    /** Android Automotive Car API connection instance. */
    private var car: Car? = null

    /** Car property manager used to read and subscribe to vehicle hardware properties. */
    private var propertyManager: CarPropertyManager? = null

    /** Backing mutable state flow holding the latest [ClusterTelemetryState]. */
    private val _state = MutableStateFlow(ClusterTelemetryState())

    /** Public read-only state flow exposed to UI components and ViewModels. */
    val state: StateFlow<ClusterTelemetryState> = _state.asStateFlow()

    /**
     * Callback listener receiving asynchronous hardware property change and error events from VHAL.
     */
    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        /**
         * Triggered whenever a subscribed VHAL property value changes.
         *
         * @param value The updated [CarPropertyValue] containing property ID, area ID, and new value.
         */
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            // Ignore hardware sensor updates while the user is actively driving sliders from CID simulator/bench override


            if (_state.value.isBenchOverrideActive) return

            // Handle updates based on specific vehicle property IDs received from VHAL
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    // Speed in VHAL is typically returned in meters per second (m/s)
                    val speedMs = (value.value as? Float) ?: 0f
                    // Convert m/s to km/h (multiply by 3.6), round to nearest int, and clamp between 0 and 260 km/h
                    val kmh = (speedMs * 3.6f).roundToInt().coerceIn(0, 260)
                    _state.update { it.copy(speedKmh = kmh) }
                }

                VehiclePropertyIds.ENGINE_RPM -> {
                    val rpmVal = (value.value as? Float)?.roundToInt() ?: 0
                    // Clamp engine RPM between 0 and 8000
                    _state.update { it.copy(rpm = rpmVal.coerceIn(0, 8000)) }
                }

                VehiclePropertyIds.CURRENT_GEAR -> {
                    val rawGear = (value.value as? Int) ?: VehicleGear.GEAR_PARK
                    // Map raw VHAL gear integer to gear label string (P, R, N, D, S)
                    _state.update { it.copy(gearLabel = mapVhalGear(rawGear)) }
                }

                VehiclePropertyIds.TURN_SIGNAL_STATE -> {
                    val rawSignal = (value.value as? Int) ?: 0
                    // Map raw VHAL integer turn signal state to TurnSignal enum
                    _state.update { it.copy(turnSignal = TurnSignal.fromVhal(rawSignal)) }
                }

                VehiclePropertyIds.TIRE_PRESSURE -> {
                    val kpa = (value.value as? Float) ?: 0f
                    // Convert kilopascals (kPa) to PSI (1 kPa = 0.145038 PSI), rounded to 1 decimal place
                    val psi = ((kpa * 0.145038f) * 10f).roundToInt() / 10f
                    // Update tire pressure for the specific wheel area ID (front-left, front-right, etc.)
                    updateWheelPressure(value.areaId, psi)
                }
            }
        }

        /**
         * Triggered when an error occurs on a subscribed VHAL property.
         *
         * @param propId The property ID that triggered the error.
         * @param zone The zone/area ID where the error occurred.
         */
        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.e(TAG, "VHAL callback error for propId=0x${propId.toString(16)}, zone=$zone")
        }
    }

    /**
     * Establishes connection to the Android Automotive Car service and initializes property subscriptions
     * once connected.
     */
    fun connect() {
        if (car != null) return
        try {
            // Create car connection with indefinite wait timeout
            car = Car.createCar(
                appContext,
                null,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER
            ) { connectedCar, ready ->
                if (ready) {
                    // Obtain CarPropertyManager service reference upon successful connection
                    propertyManager =
                        connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    // Register hardware property subscriptions
                    registerVhalSubscriptions()
                    _state.update { it.copy(isVhalConnected = true) }
                    Log.i(TAG, "Connected to CarPropertyManager")
                } else {
                    _state.update { it.copy(isVhalConnected = false) }
                    Log.w(TAG, "Car service disconnected")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Car.createCar failed (non-AAOS environment?): ${e.message}")
        }
    }

    /**
     * Subscribes to VHAL vehicle properties (Speed, RPM, Gear, Turn Signal, Tire Pressure)
     * using [CarPropertyManager] and updates sensor readiness states.
     */
    private fun registerVhalSubscriptions() {
        val mgr = propertyManager ?: return

        // Subscribe to high-frequency sensor updates (speed and RPM)
        val speedOk = subscribeSafe(mgr, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST)
        val rpmOk = subscribeSafe(mgr, VehiclePropertyIds.ENGINE_RPM, CarPropertyManager.SENSOR_RATE_FAST)
        // Subscribe to on-change state updates (gear, turn signal, tire pressure)
        val gearOk = subscribeSafe(mgr, VehiclePropertyIds.CURRENT_GEAR, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        val signalOk = subscribeSafe(mgr, VehiclePropertyIds.TURN_SIGNAL_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        val tpmsOk = subscribeSafe(mgr, VehiclePropertyIds.TIRE_PRESSURE, CarPropertyManager.SENSOR_RATE_ONCHANGE)

        // Update sensor readiness status in state flow
        _state.update {
            it.copy(
                sensorReadiness = SensorReadiness(
                    speedReady = speedOk,
                    rpmReady = rpmOk,
                    gearReady = gearOk,
                    turnSignalReady = signalOk,
                    tpmsReady = tpmsOk
                )
            )
        }
    }

    /**
     * Safely registers a property callback with [CarPropertyManager], catching any unsupported property exceptions.
     */
    private fun subscribeSafe(mgr: CarPropertyManager, propId: Int, rate: Float): Boolean {
        return try {
            mgr.registerCallback(propertyCallback, propId, rate)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Property 0x${propId.toString(16)} unavailable: ${e.message}")
            false
        }
    }

    /**
     * Updates individual wheel tire pressure (PSI) based on the VHAL area ID.
     */
    private fun updateWheelPressure(areaId: Int, psi: Float) {
        _state.update { current ->
            val t = current.tirePressures
            val updated = when (areaId) {
                VehicleAreaWheel.WHEEL_LEFT_FRONT -> t.copy(frontLeft = psi)
                VehicleAreaWheel.WHEEL_RIGHT_FRONT -> t.copy(frontRight = psi)
                VehicleAreaWheel.WHEEL_LEFT_REAR -> t.copy(rearLeft = psi)
                VehicleAreaWheel.WHEEL_RIGHT_REAR -> t.copy(rearRight = psi)
                else -> t
            }
            current.copy(tirePressures = updated)
        }
    }

    /**
     * Maps raw VHAL gear integer constants to user-friendly gear display labels ("P", "R", "N", "D", "S").
     */
    private fun mapVhalGear(gear: Int): String = when (gear) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_DRIVE -> "D"
        VehicleGear.GEAR_FIRST, VehicleGear.GEAR_SECOND, VehicleGear.GEAR_THIRD,
        VehicleGear.GEAR_FOURTH, VehicleGear.GEAR_FIFTH, VehicleGear.GEAR_SIXTH, VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH,VehicleGear.GEAR_NINTH,  -> "S"
        else -> "D"
    }

    // --- CID Bench Simulator Overrides (Called from Display 0 via TelemetryService IPC) ---

    /**
     * Enables or disables test bench simulator overrides, bypassing live VHAL sensor updates when active.
     */
    fun setBenchOverrideEnabled(enabled: Boolean) {
        _state.update { it.copy(isBenchOverrideActive = enabled) }
    }

    /**
     * Updates simulated vehicle speed (km/h) for the bench simulator.
     */
    fun updateSimulatedSpeed(kmh: Int) {
        _state.update { it.copy(isBenchOverrideActive = true, speedKmh = kmh.coerceIn(0, 260)) }
    }

    /**
     * Updates simulated engine RPM for the bench simulator.
     */
    fun updateSimulatedRpm(rpm: Int) {
        _state.update { it.copy(isBenchOverrideActive = true, rpm = rpm.coerceIn(0, 8000)) }
    }

    /**
     * Updates simulated gear label for the bench simulator.
     */
    fun updateSimulatedGear(gearLabel: String) {
        _state.update { it.copy(isBenchOverrideActive = true, gearLabel = gearLabel) }
    }

    /**
     * Updates simulated turn signal state for the bench simulator.
     */
    fun updateSimulatedTurnSignal(signal: TurnSignal) {
        _state.update { it.copy(isBenchOverrideActive = true, turnSignal = signal) }
    }

    /**
     * Updates simulated tire pressures for the bench simulator.
     */
    fun updateSimulatedTirePressures(pressures: TirePressuresPsi) {
        _state.update { it.copy(isBenchOverrideActive = true, tirePressures = pressures) }
    }

    /**
     * Sets the active vehicle drive mode (e.g. ECO, COMFORT, SPORT).
     */
    fun setDriveMode(mode: DriveMode) {
        _state.update { it.copy(driveMode = mode) }
    }

    /**
     * Unregisters property callbacks and disconnects from the car service.
     */
    fun disconnect() {
        try {
            propertyManager?.unregisterCallback(propertyCallback)
            car?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error during Car disconnect: ${e.message}")
        } finally {
            propertyManager = null
            car = null
        }
    }

    companion object {
        /** Logging tag for [com.oem.cluster.service.ClusterTelemetryManager]. */
        private const val TAG = "ClusterTelemetryMgr"
    }
}
