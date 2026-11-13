package com.oem.cluster.ui.cluster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oem.cluster.data.*
import com.oem.cluster.display.ClusterDisplayRouter
import com.oem.cluster.display.SecondaryDisplayInfo
import com.oem.cluster.service.ClusterTelemetryManager

/**
 * Center Information Display (CID) Controller Activity (Display 0).
 * Acts as a testing and bench simulation dashboard allowing developers to override vehicle telemetry parameters
 * (speed, RPM, gear, turn signals, TPMS, drive modes) and launch the cluster HUD onto secondary displays.
 */
class CidControllerActivity : ComponentActivity() {

    /** Telemetry manager state instance retrieved from bound [TelemetryService]. */
    private var managerState by mutableStateOf<ClusterTelemetryManager?>(null)
    /** Secondary display information for projecting the cluster HUD onto Display 1. */
    private var clusterDisplayInfo by mutableStateOf<SecondaryDisplayInfo?>(null)

    /** Service connection callback handling binding and unbinding lifecycle of [TelemetryService]. */
    private val connection = object : ServiceConnection {
        /** Called when successfully connected to [TelemetryService]. */
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TelemetryService.TelemetryBinder
            managerState = binder?.getManager()
        }

        /** Called when the connection to [TelemetryService] is unexpectedly disconnected. */
        override fun onServiceDisconnected(name: ComponentName?) {
            managerState = null
        }
    }

    /** Called when the activity is starting; binds to [TelemetryService]. */
    override fun onStart() {
        super.onStart()
        bindService(Intent(this, TelemetryService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    /** Called when the activity is stopping; unbinds from [TelemetryService]. */
    override fun onStop() {
        unbindService(connection)
        super.onStop()
    }

    /** Called when activity is created; launches cluster on secondary display and sets up Compose UI. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Automatically project ClusterHudActivity onto Display 1 if present
        clusterDisplayInfo = ClusterDisplayRouter.launchClusterOnSecondaryDisplay(this)

        setContent {
            val manager = managerState
            if (manager != null) {
                val state by manager.state.collectAsStateWithLifecycle()
                CidSimulatorScreen(
                    state = state,
                    clusterDisplay = clusterDisplayInfo,
                    onRelaunchCluster = {
                        clusterDisplayInfo = ClusterDisplayRouter.launchClusterOnSecondaryDisplay(this)
                    },
                    onSpeedChange = manager::updateSimulatedSpeed,
                    onRpmChange = manager::updateSimulatedRpm,
                    onGearChange = manager::updateSimulatedGear,
                    onSignalChange = manager::updateSimulatedTurnSignal,
                    onTirePressureChange = manager::updateSimulatedTirePressures,
                    onDriveModeChange = manager::setDriveMode,
                    onResetToLiveVhal = { manager.setBenchOverrideEnabled(false) }
                )
            }
        }
    }
}

/**
 * Composable screen rendering the CID Bench Controller interface with interactive simulation controls.
 */
@Composable
fun CidSimulatorScreen(
    state: ClusterTelemetryState,
    clusterDisplay: SecondaryDisplayInfo?,
    onRelaunchCluster: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onRpmChange: (Int) -> Unit,
    onGearChange: (String) -> Unit,
    onSignalChange: (TurnSignal) -> Unit,
    onTirePressureChange: (TirePressuresPsi) -> Unit,
    onDriveModeChange: (DriveMode) -> Unit,
    onResetToLiveVhal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B111A))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header & Multi-Display Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CID TELEMETRY BENCH CONTROLLER (DISPLAY 0)",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (clusterDisplay != null) {
                        "Cluster Projected -> Display ${clusterDisplay.displayId} (${clusterDisplay.width}x${clusterDisplay.height})"
                    } else {
                        "No Secondary Display Detected — Add one in Emulator Extended Controls -> Displays"
                    },
                    color = if (clusterDisplay != null) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                    fontSize = 13.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onResetToLiveVhal) {
                    Text("Use Live VHAL")
                }
                Button(onClick = onRelaunchCluster) {
                    Text("Launch Cluster on Display 1")
                }
            }
        }

        // Speed & RPM Sliders
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2B))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Vehicle Speed: ${state.speedKmh} km/h", color = Color.White, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = state.speedKmh.toFloat(),
                    onValueChange = { onSpeedChange(it.toInt()) },
                    valueRange = 0f..240f
                )

                Text("Engine RPM: ${state.rpm} RPM", color = Color.White, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = state.rpm.toFloat(),
                    onValueChange = { onRpmChange(it.toInt()) },
                    valueRange = 0f..8000f
                )
            }
        }

        // Gear, Turn Signals & Drive Modes
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2B))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Gear Selection", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("P", "R", "N", "D", "S").forEach { gear ->
                        FilterChip(
                            selected = state.gearLabel == gear,
                            onClick = { onGearChange(gear) },
                            label = { Text(gear) }
                        )
                    }
                }

                Text("Turn Signals", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TurnSignal.entries.forEach { signal ->
                        FilterChip(
                            selected = state.turnSignal == signal,
                            onClick = { onSignalChange(signal) },
                            label = { Text(signal.name) }
                        )
                    }
                }

                Text("Cluster Drive Mode Theme", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DriveMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.driveMode == mode,
                            onClick = { onDriveModeChange(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        // TPMS Front-Left Pressure Test Slider (Trigger < 28 PSI Alert)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2B))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val fl = state.tirePressures.frontLeft
                Text(
                    text = "Front-Left Tire Pressure: ${String.format("%.1f", fl)} PSI ${if (fl < 28f) "(LOW ALERT!)" else ""}",
                    color = if (fl < 28f) Color(0xFFF59E0B) else Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = fl,
                    onValueChange = { newPsi ->
                        onTirePressureChange(state.tirePressures.copy(frontLeft = newPsi))
                    },
                    valueRange = 18f..40f
                )
            }
        }
    }
}
