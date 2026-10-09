package com.smartstaffcall

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * PROTOTYPE: shows a LOCAL notification on this phone only, to demonstrate sound/vibration/tap behaviour.
 * It is NOT a remote push. Real delivery to another phone needs Firebase Cloud Messaging.
 */
object Notifier {
    private const val CHANNEL = "staff_calls"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Staff call requests (simulated)", NotificationManager.IMPORTANCE_HIGH)
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 400, 200, 400)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun show(ctx: Context, callId: String, staffName: String) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(ctx)
            val intent = Intent(ctx, MainActivity::class.java)
                .putExtra("callId", callId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                ctx, callId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("[SIMULATED] Call request: $staffName")
                .setContentText("Tap to open this call. Local test only, not a real remote push.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(callId.hashCode(), n)
        } catch (e: Exception) {
            // Notifications are optional in the prototype; never crash the app because of them.
        }
    }

    fun clear(ctx: Context, callId: String) {
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(callId.hashCode())
        } catch (e: Exception) { }
    }
}
