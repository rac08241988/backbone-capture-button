package com.rattlehead.backbonecapture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

/** Invisible: started by the root script to toggle recording. Gets the screen-capture grant. */
class RecordActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        Log.i(TAG, "RecordActivity action=$action running=${RecordService.running}")
        if (action == ACTION_STOP || (action == ACTION_TOGGLE && RecordService.running)) {
            startService(Intent(this, RecordService::class.java).setAction(ACTION_STOP))
            finish()
            return
        }
        if (RecordService.running) { finish(); return }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), 1)
        } catch (t: Throwable) {
            Log.e(TAG, "cannot request screen capture", t)
            Notif.toast(this, "Could not start recording")
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, RecordService::class.java).setAction(ACTION_START)
                    .putExtra("code", resultCode).putExtra("data", data)
            )
        } else {
            Log.w(TAG, "screen capture not allowed (result $resultCode)")
            Notif.toast(this, "Screen recording was not allowed")
        }
        finish()
    }
}
