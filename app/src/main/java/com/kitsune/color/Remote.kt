package com.kitsune.color

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import android.widget.RemoteViews

/** Ponte entre o app aberto e os controles externos (notificação e widget). */
object Hub {
    @Volatile var box: Container? = null
}

object Frames {
    fun frame(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    fun power(on: Boolean) = frame(0xBC, 0x01, 0x01, if (on) 1 else 0, 0x55)
    fun color(h: Int, s: Int) = frame(0xBC, 0x04, 0x06, h shr 8, h and 255, s shr 8, s and 255, 0, 0, 0x55)
    fun bright(v: Int) = frame(0xBC, 0x05, 0x06, v shr 8, v and 255, 0, 0, 0, 0, 0x55)
}

object Ctl {
    const val POWER = "com.kitsune.color.POWER"
    const val COLOR = "com.kitsune.color.COLOR"
    const val BRIGHT = "com.kitsune.color.BRIGHT"
    const val BRIGHT_STEP = "com.kitsune.color.BRIGHT_STEP"
    const val MUSIC_TOGGLE = "com.kitsune.color.MUSIC_TOGGLE"
    const val MUSIC_NEXT = "com.kitsune.color.MUSIC_NEXT"
    const val MIC_STOP = "com.kitsune.color.MIC_STOP"
    const val LIVE_DISMISSED = "com.kitsune.color.LIVE_DISMISSED"
}

/** Conexão rápida só para mandar alguns comandos (usada quando o app não está conectado). */
@SuppressLint("MissingPermission")
class LedRemote(private val ctx: Context, private val frames: List<ByteArray>, private val done: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var wr: BluetoothGattCharacteristic? = null
    private var i = 0
    private var finished = false

    fun start(): Boolean {
        val addr = ctx.getSharedPreferences("led", Context.MODE_PRIVATE).getString("addr", null) ?: return false
        val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val dev = try { mgr.adapter?.getRemoteDevice(addr) } catch (e: Exception) { null } ?: return false
        main.postDelayed({ finish() }, 9000)
        gatt = try { dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) } catch (e: Exception) { null }
        if (gatt == null) { finished = true; return false }
        return true
    }

    private fun finish() {
        if (finished) return
        finished = true
        try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
        gatt = null
        done()
    }

    private fun next() {
        val g = gatt ?: return
        val c = wr ?: return
        if (i >= frames.size) { main.postDelayed({ finish() }, 150); return }
        val d = frames[i++]
        val type = if ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, d, type) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { c.writeType = type; c.value = d; g.writeCharacteristic(c) }
        }
        if (!ok) finish()
    }

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) main.post { finish() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            wr = g.getService(LedController.SVC)?.getCharacteristic(LedController.WRC)
            if (wr == null) main.post { finish() } else next()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post { next() }
        }
    }
}

/** Recebe os toques da notificação e do widget. */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val sp = app.getSharedPreferences("led", Context.MODE_PRIVATE)
        val live = Hub.box?.led
        val connected = live != null && live.link == LedController.Link.ON
        val frames = ArrayList<ByteArray>()
        when (intent.action) {
            Ctl.POWER -> {
                val on = !(if (live != null && connected) live.power else sp.getBoolean("pwr", true))
                sp.edit().putBoolean("pwr", on).apply()
                if (live != null && connected) live.changePower(on) else frames.add(Frames.power(on))
            }
            Ctl.COLOR -> {
                val h = intent.getIntExtra("h", 0)
                val s = intent.getIntExtra("s", 1000)
                sp.edit().putInt("h", h).putInt("s", s).apply()
                if (live != null && connected) live.setColor(h, s) else frames.add(Frames.color(h, s))
            }
            Ctl.BRIGHT -> {
                val v = intent.getIntExtra("v", 1000)
                sp.edit().putInt("bri", v).apply()
                if (live != null && connected) live.changeBright(v) else frames.add(Frames.bright(v))
            }
            Ctl.BRIGHT_STEP -> {
                val cur = if (live != null && connected) live.bright else sp.getInt("bri", 1000)
                val v = (cur + intent.getIntExtra("d", 0)).coerceIn(0, 1000)
                sp.edit().putInt("bri", v).apply()
                if (live != null && connected) live.changeBright(v) else frames.add(Frames.bright(v))
            }
            Ctl.MUSIC_TOGGLE -> Hub.box?.music?.toggle()
            Ctl.MUSIC_NEXT -> Hub.box?.music?.next()
            Ctl.MIC_STOP -> Hub.box?.mic?.stop()
            Ctl.LIVE_DISMISSED -> Surfaces.liveDismissed = true
        }
        Surfaces.refresh(app)
        if (!connected && frames.isNotEmpty()) {
            val pending = goAsync()
            val ok = LedRemote(app, frames) { pending.finish() }.start()
            if (!ok) pending.finish()
        }
    }
}

/** Widget redimensionável: ligar/desligar, brilho e cores favoritas. */
class ControlWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        mgr.updateAppWidget(ids, Surfaces.widgetViews(ctx))
    }
}

object Surfaces {
    private const val CH = "control"
    private val LEVELS = intArrayOf(200, 400, 600, 800, 1000)

    private fun favorites(ctx: Context, n: Int): List<Pair<Int, Int>> {
        val all = Recents(ctx).list
        val src = all.filter { it.pinned } + all.filter { !it.pinned }
        val out = src.map { it.h to it.s }.take(n).toMutableList()
        val defaults = listOf(0 to 1000, 120 to 1000, 240 to 1000, 55 to 1000, 300 to 1000)
        var k = 0
        while (out.size < n) out.add(defaults[k++ % defaults.size])
        return out
    }

    private fun pi(ctx: Context, code: Int, action: String, vararg extras: Pair<String, Int>): PendingIntent {
        val i = Intent(ctx, ControlReceiver::class.java).setAction(action)
        extras.forEach { i.putExtra(it.first, it.second) }
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun rgb(h: Int, s: Int): Int =
        android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0, 360).toFloat(), (s / 1000f).coerceIn(0f, 1f), 1f))

    private fun bind(ctx: Context, rv: RemoteViews, power: Int, colors: IntArray, bars: IntArray, base: Int) {
        val sp = ctx.getSharedPreferences("led", Context.MODE_PRIVATE)
        val on = sp.getBoolean("pwr", true)
        val bri = sp.getInt("bri", 1000)
        rv.setInt(power, "setColorFilter", if (on) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt())
        rv.setOnClickPendingIntent(power, pi(ctx, base, Ctl.POWER))
        val fav = favorites(ctx, colors.size)
        colors.forEachIndexed { i, id ->
            rv.setInt(id, "setColorFilter", rgb(fav[i].first, fav[i].second))
            rv.setOnClickPendingIntent(id, pi(ctx, base + 10 + i, Ctl.COLOR, "h" to fav[i].first, "s" to fav[i].second))
        }
        bars.forEachIndexed { i, id ->
            rv.setInt(id, "setColorFilter", if (bri >= LEVELS[i] - 100) 0xFFFFA726.toInt() else 0x66888888)
            rv.setOnClickPendingIntent(id, pi(ctx, base + 20 + i, Ctl.BRIGHT, "v" to LEVELS[i]))
        }
    }

    fun widgetViews(ctx: Context): RemoteViews {
        val small = RemoteViews(ctx.packageName, R.layout.widget_small)
        bind(ctx, small, R.id.w_power, intArrayOf(R.id.w_c0, R.id.w_c1, R.id.w_c2), IntArray(0), 300)
        val wide = RemoteViews(ctx.packageName, R.layout.widget_wide)
        bind(
            ctx, wide, R.id.w_power,
            intArrayOf(R.id.w_c0, R.id.w_c1, R.id.w_c2, R.id.w_c3, R.id.w_c4),
            intArrayOf(R.id.w_b0, R.id.w_b1, R.id.w_b2, R.id.w_b3, R.id.w_b4), 400,
        )
        return RemoteViews(mapOf(SizeF(100f, 100f) to small, SizeF(220f, 100f) to wide))
    }

    /** Atualiza a notificação e o widget (chamado com atraso para economizar bateria). */
    fun refresh(ctx: Context) {
        notification(ctx)
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, ControlWidget::class.java))
        if (ids.isNotEmpty()) mgr.updateAppWidget(ids, widgetViews(ctx))
    }

    fun cancelNotification(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(1)
        nm.cancel(2)
        liveDismissed = false
    }

    /** Se o usuário dispensou o Live Update, não o publicamos de novo até a sessão terminar. */
    @Volatile var liveDismissed = false

    /**
     * Live Update (Android 16+). A Now Bar da Samsung (One UI 8) mostra essas notificações promovidas.
     * Precisa ser padrão (sem RemoteViews), em andamento, com título e pedir a promoção.
     * kind: 0 = nenhuma sessão, 1 = música sincronizada, 2 = microfone sincronizado, 3 = conectado.
     */
    fun live(ctx: Context, kind: Int, title: String, text: String, since: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (kind == 0) {
            nm.cancel(2)
            liveDismissed = false
            return
        }
        if (liveDismissed) return
        nm.createNotificationChannel(
            NotificationChannel("live", "Sessão de luz (Now Bar)", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val icon = Icon.createWithResource(ctx, R.drawable.ic_stat)
        fun act(label: String, code: Int, action: String, vararg ex: Pair<String, Int>) =
            Notification.Action.Builder(icon, label, pi(ctx, code, action, *ex)).build()
        val b = Notification.Builder(ctx, "live")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(since)
            .setUsesChronometer(true)
            .setContentIntent(open)
            .setDeleteIntent(pi(ctx, 900, Ctl.LIVE_DISMISSED))
            .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        when (kind) {
            1 -> {
                b.addAction(act("Pausar", 910, Ctl.MUSIC_TOGGLE))
                b.addAction(act("Próxima", 911, Ctl.MUSIC_NEXT))
            }
            2 -> b.addAction(act("Parar", 920, Ctl.MIC_STOP))
            else -> {
                b.addAction(act("Ligar/Desligar", 930, Ctl.POWER))
                b.addAction(act("Brilho −", 931, Ctl.BRIGHT_STEP, "d" to -200))
                b.addAction(act("Brilho +", 932, Ctl.BRIGHT_STEP, "d" to 200))
            }
        }
        nm.notify(2, b.build())
    }

    private fun notification(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("notif", true)) {
            nm.cancel(1)
            return
        }
        nm.createNotificationChannel(
            NotificationChannel(CH, "Controle da fita LED", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
        )
        val big = RemoteViews(ctx.packageName, R.layout.notif_big)
        bind(
            ctx, big, R.id.n_power,
            intArrayOf(R.id.n_c0, R.id.n_c1, R.id.n_c2, R.id.n_c3, R.id.n_c4),
            intArrayOf(R.id.n_b0, R.id.n_b1, R.id.n_b2, R.id.n_b3, R.id.n_b4), 100,
        )
        val small = RemoteViews(ctx.packageName, R.layout.notif_small)
        bind(ctx, small, R.id.n_power, IntArray(0), IntArray(0), 200)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
        nm.notify(1, n)
    }
}
