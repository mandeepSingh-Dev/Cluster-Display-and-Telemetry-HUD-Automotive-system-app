package com.oem.cluster.service

import android.car.Car
import android.car.VehicleAreaWheel
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Looper
import android.util.Log
import com.oem.cluster.data.ClusterTelemetryState
import com.oem.cluster.data.DriveMode
import com.oem.cluster.data.SensorReadiness
import com.oem.cluster.data.TirePressuresPsi
import com.oem.cluster.data.TurnSignal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.logging.Handler
import kotlin.math.roundToInt

class ClusterTelemetryManager(context: Context) {

    private val appContext = context.applicationContext
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    private val _state = MutableStateFlow(ClusterTelemetryState())
    val state: StateFlow<ClusterTelemetryState> = _state.asStateFlow()

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            // Ignore hardware updates while user is actively driving sliders from CID simulator

            Log.d("fkbmfknbkfb", "AreaID: ${value.areaId} PropertyId: ${value.propertyId} Value:  ${value.value} ")
            if (_state.value.isBenchOverrideActive) return

            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    val speedMs = (value.value as? Float) ?: 0f
                    val kmh = (speedMs * 3.6f).roundToInt().coerceIn(0, 260)
                    _state.update { it.copy(speedKmh = kmh) }
                }

                VehiclePropertyIds.ENGINE_RPM -> {
                    val rpmVal = (value.value as? Float)?.roundToInt() ?: 0
                    _state.update { it.copy(rpm = rpmVal.coerceIn(0, 8000)) }
                }

                VehiclePropertyIds.CURRENT_GEAR -> {
                    val rawGear = (value.value as? Int) ?: VehicleGear.GEAR_PARK
                    _state.update { it.copy(gearLabel = mapVhalGear(rawGear)) }
                }

                VehiclePropertyIds.TURN_SIGNAL_STATE -> {
                    val rawSignal = (value.value as? Int) ?: 0
                    _state.update { it.copy(turnSignal = TurnSignal.fromVhal(rawSignal)) }
                }

                VehiclePropertyIds.TIRE_PRESSURE -> {
                    val kpa = (value.value as? Float) ?: 0f
                    val psi = ((kpa * 0.145038f) * 10f).roundToInt() / 10f
                    updateWheelPressure(value.areaId, psi)
                }
            }
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.e(TAG, "VHAL callback error for propId=0x${propId.toString(16)}, zone=$zone")
        }
    }

    // Inside connect():
    fun connect() {
        if (car != null) return
        val mainHandler = android.os.Handler(Looper.getMainLooper())
        try {
            car = Car.createCar(
                appContext,
                mainHandler,
                Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER
            ) { connectedCar, ready ->
                if (ready) {
                    propertyManager =
                        connectedCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    registerVhalSubscriptions()
                    _state.update { it.copy(isVhalConnected = true) }
                    Log.i(TAG, "Connected to CarPropertyManager on MainLooper")
                } else {
                    _state.update { it.copy(isVhalConnected = false) }
                    Log.w(TAG, "Car service disconnected")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Car.createCar failed: ${e.message}", e)
        }
    }
    private fun registerVhalSubscriptions() {
        val mgr = propertyManager ?: return

        val speedOk = subscribeSafe(mgr, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_UI)
        val rpmOk = subscribeSafe(mgr, VehiclePropertyIds.ENGINE_RPM, CarPropertyManager.SENSOR_RATE_UI)
        val gearOk = subscribeSafe(mgr, VehiclePropertyIds.CURRENT_GEAR, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        val signalOk = subscribeSafe(mgr, VehiclePropertyIds.TURN_SIGNAL_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        val tpmsOk = subscribeSafe(mgr, VehiclePropertyIds.TIRE_PRESSURE, CarPropertyManager.SENSOR_RATE_ONCHANGE)

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

    private fun subscribeSafe(mgr: CarPropertyManager, propId: Int, rate: Float): Boolean {
        return try {
            mgr.registerCallback(propertyCallback, propId, rate)
            true
        } catch (e: SecurityException){
            Log.w(TAG,"Security Exception: Property 0x${propId.toString(16)} unavailable: ${e.message}")
         false
        }catch (e: Exception) {
            Log.w(TAG, "Property 0x${propId.toString(16)} unavailable: ${e.message}")
            false
        }
    }

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

    private fun mapVhalGear(gear: Int): String = when (gear) {
        VehicleGear.GEAR_PARK -> "P"
        VehicleGear.GEAR_REVERSE -> "R"
        VehicleGear.GEAR_NEUTRAL -> "N"
        VehicleGear.GEAR_DRIVE -> "D"
        VehicleGear.GEAR_FIRST, VehicleGear.GEAR_SECOND, VehicleGear.GEAR_THIRD,
        VehicleGear.GEAR_FOURTH, VehicleGear.GEAR_FIFTH, VehicleGear.GEAR_SIXTH, VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH, VehicleGear.GEAR_NINTH  -> "S"
        else -> "D"
    }

    // --- CID Bench Simulator Overrides (Called from Display 0 via TelemetryService IPC) ---

    fun setBenchOverrideEnabled(enabled: Boolean) {
        _state.update { it.copy(isBenchOverrideActive = enabled) }
    }

    fun updateSimulatedSpeed(kmh: Int) {
        _state.update { it.copy(isBenchOverrideActive = true, speedKmh = kmh.coerceIn(0, 260)) }
    }

    fun updateSimulatedRpm(rpm: Int) {
        _state.update { it.copy(isBenchOverrideActive = true, rpm = rpm.coerceIn(0, 8000)) }
    }

    fun updateSimulatedGear(gearLabel: String) {
        _state.update { it.copy(isBenchOverrideActive = true, gearLabel = gearLabel) }
    }

    fun updateSimulatedTurnSignal(signal: TurnSignal) {
        _state.update { it.copy(isBenchOverrideActive = true, turnSignal = signal) }
    }

    fun updateSimulatedTirePressures(pressures: TirePressuresPsi) {
        _state.update { it.copy(isBenchOverrideActive = true, tirePressures = pressures) }
    }

    fun setDriveMode(mode: DriveMode) {
        _state.update { it.copy(driveMode = mode) }
    }

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
        private const val TAG = "ClusterTelemetryMgr"
    }
}