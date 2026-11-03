package com.rattlehead.backbonecapture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/** Simple settings/status screen. The capture button itself is handled by the Magisk module. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun text(s: String, size: Float = 15f) = TextView(this).apply { text = s; textSize = size; setPadding(0, pad / 2, 0, pad / 2) }

        col.addView(text("Backbone Capture", 22f))
        col.addView(text("Companion app for the Backbone Capture Button Magisk module.\n\n" +
            "Capture button: press = screenshot, hold = start/stop recording.\n" +
            "This app shows the pop-ups and notifications and records the screen with sound."))
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        col.addView(text("Audio permission: " + if (mic) "granted" else "missing (the module grants it at boot)"))

        col.addView(text("Recording audio", 18f))
        val group = RadioGroup(this)
        val opts = listOf("internal" to "Game / phone sound (recommended)", "mic" to "Microphone", "none" to "No audio")
        val cur = prefs.getString("audio", "internal")
        opts.forEachIndexed { i, (key, label) ->
            group.addView(RadioButton(this).apply { id = 100 + i; text = label; isChecked = key == cur })
        }
        group.setOnCheckedChangeListener { _, id ->
            prefs.edit().putString("audio", opts[id - 100].first).apply()
        }
        col.addView(group)
        col.addView(text("Some games block recording their sound; then the video has no game audio."))

        col.addView(Button(this).apply {
            text = "Test notification"
            setOnClickListener {
                Notif.toast(this@MainActivity, "Screenshot saved")
                Notif.saved(this@MainActivity, "Screenshot saved", "(test)", null, "image/png", null)
            }
        })
        col.addView(text("Unofficial. Not affiliated with Backbone.\nMade by Rattlehead", 13f))
        setContentView(ScrollView(this).apply { addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) })
    }
}
