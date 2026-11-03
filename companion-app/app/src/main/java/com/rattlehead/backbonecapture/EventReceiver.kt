package com.rattlehead.backbonecapture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Root script -> "a screenshot was saved at <path>". Shows a toast and a notification. */
class EventReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_SHOT) return
        val path = intent.getStringExtra("path") ?: return
        Log.i(TAG, "screenshot event: $path")
        val pending = goAsync()
        val app = ctx.applicationContext
        Notif.toast(app, "Screenshot saved")
        val thumb = try {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
        } catch (t: Throwable) { null }
        val done = AtomicBoolean(false)
        val finish = { uri: android.net.Uri? ->
            if (done.compareAndSet(false, true)) {
                Notif.saved(app, "Screenshot saved", File(path).name, uri, "image/png", thumb)
                pending.finish()
            }
        }
        try {
            MediaScannerConnection.scanFile(app, arrayOf(path), arrayOf("image/png")) { _, uri -> finish(uri) }
        } catch (t: Throwable) {
            Log.w(TAG, "scan failed", t)
        }
        Handler(Looper.getMainLooper()).postDelayed({ finish(null) }, 6000)  // never wait forever
    }
}
