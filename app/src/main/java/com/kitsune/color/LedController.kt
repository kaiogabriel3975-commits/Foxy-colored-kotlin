package com.kitsune.color

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.UUID
import kotlin.math.cos

data class Found(val address: String, val name: String, val rssi: Int, val device: BluetoothDevice)

/**
 * Conversa com a fita LED pelo protocolo BLE (serviço FFF0, escrita em FFF3).
 * Quadro: BC <cmd> <tamanho> <dados...> 55
 */
@SuppressLint("MissingPermission")
class LedController(private val ctx: Context) {
    enum class Link { OFF, SCANNING, CONNECTING, ON }

    private val prefs = ctx.getSharedPreferences("led", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    var link by mutableStateOf(Link.OFF)
        private set
    var deviceName by mutableStateOf("")
        private set
    var message by mutableStateOf("")
        private set
    val found = mutableStateListOf<Found>()

    var hue by mutableStateOf(prefs.getInt("h", 0))
        private set
    var sat by mutableStateOf(prefs.getInt("s", 1000))
        private set
    var bright by mutableStateOf(1000)
        private set
    var power by mutableStateOf(true)
        private set

    var autoDemo = true

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
                if (autoDemo && link == Link.SCANNING && isDemo(name)) connect(f)
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
        if (scanning) return
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

    fun connect(f: Found) {
        if (link == Link.CONNECTING || link == Link.ON) return
        stopScan()
        link = Link.CONNECTING
        deviceName = if (isDemo(f.name)) "leds do quarto" else f.name.ifBlank { f.address }
        message = ""
        gatt = f.device.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() { gatt?.disconnect() }

    fun close() {
        stopScan()
        gatt?.close()
        gatt = null
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
                    link = Link.OFF
                    message = if (status == BluetoothGatt.GATT_SUCCESS) "" else "Desconectado ($status)"
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val c = g.getService(SVC)?.getCharacteristic(WRC)
            if (c == null) {
                main.post { message = "Serviço FFF0 não encontrado neste aparelho." }
                g.disconnect()
                return
            }
            wr = c
            main.post {
                link = Link.ON
                message = ""
                send("clock", clockFrame())
                welcome()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            synchronized(queue) { busy = false }
            pump()
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
        prefs.edit().putInt("h", hue).putInt("s", sat).apply()
        send("color", colorFrame(hue, sat))
    }

    fun updateBrightness(v: Int) {
        bright = v.coerceIn(100, 1000)
        send("bright", frame(0xBC, 0x05, 0x06, bright shr 8, bright and 255, 0, 0, 0, 0, 0x55))
    }

    fun updatePower(on: Boolean) {
        power = on
        send("power", frame(0xBC, 0x01, 0x01, if (on) 1 else 0, 0x55))
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

    private fun frame(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    companion object {
        val SVC: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val WRC: UUID = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
        private val DEMO = Regex("^GATT-+DEMO$", RegexOption.IGNORE_CASE)
        fun isDemo(n: String) = DEMO.matches(n.trim())
    }
}
