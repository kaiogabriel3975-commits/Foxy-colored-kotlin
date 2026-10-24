package com.kitsune.color

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.UUID
import kotlin.math.cos

data class Found(val address: String, val name: String, val rssi: Int, val device: BluetoothDevice)

/** Um agendamento do aparelho (ligar ou desligar). Dias na ordem seg..dom. */
class Sch(h: Int, m: Int) {
    var en by mutableStateOf(false)
    val days = mutableStateListOf(true, true, true, true, true, true, true)
    var h by mutableIntStateOf(h)
    var m by mutableIntStateOf(m)

    fun mask(): Int {
        var x = 0
        for (i in 0..6) if (days[i]) x = x or (1 shl i)
        return x
    }

    fun summary(): String {
        if (!en) return "Desativado"
        val d = days.joinToString("") { if (it) "1" else "0" }
        val names = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
        val x = when (d) {
            "1111111" -> "todos os dias"
            "1111100" -> "dias úteis"
            "0000011" -> "fim de semana"
            "0000000" -> "sem dias"
            else -> names.filterIndexed { i, _ -> days[i] }.joinToString(" ")
        }
        return "%02d:%02d, %s".format(h, m, x)
    }
}

/**
 * Conversa com a fita LED pelo protocolo BLE (serviço FFF0, escrita em FFF3, notificações em FFF4).
 * Quadro: BC <cmd> <tamanho> <dados...> 55
 */
@SuppressLint("MissingPermission")
class LedController(private val ctx: Context, private val prefs: Prefs) {
    enum class Link { OFF, SCANNING, CONNECTING, ON }

    private val sp = ctx.getSharedPreferences("led", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    var link by mutableStateOf(Link.OFF)
        private set
    var deviceName by mutableStateOf("")
        private set
    var message by mutableStateOf("")
        private set
    val found = mutableStateListOf<Found>()
    val rxLog = mutableStateListOf<String>()

    var hue by mutableIntStateOf(sp.getInt("h", 0))
        private set
    var sat by mutableIntStateOf(sp.getInt("s", 1000))
        private set
    var bright by mutableIntStateOf(1000)
        private set
    var power by mutableStateOf(true)
        private set

    val schOn = Sch(7, 0)
    val schOff = Sch(22, 0)

    fun say(m: String) { message = m }

    // ---------- busca ----------
    private var scanning = false
    private val adapter get() = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) {
            val name = r.scanRecord?.deviceName ?: r.device.name ?: ""
            val f = Found(r.device.address, name, r.rssi, r.device)
            main.post {
                val i = found.indexOfFirst { it.address == f.address }
                if (i >= 0) found[i] = f else found.add(f)
                if (prefs.autoDemo.value && link == Link.SCANNING && isDemo(name)) connect(f)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post {
                scanning = false
                if (link == Link.SCANNING) link = Link.OFF
                message = "Falha na busca ($errorCode)"
            }
        }
    }

    fun startScan() {
        if (scanning || link == Link.ON || link == Link.CONNECTING) return
        val sc = adapter?.bluetoothLeScanner
        if (sc == null) { message = "Ligue o Bluetooth para buscar aparelhos."; return }
        found.clear()
        scanning = true
        if (link == Link.OFF) link = Link.SCANNING
        sc.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        main.postDelayed({ stopScan() }, 12000)
    }

    fun stopScan() {
        if (!scanning) return
        scanning = false
        adapter?.bluetoothLeScanner?.stopScan(scanCb)
        if (link == Link.SCANNING) link = Link.OFF
    }

    // ---------- conexão ----------
    private var gatt: BluetoothGatt? = null
    private var wr: BluetoothGattCharacteristic? = null
    private var lastDev: BluetoothDevice? = null
    private var manualOff = false
    private var retry = 0
    private var quiet = false

    fun connect(f: Found) {
        if (link == Link.CONNECTING || link == Link.ON) return
        stopScan()
        manualOff = false
        retry = 0
        lastDev = f.device
        deviceName = if (isDemo(f.name)) "leds do quarto" else f.name.ifBlank { f.address }
        openGatt(false)
    }

    private fun openGatt(q: Boolean) {
        val d = lastDev ?: return
        quiet = q
        link = Link.CONNECTING
        message = if (q) "Reconectando…" else ""
        gatt = d.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        manualOff = true
        retry = 0
        gatt?.disconnect()
    }

    fun close() {
        manualOff = true
        stopScan()
        gatt?.close()
        gatt = null
    }

    private fun ready() {
        main.post {
            link = Link.ON
            message = ""
            retry = 0
            if (prefs.clock.value) send("clock", clockFrame())
            send("q", frame(0xBC, 0x0C, 0x01, 0x01, 0x55))
            if (prefs.welcome.value && !quiet) welcome()
        }
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                g.close()
                wr = null
                gatt = null
                synchronized(queue) { queue.clear(); busy = false }
                main.post {
                    if (!manualOff && prefs.reconnect.value && lastDev != null && retry < 3) {
                        retry++
                        link = Link.CONNECTING
                        message = "Reconectando…"
                        main.postDelayed({ if (!manualOff && gatt == null) openGatt(true) }, 1500L * retry)
                    } else {
                        link = Link.OFF
                        message = if (manualOff || status == BluetoothGatt.GATT_SUCCESS) "" else "Desconectado ($status)"
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(SVC)
            val c = svc?.getCharacteristic(WRC)
            if (svc == null || c == null) {
                main.post { message = "Serviço FFF0 não encontrado neste aparelho." }
                manualOff = true
                g.disconnect()
                return
            }
            wr = c
            val nt = svc.getCharacteristic(NTC)
            val cccd = nt?.getDescriptor(CCCD)
            if (nt == null || cccd == null) {
                ready()
                return
            }
            g.setCharacteristicNotification(nt, true)
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run { cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(cccd) }
            }
            if (!ok) ready()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            ready()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            synchronized(queue) { busy = false }
            pump()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            rx(value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val v = c.value ?: return
            rx(v)
        }
    }

    // ---------- respostas do aparelho ----------
    private fun hex(a: ByteArray) = a.joinToString(" ") { "%02X".format(it.toInt() and 255) }

    private fun rx(a: ByteArray) {
        main.post {
            rxLog.add(0, "\u2190 " + hex(a))
            while (rxLog.size > 8) rxLog.removeAt(rxLog.size - 1)
        }
        var i = 0
        while (i + 2 < a.size) {
            if ((a[i].toInt() and 255) != 0xBC) { i++; continue }
            val n = (a[i + 2].toInt() and 255) + 4
            if (i + n > a.size) break
            parse(a.copyOfRange(i, i + n))
            i += n
        }
    }

    private fun parse(b: ByteArray) {
        if (b.size < 10 || (b[1].toInt() and 255) != 0x0D) return
        val s = when (b[4].toInt()) { 1 -> schOn; 0 -> schOff; else -> return }
        val en = b[5].toInt() == 1
        val mask = b[6].toInt() and 255
        val h = (b[7].toInt() and 255).coerceAtMost(23)
        val m = (b[8].toInt() and 255).coerceAtMost(59)
        main.post {
            s.en = en
            for (d in 0..6) s.days[d] = ((mask shr d) and 1) == 1
            s.h = h
            s.m = m
        }
    }

    // ---------- fila de escrita (uma por vez, só a última de cada tipo) ----------
    private val queue = LinkedHashMap<String, ByteArray>()
    private var busy = false

    private fun send(key: String, data: ByteArray) {
        synchronized(queue) { queue.remove(key); queue[key] = data }
        pump()
    }

    private fun pump() {
        val g = gatt ?: return
        val c = wr ?: return
        val d: ByteArray = synchronized(queue) {
            if (busy || queue.isEmpty()) {
                null
            } else {
                val k = queue.keys.first()
                busy = true
                queue.remove(k)
            }
        } ?: return
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
        if (!ok) synchronized(queue) { busy = false }
    }

    // ---------- comandos ----------
    fun setColor(h: Int, s: Int) {
        hue = h.coerceIn(0, 360)
        sat = s.coerceIn(0, 1000)
        sp.edit().putInt("h", hue).putInt("s", sat).apply()
        send("color", colorFrame(hue, sat))
    }

    fun changeBright(v: Int) {
        bright = v.coerceIn(100, 1000)
        send("bright", brightFrame(bright))
    }

    /** Usado pela música: muda o brilho sem mexer no estado da tela. */
    fun brightRaw(v: Int) = send("bright", brightFrame(v.coerceIn(100, 1000)))

    fun changePower(on: Boolean) {
        power = on
        send("power", frame(0xBC, 0x01, 0x01, if (on) 1 else 0, 0x55))
    }

    fun mode(id: Int) = send("mode", frame(0xBC, 0x06, 0x02, 0, id.coerceIn(1, 255), 0x55))
    fun speed(v: Int) = send("speed", frame(0xBC, 0x08, 0x01, v.coerceIn(1, 100), 0x55))
    fun direction(v: Boolean) = send("dir", frame(0xBC, 0x07, 0x01, if (v) 1 else 0, 0x55))
    fun ledCount(n: Int) {
        val c = n.coerceIn(8, 300)
        send("leds", frame(0xBC, 0x03, 0x02, c shr 8, c and 255, 0x55))
    }
    fun colorOrder(n: Int) = send("order", frame(0xBC, 0x02, 0x01, n.coerceIn(1, 6), 0x55))
    fun micDevice(on: Boolean) = send("micdev", frame(0xBC, 0x0F, 0x01, if (on) 1 else 0, 0x55))
    fun micEffect(n: Int) = send("micfx", frame(0xBC, 0x11, 0x01, n.coerceIn(1, 4), 0x55))
    fun micSens(v: Int) = send("micsens", frame(0xBC, 0x12, 0x01, v.coerceIn(1, 100), 0x55))

    /** Cor sem alterar o estado da tela (música e microfone do celular). */
    fun colorRaw(h: Int, s: Int) = send("fx", colorFrame(h.coerceIn(0, 360), s.coerceIn(0, 1000)))
    fun voiceColor(h: Int, s: Int) = send("voice", frame(0xBC, 0x09, 0x06, h shr 8, h and 255, s shr 8, s and 255, 0, 0, 0x55))

    fun raw(bytes: ByteArray) {
        main.post {
            rxLog.add(0, "\u2192 " + hex(bytes))
            while (rxLog.size > 8) rxLog.removeAt(rxLog.size - 1)
        }
        send("lab", bytes)
    }

    private val schRun = HashMap<Boolean, Runnable>()

    /** Chame depois de mudar um agendamento; envia ao aparelho com um pequeno atraso. */
    fun schChanged(isOn: Boolean) {
        schRun[isOn]?.let { main.removeCallbacks(it) }
        val r = Runnable {
            val s = if (isOn) schOn else schOff
            send(if (isOn) "sch-on" else "sch-off",
                frame(0xBC, 0x0D, 0x06, 0x01, if (isOn) 1 else 0, if (s.en) 1 else 0, s.mask(), s.h, s.m, 0x55))
        }
        schRun[isOn] = r
        main.postDelayed(r, 350)
    }

    /** Ao conectar: a cor atual vai suavemente ao branco e volta, em 1 segundo. */
    private fun welcome() {
        val h = hue
        val s0 = sat
        val t0 = SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                val t = ((SystemClock.uptimeMillis() - t0) / 1000f).coerceAtMost(1f)
                val w = ((1 - cos(2 * Math.PI * t)) / 2).toFloat()
                send("welcome", colorFrame(h, (s0 * (1 - w)).toInt()))
                if (t < 1f) main.postDelayed(this, 50) else send("welcome", colorFrame(h, s0))
            }
        }
        main.post(step)
    }

    private fun clockFrame(): ByteArray {
        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR)
        val wd = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        return frame(
            0xBC, 0x0B, 0x07, y shr 8, y and 255, c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), 0x55, wd,
        )
    }

    private fun colorFrame(h: Int, s: Int) = frame(0xBC, 0x04, 0x06, h shr 8, h and 255, s shr 8, s and 255, 0, 0, 0x55)
    private fun brightFrame(v: Int) = frame(0xBC, 0x05, 0x06, v shr 8, v and 255, 0, 0, 0, 0, 0x55)
    private fun frame(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    companion object {
        val SVC: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val WRC: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
        val NTC: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val DEMO = Regex("^GATT-+DEMO$", RegexOption.IGNORE_CASE)
        fun isDemo(n: String) = DEMO.matches(n.trim())
    }
}
