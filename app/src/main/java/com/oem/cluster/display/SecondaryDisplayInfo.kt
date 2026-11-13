package com.oem.cluster.display

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import com.oem.cluster.ui.cluster.ClusterHudActivity

data class SecondaryDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int
)

object ClusterDisplayRouter {

    private const val TAG = "ClusterDisplayRouter"

    fun findClusterDisplay(context: Context): SecondaryDisplayInfo? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val secondary = dm.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY && display.state != Display.STATE_OFF
        } ?: return null

        return SecondaryDisplayInfo(
            displayId = secondary.displayId,
            name = secondary.name,
            width = secondary.mode.physicalWidth,
            height = secondary.mode.physicalHeight
        )
    }

    fun launchClusterOnSecondaryDisplay(context: Context): SecondaryDisplayInfo? {
        val target = findClusterDisplay(context)
        if (target == null) {
            Log.w(TAG, "No secondary cluster display found. Enable one in Emulator Extended Controls -> Displays.")
            return null
        }

        val intent = Intent(context, ClusterHudActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }

        val options = ActivityOptions.makeBasic().apply {
            launchDisplayId = target.displayId
        }

        context.startActivity(intent, options.toBundle())
        Log.i(TAG, "Launched ClusterHudActivity on displayId=${target.displayId} (${target.width}x${target.height})")
        return target
    }
}