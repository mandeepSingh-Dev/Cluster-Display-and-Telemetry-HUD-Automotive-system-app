package com.oem.cluster.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Android Foreground/Background Service responsible for hosting and managing the lifecycle
 * of [ClusterTelemetryManager].
 *
 * Provides local IPC (Inter-Process Communication) via [TelemetryBinder] so that UI clients
 * (such as activities or cluster displays) can bind to the service and access real-time vehicle telemetry.
 */
class TelemetryService : Service() {

    /**
     * Custom IBinder implementation returned to clients during service binding.
     * Allows bound components to retrieve the shared [ClusterTelemetryManager] instance.
     */
    inner class TelemetryBinder : Binder() {
        /**
         * Returns the underlying [ClusterTelemetryManager] instance managing vehicle data and VHAL connections.
         *
         * @return The active [ClusterTelemetryManager].
         */
        fun getManager(): ClusterTelemetryManager = telemetryManager
    }

    /** Binder instance exposed to clients binding to this service. */
    private val binder = TelemetryBinder()

    /**
     * Central manager handling VHAL connections and telemetry state flows.
     * Initialized when the service is created.
     */
    lateinit var telemetryManager: ClusterTelemetryManager
        private set

    /**
     * Called when the service is first created.
     * Initializes [ClusterTelemetryManager] with the application context and initiates VHAL connection.
     */
    override fun onCreate() {
        super.onCreate()
        // Initialize the telemetry manager using the application context
        telemetryManager = ClusterTelemetryManager(applicationContext)
        // Establish connection to Android Automotive VHAL / Car service
        telemetryManager.connect()
    }

    /**
     * Called when a client attempts to bind to the service.
     *
     * @param intent The Intent that was used to bind to this service.
     * @return An [IBinder] through which clients can communicate with the service.
     */
    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Called when the service is being destroyed.
     * Disconnects the telemetry manager and cleans up VHAL callbacks and car service resources.
     */
    override fun onDestroy() {
        // Safely disconnect VHAL and clean up listeners
        telemetryManager.disconnect()
        super.onDestroy()
    }
}
