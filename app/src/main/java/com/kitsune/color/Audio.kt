package com.kitsune.color

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min

/** Microfone do celular: o volume vira cor (silêncio = azul, alto = vermelho). */
class PhoneMic(private val led: LedController) {
    @Volatile private var running = false
    @Volatile var sens = 50

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2)
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        running = true
        led.micDevice(false)
        Thread {
            val buf = ShortArray(rate / 10)
            var lv = 0f
            var last = 0L
            rec.startRecording()
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var pk = 0
                for (i in 0 until n) {
                    val a = abs(buf[i].toInt())
                    if (a > pk) pk = a
                }
                val l = min(1f, pk / 32768f * 2f * (sens / 50f))
                lv += (l - lv) * 0.35f
                val now = SystemClock.uptimeMillis()
                if (now - last >= 200) {
                    last = now
                    led.voiceColor((240 - lv * 240).toInt(), 1000)
                }
            }
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }.start()
        return true
    }

    fun stop() { running = false }
}

/** Estado e controle da tela de microfone (aparelho ou celular). */
class MicController(private val led: LedController) {
    var source by mutableIntStateOf(0) // 0 = aparelho, 1 = celular
    var on by mutableStateOf(false)
    var fx by mutableIntStateOf(1)
    var sensDev by mutableIntStateOf(50)
    var sensPhone by mutableIntStateOf(50)
    private val phone = PhoneMic(led)
    private var devOn = false

    fun apply(): Boolean {
        stopAll()
        if (!on) return true
        if (source == 0) {
            devOn = true
            led.micDevice(true)
            led.micEffect(fx)
            led.micSens(sensDev)
            return true
        }
        phone.sens = sensPhone
        val ok = phone.start()
        if (!ok) on = false
        return ok
    }

    fun phoneSens(v: Int) { phone.sens = v }

    private fun stopAll() {
        phone.stop()
        if (devOn) {
            devOn = false
            led.micDevice(false)
        }
    }

    fun stop() {
        on = false
        stopAll()
    }
}

data class Track(val id: Long, val title: String, val artist: String, val durMs: Long, val uri: Uri)

/** Player de músicas do aparelho; as luzes reagem à frequência dominante e ao volume (Visualizer). */
class MusicPlayer(private val ctx: Context, private val led: LedController, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    val tracks = mutableStateListOf<Track>()
    var index by mutableIntStateOf(-1)
        private set
    var playing by mutableStateOf(false)
        private set
    var pos by mutableIntStateOf(0)
        private set
    var dur by mutableIntStateOf(0)
        private set
    var vary by mutableStateOf(false)
    var loaded by mutableStateOf(false)
        private set
    var vizMissing by mutableStateOf(false)
        private set

    private var mp: MediaPlayer? = null
    private var viz: Visualizer? = null
    private var level = 0f
    private var lastSend = 0L
    private var tick = 0

    fun load() {
        if (loaded) return
        loaded = true
        Thread {
            val out = ArrayList<Track>()
            val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
            )
            try {
                ctx.contentResolver.query(
                    base, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val artist = c.getString(2)?.takeIf { it != "<unknown>" } ?: ""
                        out.add(Track(id, c.getString(1) ?: "Sem título", artist, c.getLong(3), ContentUris.withAppendedId(base, id)))
                    }
                }
            } catch (_: Exception) {
            }
            main.post { tracks.clear(); tracks.addAll(out) }
        }.start()
    }

    fun play(i: Int) {
        if (tracks.isEmpty()) return
        val idx = (i + tracks.size) % tracks.size
        release()
        index = idx
        pos = 0
        dur = (tracks[idx].durMs).toInt()
        val m = MediaPlayer()
        try {
            m.setDataSource(ctx, tracks[idx].uri)
            m.setOnPreparedListener {
                it.start()
                dur = it.duration
                playing = true
                attachViz(it.audioSessionId)
            }
            m.setOnCompletionListener {
                if (prefs.autoNext.value && tracks.size > 1) next() else { playing = false; stopViz() }
            }
            m.setOnErrorListener { _, _, _ -> playing = false; true }
            m.prepareAsync()
            mp = m
        } catch (e: Exception) {
            m.release()
        }
    }

    fun toggle() {
        val m = mp
        if (m == null) { play(if (index < 0) 0 else index); return }
        if (m.isPlaying) { m.pause(); playing = false; stopViz() } else { m.start(); playing = true; attachViz(m.audioSessionId) }
    }

    fun next() = play(if (index < 0) 0 else index + 1)
    fun prev() { if (pos > 3000) seek(0) else play(if (index < 0) 0 else index - 1) }

    fun seek(ms: Int) { mp?.seekTo(ms); pos = ms }
    fun refresh() { mp?.let { pos = it.currentPosition } }

    fun stop() {
        release()
        playing = false
    }

    private fun release() {
        stopViz()
        mp?.let { try { it.release() } catch (_: Exception) {} }
        mp = null
        playing = false
    }

    private fun stopViz() {
        viz?.let { try { it.setEnabled(false); it.release() } catch (_: Exception) {} }
        viz = null
    }

    private fun attachViz(session: Int) {
        stopViz()
        try {
            val v = Visualizer(session)
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, wave: ByteArray?, rate: Int) {
                    if (wave == null) return
                    var m = 0
                    for (b in wave) {
                        val a = abs((b.toInt() and 255) - 128)
                        if (a > m) m = a
                    }
                    level = m / 128f
                }

                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft == null || fft.size < 4) return
                    val now = SystemClock.uptimeMillis()
                    if (now - lastSend < 100) return
                    lastSend = now
                    var best = 1
                    var mx = 0.0
                    for (k in 1 until fft.size / 2) {
                        val mag = hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble())
                        if (mag > mx) { mx = mag; best = k }
                    }
                    val hz = best * (rate / 1000.0) / fft.size
                    val h = (300.0 * ln(maxOf(hz, 40.0) / 40.0) / ln(100.0)).toInt().coerceIn(0, 300)
                    led.colorRaw(h, 1000)
                    if (vary && (++tick % 2 == 0)) led.brightRaw(100 + (level * 900).toInt())
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true)
            v.setEnabled(true)
            viz = v
            vizMissing = false
        } catch (e: Exception) {
            viz = null
            vizMissing = true
        }
    }
}
