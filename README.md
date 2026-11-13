# Cluster (Digital Instrument & Telemetry HUD) App for Android Automotive OS (AAOS)

An advanced, production-grade Android Automotive OS (AAOS) Digital Instrument Cluster and Telemetry HUD application built with **Jetpack Compose**, **Kotlin Coroutines (StateFlow)**, and **AAOS VHAL (`CarPropertyManager`)**.

---

## 🏗️ Architecture & Execution Flow

The application follows a decoupled client-server architecture tailored for multi-display automotive head units (e.g., Center Information Display on Display 0 and Instrument Cluster HUD on Display 1).

### High-Level Architecture Diagram (Who Talks to Whom)

```text
[1. CidControllerActivity] (Display 0 - Infotainment)
       │
       ├──► Calls ClusterDisplayRouter.launchClusterOnSecondaryDisplay()
       │          └──► Launches [2. ClusterHudActivity] onto Display 1+ (Cluster Screen)
       │
       └──► Both Activities bind via IPC (bindService) to:
                  ▼
       [3. TelemetryService] (Background Bound Service)
                  │
                  └──► Owns 1 instance of [4. ClusterTelemetryManager]
                             ├──► Connects to Android Car Service (Car.createCar)
                             ├──► Subscribes to VHAL via CarPropertyManager.registerCallback()
                             ├──► Merges VHAL events OR CID Simulator slider overrides
                             └──► Emits StateFlow<ClusterTelemetryState> to BOTH screens
```

### Step-by-Step Flow

```mermaid
graph TD
    A[OS Boot / Launcher] --> B[TelemetryService.onCreate]
    B --> C[ClusterTelemetryManager.connect]
    C --> D[Car.createCar & VHAL Subscriptions]
    D --> E[CidControllerActivity - Display 0]
    D --> F[ClusterHudActivity - Display 1]
    E -->|ServiceConnection / IBinder| B
    F -->|ServiceConnection / IBinder| B
    E -->|CID Bench Simulator Sliders| G[_state MutableStateFlow]
    D -->|CarPropertyEventCallback (60Hz)| G
    G -->|collectAsStateWithLifecycle| H[Compose UI Gauges & TPMS]
```

1. **Service Initialization (`TelemetryService.kt`)**:
   - When the app starts, [TelemetryService](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/service/TelemetryService.kt#L1-L70) starts in the background and initializes `ClusterTelemetryManager`.
   - Exposes a local IPC binder (`TelemetryBinder`) allowing UI activities to bind and retrieve live telemetry.

2. **VHAL Telemetry Manager (`ClusterTelemetryManager.kt`)**:
   - [ClusterTelemetryManager](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/service/ClusterTelemetryManager.kt#L29-L330) connects to AAOS via `Car.createCar(...)`.
   - Subscribes to vehicle properties (`PERF_VEHICLE_SPEED`, `ENGINE_RPM`, `CURRENT_GEAR`, `TURN_SIGNAL_STATE`, `TIRE_PRESSURE`) through `CarPropertyManager`.
   - Updates a reactive `MutableStateFlow<ClusterTelemetryState>` at 60Hz / on-change.
   - Provides CID bench simulator overrides (`updateSimulatedSpeed`, `updateSimulatedRpm`, etc.) for testing on non-AAOS environments.

3. **Center Information Display - CID Controller (`CidControllerActivity.kt`)**:
   - Runs on **Display 0** (Main Center Screen).
   - Automatically projects the digital cluster onto **Display 1** (Instrument Cluster Screen) using [ClusterDisplayRouter](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/display/ClusterDisplayRouter.kt).
   - Provides interactive sliders and toggles to simulate vehicle speed, RPM, gear selection, turn signals, and TPMS pressure alerts.

4. **Digital Instrument Cluster HUD (`ClusterHudActivity.kt`)**:
   - Runs on **Display 1** (Instrument Cluster).
   - Collects telemetry state reactively via `collectAsStateWithLifecycle()`.
   - Renders dual circular telemetry gauges ([CircularTelemetryGauge](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/ui/cluster/CircularTelemetryGauge.kt)), a 4-wheel TPMS chassis wireframe card, gear indicator, and turn signal indicators.

---

## 📂 Key Classes & Function Call Flow

| Component / File | Class Name | Key Functions & Lines | Description |
| :--- | :--- | :--- | :--- |
| [TelemetryService.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/service/TelemetryService.kt) | `TelemetryService` | `onCreate()`, `onBind()`, `onDestroy()` | Hosts background telemetry service & binder. |
| [ClusterTelemetryManager.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/service/ClusterTelemetryManager.kt) | `ClusterTelemetryManager` | `connect()`, `registerVhalSubscriptions()`, `updateSimulatedSpeed()` | Manages VHAL callbacks & state flows. |
| [CidControllerActivity.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/ui/cluster/CidControllerActivity.kt) | `CidControllerActivity` | `onCreate()`, `CidSimulatorScreen()` | CID Dashboard (Display 0) for bench controls. |
| [ClusterHudActivity.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/ui/cluster/ClusterHudActivity.kt) | `ClusterHudActivity` | `onCreate()`, `ClusterHudScreen()` | Instrument Cluster HUD (Display 1). |
| [CircularTelemetryGauge.kt](file:///C:/Users/Lenovo/AndroidStudioProjects/ClusterDigitalInstrumentAndTelemetryHUDapp/app/src/main/java/com/oem/cluster/ui/cluster/CircularTelemetryGauge.kt) | Composable Functions | `CircularTelemetryGauge()`, `TopTelltaleAndGearBar()`, `CenterTpmsChassisCard()` | Draws animated speed/RPM dials, gear bar, and TPMS chassis. |

---

## 🚗 Setting Up Multi-Display in Android Emulator

To test the dual-display AAOS setup (Display 0 for CID Controller, Display 1 for Cluster HUD):

1. Open **Android Studio Emulator Extended Controls**.
2. Navigate to **Displays**.
3. Click **Add Secondary Display** and set the type to **Secondary or Car Cluster / Instrument Cluster** (e.g., 1920x720 resolution).
4. Restart or launch the emulator.

---

## 🛠️ Useful ADB Commands

### 1. Build and Install APK
```bash
# Build debug APK
./gradlew assembleDebug

# Install APK onto emulator/device
adb install -r app/build/outputs/apk/debug/Cluster.apk
```

### 2. Launch Activities
```bash
# Launch CID Controller Activity on Display 0
adb shell am start -n com.oem.cluster/.ui.cluster.CidControllerActivity

# Launch Cluster HUD Activity on Display 1 (Secondary Display)
adb shell am start -n com.oem.cluster/.ui.cluster.ClusterHudActivity --display 1
```

### 3. VHAL Simulation & Debugging (Car Property Manager CLI)
```bash
# List all car properties
adb shell cmd car_service get-property-list

# Simulate vehicle speed (PERF_VEHICLE_SPEED in m/s, e.g. 25.0 m/s = 90 km/h)
adb shell cmd car_service inject-vhal-event PERF_VEHICLE_SPEED 0 25.0

# Simulate engine RPM (ENGINE_RPM)
adb shell cmd car_service inject-vhal-event ENGINE_RPM 0 3500.0

# Simulate gear position (CURRENT_GEAR, 4 = Drive / GEAR_DRIVE)
adb shell cmd car_service inject-vhal-event CURRENT_GEAR 0 4
```

### 4. Logcat Filtering
```bash
# Monitor VHAL and Cluster telemetry logs in real-time
adb logcat -s ClusterTelemetryMgr ClusterHudActivity CidControllerActivity
```
