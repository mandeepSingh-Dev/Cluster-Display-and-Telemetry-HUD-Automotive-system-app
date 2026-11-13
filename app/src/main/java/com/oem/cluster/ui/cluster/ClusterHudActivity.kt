package com.oem.cluster.ui.cluster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oem.cluster.service.ClusterTelemetryManager
import com.oem.cluster.data.ClusterTelemetryState
import com.oem.cluster.data.TelemetryService
import com.oem.cluster.ui.theme.paletteForDriveMode

/**
 * Activity representing the digital instrument cluster HUD screen.
 * Binds to [TelemetryService] to retrieve vehicle telemetry data and renders the HUD UI using Jetpack Compose.
 */
class ClusterHudActivity : ComponentActivity() {

    /** State holding the connected [ClusterTelemetryManager] instance retrieved from the bound service. */
    private var managerState by mutableStateOf<ClusterTelemetryManager?>(null)

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

    /** Called when the activity is starting; initiates binding to [TelemetryService]. */
    override fun onStart() {
        super.onStart()
        bindService(Intent(this, TelemetryService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    /** Called when the activity is stopping; unbinds from [TelemetryService]. */
    override fun onStop() {
        unbindService(connection)
        super.onStop()
    }

    /** Called when the activity is created; initializes display and Compose UI content. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val displayId = display?.displayId ?: -1

        setContent {
            val manager = managerState
            if (manager != null) {
                val telemetry by manager.state.collectAsStateWithLifecycle()
                ClusterHudScreen(state = telemetry, displayId = displayId)
            } else {
                ClusterHudScreen(state = ClusterTelemetryState(), displayId = displayId)
            }
        }
    }
}

/**
 * Composable function rendering the main instrument cluster HUD screen layout.
 *
 * @param state Current vehicle telemetry state.
 * @param displayId Identifier of the physical or virtual display rendering this UI.
 */
@Composable
fun ClusterHudScreen(state: ClusterTelemetryState, displayId: Int) {
    // Select color palette dynamically based on current drive mode (e.g. ECO, COMFORT, SPORT)
    val palette = paletteForDriveMode(state.driveMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Telltale & Gear Bar
        TopTelltaleAndGearBar(
            gearLabel = state.gearLabel,
            turnSignal = state.turnSignal,
            palette = palette
        )

        // 2. Main 3-Column Gauge & Vehicle Telemetry Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Dial: Speedometer
            CircularTelemetryGauge(
                value = state.speedKmh.toFloat(),
                maxValue = 240f,
                displayValueText = "${state.speedKmh}",
                unitLabel = "km/h",
                palette = palette,
                modifier = Modifier.fillMaxHeight(0.92f)
            )

            // Center: 4-Wheel TPMS Chassis Card
            CenterTpmsChassisCard(
                pressures = state.tirePressures,
                palette = palette,
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(0.86f)
            )

            // Right Dial: Tachometer (Redline starts at 6500 / 8000 = 0.8125f)
            CircularTelemetryGauge(
                value = state.rpm.toFloat(),
                maxValue = 8000f,
                displayValueText = String.format("%.1f", state.rpm / 1000f),
                unitLabel = "x1000 RPM",
                palette = palette,
                redlineStartRatio = 0.8125f,
                modifier = Modifier.fillMaxHeight(0.92f)
            )
        }

        // 3. Bottom Diagnostic & Mode Status Footer
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val sourceTag = when {
                state.isBenchOverrideActive -> "SOURCE: CID BENCH OVERRIDE"
                state.isVhalConnected -> "SOURCE: VHAL LIVE (60Hz)"
                else -> "SOURCE: INITIALIZING VHAL..."
            }
            Text(text = "DISPLAY ID: $displayId  |  $sourceTag", color = palette.textMuted, fontSize = 12.sp)
            Text(
                text = "MODE: ${state.driveMode.label}",
                color = palette.primaryAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
