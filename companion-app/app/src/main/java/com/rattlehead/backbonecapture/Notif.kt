package com.rattlehead.backbonecapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

const val TAG = "BackboneCapture"
const val PKG = "com.rattlehead.backbonecapture"
const val ACTION_SHOT = "$PKG.SHOT_SAVED"
const val ACTION_TOGGLE = "$PKG.TOGGLE"
const val ACTION_START = "$PKG.START"
const val ACTION_STOP = "$PKG.STOP"
const val CH_EVENTS = "saved"
const val CH_REC = "recording"

object Notif {
    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_EVENTS, "Screenshot / recording saved", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REC, "Recording in progress", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
        Log.i(TAG, "toast: $text")
    }

    private fun viewIntent(ctx: Context, uri: Uri?, mime: String, req: Int): PendingIntent? {
        uri ?: return null
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return PendingIntent.getActivity(
            ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** "Screenshot saved" / "Recording saved": heads-up, stays in the shade, tap opens the file. */
    fun saved(ctx: Context, title: String, name: String, uri: Uri?, mime: String, thumb: Bitmap?) {
        try {
            channels(ctx)
            val id = (System.currentTimeMillis() and 0x3fffffff).toInt()
            val b = Notification.Builder(ctx, CH_EVENTS)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(if (uri != null) "$name - tap to open" else name)
                .setShowWhen(true)
                .setAutoCancel(false)
            viewIntent(ctx, uri, mime, id)?.let { b.setContentIntent(it) }
            if (thumb != null) {
                b.setLargeIcon(thumb).setStyle(Notification.BigPictureStyle().bigPicture(thumb))
            }
            ctx.getSystemService(NotificationManager::class.java).notify(id, b.build())
            Log.i(TAG, "notification posted: $title $name uri=$uri")
        } catch (t: Throwable) {
            Log.e(TAG, "notification failed: $title", t)
        }
    }
}
