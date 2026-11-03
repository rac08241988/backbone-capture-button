package com.rattlehead.backbonecapture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Screen video (H.264 from a VirtualDisplay) + audio (AAC) -> MP4 in Movies/.
 * audioMode: "internal" = game/media sound (Android 10+ playback capture), "mic", or "none".
 */
class ScreenRecorder(private val ctx: Context, private val mp: MediaProjection, private val audioMode: String) {
    var uri: Uri? = null
    var name = ""
    var audioNote: String? = null

    private var pfd: ParcelFileDescriptor? = null
    private lateinit var muxer: MediaMuxer
    private var venc: MediaCodec? = null
    private var aenc: MediaCodec? = null
    private var arec: AudioRecord? = null
    private var vd: VirtualDisplay? = null
    private val lock = Object()
    private var vTrack = -1
    private var aTrack = -1
    private var muxing = false
    private var expectAudio = false
    private val pending = ArrayList<Triple<Boolean, ByteArray, MediaCodec.BufferInfo>>()
    @Volatile private var quit = false
    private var vThread: Thread? = null
    private var aThread: Thread? = null

    private companion object {
        const val RATE = 44100
        const val CH = 2
    }

    fun start() {
        name = "Backbone_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv)
            ?: throw IllegalStateException("cannot create file in Movies")
        pfd = ctx.contentResolver.openFileDescriptor(uri!!, "rw")
        muxer = MediaMuxer(pfd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        ctx.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(dm)
        var lastErr: Throwable? = null
        for (cap in intArrayOf(1920, 1280)) {
            try {
                val s = min(1.0, cap.toDouble() / max(dm.widthPixels, dm.heightPixels))
                val w = (dm.widthPixels * s).toInt() / 16 * 16
                val h = (dm.heightPixels * s).toInt() / 16 * 16
                val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 60)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                val e = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                try {
                    e.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                } catch (t: Throwable) { e.release(); throw t }
                val surface = e.createInputSurface()
                e.start()
                venc = e
                vd = mp.createVirtualDisplay("BackboneCapture", w, h, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
                Log.i(TAG, "video ${w}x$h")
                break
            } catch (t: Throwable) {
                Log.w(TAG, "video encoder at $cap failed", t)
                lastErr = t
            }
        }
        if (venc == null) throw lastErr ?: IllegalStateException("no video encoder")
        setupAudio()
        vThread = Thread({ videoLoop() }, "bbc-video").also { it.start() }
        if (arec != null) aThread = Thread({ audioLoop() }, "bbc-audio").also { it.start() }
    }

    private fun setupAudio() {
        if (audioMode == "none") { audioNote = "no audio"; return }
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioNote = "no audio: microphone permission missing"; return
        }
        var r: AudioRecord? = null
        try {
            val chMask = AudioFormat.CHANNEL_IN_STEREO
            val af = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE).setChannelMask(chMask).build()
            val minBuf = AudioRecord.getMinBufferSize(RATE, chMask, AudioFormat.ENCODING_PCM_16BIT)
            val b = AudioRecord.Builder().setAudioFormat(af).setBufferSizeInBytes(max(minBuf * 4, 32768))
            if (audioMode == "mic") {
                b.setAudioSource(MediaRecorder.AudioSource.MIC)
            } else {
                b.setAudioPlaybackCaptureConfig(
                    AudioPlaybackCaptureConfiguration.Builder(mp)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()
                )
            }
            r = b.build()
            if (r.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("audio input not ready")
            val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, CH).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val e = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            e.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            e.start()
            r.startRecording()
            arec = r
            aenc = e
            expectAudio = true
            Log.i(TAG, "audio: $audioMode")
        } catch (t: Throwable) {
            Log.w(TAG, "audio setup failed ($audioMode)", t)
            try { r?.release() } catch (_: Throwable) {}
            audioNote = "no audio: ${t.message}"
        }
    }

    private fun videoLoop() {
        val e = venc!!
        var after = 0
        while (!drain(e, true, 10_000)) {
            if (quit && ++after > 500) { Log.w(TAG, "video EOS timeout"); break }
        }
    }

    private fun audioLoop() {
        val r = arec!!
        val e = aenc!!
        val buf = ByteArray(8192)
        var startUs = -1L
        var bytes = 0L
        val bytesPerSec = RATE * CH * 2L
        try {
            while (!quit) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0) {
                    if (startUs < 0) startUs = System.nanoTime() / 1000 - n * 1_000_000L / bytesPerSec
                    var off = 0
                    while (off < n) {
                        val idx = e.dequeueInputBuffer(10_000)
                        if (idx < 0) { drain(e, false, 0); continue }
                        val ib = e.getInputBuffer(idx)!!
                        ib.clear()
                        val len = min(ib.remaining(), n - off)
                        ib.put(buf, off, len)
                        e.queueInputBuffer(idx, 0, len, startUs + bytes * 1_000_000L / bytesPerSec, 0)
                        bytes += len
                        off += len
                    }
                }
                drain(e, false, 0)
            }
            var idx: Int
            var tries = 0
            do { idx = e.dequeueInputBuffer(10_000); if (idx < 0) drain(e, false, 0) } while (idx < 0 && ++tries < 200)
            if (idx >= 0) e.queueInputBuffer(idx, 0, 0, startUs + bytes * 1_000_000L / bytesPerSec, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            tries = 0
            while (!drain(e, false, 10_000) && ++tries < 300) {}
        } catch (t: Throwable) {
            Log.e(TAG, "audio loop failed", t)
            synchronized(lock) { if (!muxing) { expectAudio = false; tryStartMuxer() } }
        }
    }

    /** Pulls encoded output; returns true at end of stream. */
    private fun drain(c: MediaCodec, video: Boolean, timeoutUs: Long): Boolean {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = c.dequeueOutputBuffer(info, timeoutUs)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> synchronized(lock) {
                    if (video) vTrack = muxer.addTrack(c.outputFormat) else aTrack = muxer.addTrack(c.outputFormat)
                    tryStartMuxer()
                }
                idx >= 0 -> {
                    val ob = c.getOutputBuffer(idx)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset); ob.limit(info.offset + info.size)
                        write(video, ob, info)
                    }
                    c.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
            }
        }
    }

    private fun tryStartMuxer() {
        if (muxing || vTrack < 0 || (expectAudio && aTrack < 0)) return
        muxer.start()
        muxing = true
        for ((v, data, i) in pending) {
            muxer.writeSampleData(if (v) vTrack else aTrack, ByteBuffer.wrap(data), i)
        }
        pending.clear()
    }

    private fun write(video: Boolean, ob: ByteBuffer, info: MediaCodec.BufferInfo) = synchronized(lock) {
        if (!muxing) {
            if (pending.size < 3000) {
                val data = ByteArray(info.size); ob.get(data)
                val copy = MediaCodec.BufferInfo().apply { set(0, data.size, info.presentationTimeUs, info.flags) }
                pending.add(Triple(video, data, copy))
            }
            return@synchronized
        }
        if (video || aTrack >= 0) muxer.writeSampleData(if (video) vTrack else aTrack, ob, info)
    }

    /** Stops and finalizes. Returns true when a playable file was saved. */
    fun stop(): Boolean {
        quit = true
        try { venc?.signalEndOfInputStream() } catch (t: Throwable) { Log.w(TAG, "EOS", t) }
        vThread?.join(8000)
        aThread?.join(8000)
        try { vd?.release() } catch (_: Throwable) {}
        for (c in listOf(venc, aenc)) {
            try { c?.stop() } catch (_: Throwable) {}
            try { c?.release() } catch (_: Throwable) {}
        }
        try { arec?.stop() } catch (_: Throwable) {}
        try { arec?.release() } catch (_: Throwable) {}
        var ok: Boolean
        synchronized(lock) {
            ok = muxing
            try { if (muxing) muxer.stop() } catch (t: Throwable) { Log.e(TAG, "muxer stop", t); ok = false }
            try { muxer.release() } catch (_: Throwable) {}
        }
        try { pfd?.close() } catch (_: Throwable) {}
        val u = uri ?: return false
        if (ok) {
            ctx.contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } else {
            try { ctx.contentResolver.delete(u, null, null) } catch (_: Throwable) {}
            uri = null
        }
        Log.i(TAG, "recording stopped ok=$ok $name")
        return ok
    }
}
