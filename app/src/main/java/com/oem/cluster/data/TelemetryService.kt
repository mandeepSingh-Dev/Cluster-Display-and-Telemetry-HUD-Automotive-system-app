package com.oem.cluster.data

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.oem.cluster.service.ClusterTelemetryManager

class TelemetryService : Service() {

    inner class TelemetryBinder : Binder() {
        fun getManager(): ClusterTelemetryManager = telemetryManager
    }

    private val binder = TelemetryBinder()
    lateinit var telemetryManager: ClusterTelemetryManager
        private set

    override fun onCreate() {
        super.onCreate()
        telemetryManager = ClusterTelemetryManager(applicationContext)
        telemetryManager.connect()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        telemetryManager.disconnect()
        super.onDestroy()
    }
}