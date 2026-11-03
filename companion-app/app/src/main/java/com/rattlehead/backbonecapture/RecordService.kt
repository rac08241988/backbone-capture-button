package com.rattlehead.backbonecapture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/** Foreground service that owns the screen recording while it runs (ongoing notification + Stop). */
class RecordService : Service() {
    companion object {
        @Volatile var running = false
        private const val NID = 1
    }

    private var projection: MediaProjection? = null
    private var recorder: ScreenRecorder? = null
    private var stopping = false
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_STOP -> stop()
            else -> if (!running) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ongoing(): Notification {
        Notif.channels(this)
        val stopPi = PendingIntent.getService(
            this, 2, Intent(this, RecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CH_REC)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Recording started")
            .setContentText("Hold the capture button again (or tap Stop) to save")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
            .build()
    }

    private fun start(intent: Intent) {
        if (running) return
        try {
            startForeground(NID, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground with type failed, retrying without", t)
            startForeground(NID, ongoing())
        }
        running = true
        stopping = false
        try {
            val code = intent.getIntExtra("code", 0)
            @Suppress("DEPRECATION")
            val data = intent.getParcelableExtra<Intent>("data")!!
            val mp = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
                ?: throw IllegalStateException("no MediaProjection")
            projection = mp
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { main.post { stop() } }
            }, main)
            val audio = getSharedPreferences("settings", Context.MODE_PRIVATE).getString("audio", "internal") ?: "internal"
            val r = ScreenRecorder(this, mp, audio)
            r.start()
            recorder = r
            Notif.toast(this, if (r.audioNote != null) "Recording started (${r.audioNote})" else "Recording started")
            Log.i(TAG, "recording started: ${r.name} audio=$audio note=${r.audioNote}")
        } catch (t: Throwable) {
            Log.e(TAG, "could not start recording", t)
            Notif.toast(this, "Could not start recording: ${t.message}")
            recorder?.let { try { it.stop() } catch (_: Throwable) {} }
            recorder = null
            finishService()
        }
    }

    private fun stop() {
        if (stopping) return
        val r = recorder
        if (r == null) { if (!running) stopSelf(); return }
        stopping = true
        recorder = null
        Thread {
            val ok = try { r.stop() } catch (t: Throwable) { Log.e(TAG, "stop failed", t); false }
            try { projection?.stop() } catch (_: Throwable) {}
            projection = null
            main.post {
                if (ok) {
                    Notif.toast(this, "Recording saved")
                    Notif.saved(this, "Recording saved", r.name, r.uri, "video/mp4", null)
                } else {
                    Notif.toast(this, "Recording failed to save")
                }
                finishService()
            }
        }.start()
    }

    private fun finishService() {
        running = false
        try { projection?.stop() } catch (_: Throwable) {}
        projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
