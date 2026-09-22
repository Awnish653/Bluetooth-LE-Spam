package de.simon.dankelmann.bluetoothlespam

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.UUID

class StressTestActivity : AppCompatActivity() {
    private val serviceUuid = ParcelUuid(UUID.fromString("7b3c9e20-6a52-4f19-8d2a-1c9b4e7a6101"))
    private val permissionRequest = 7401
    private val maxSessionMs = 60_000L

    private lateinit var status: TextView
    private lateinit var elapsedValue: TextView
    private lateinit var callbackValue: TextView
    private lateinit var sessionValue: TextView
    private lateinit var log: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null
    private var running = false
    private var startedAt = 0L
    private var sessionId = 0
    private var successCallbacks = 0

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val elapsed = System.currentTimeMillis() - startedAt
            elapsedValue.text = "${elapsed / 1000}s"
            callbackValue.text = successCallbacks.toString()
            sessionValue.text = "#$sessionId"
            if (elapsed >= maxSessionMs) {
                appendLog("60-second safety limit reached; stopping.")
                stopAdvertising()
            } else handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 9, 12)
        window.navigationBarColor = Color.rgb(8, 9, 12)
        buildUi()
        appendLog("Ready • authorized-device TEST MODE")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, stroke: Int = Color.TRANSPARENT, radius: Int = 18) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun statCard(title: String, value: String): Pair<LinearLayout, TextView> {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = rounded(Color.rgb(18, 20, 26), Color.rgb(43, 46, 55))
        }
        box.addView(label(title.uppercase(), 10.5f, Color.rgb(145, 150, 162), true))
        val valueView = label(value, 21f, Color.WHITE, true)
        box.addView(valueView, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        return Pair(box, valueView)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(18))
            setBackgroundColor(Color.rgb(8, 9, 12))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val mark = label("A", 24f, Color.BLACK, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, radius = 14)
        }
        brandRow.addView(mark, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(label("AWNISH", 18f, Color.WHITE, true))
        brand.addView(label("BLE LAB", 11f, Color.rgb(145, 150, 162)))
        brandRow.addView(brand)
        content.addView(brandRow)

        content.addView(label("BLE STRESS TEST", 30f, Color.WHITE, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        content.addView(label("Controlled Bluetooth Low Energy testing", 14f, Color.rgb(160, 165, 176)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })

        status = label("●  STOPPED", 15f, Color.rgb(150, 155, 166), true)
        status.background = rounded(Color.rgb(20, 22, 28), Color.rgb(44, 47, 56), 14)
        status.setPadding(dp(14), dp(10), dp(14), dp(10))
        content.addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18) })

        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val elapsed = statCard("Elapsed", "0s")
        val session = statCard("Session", "—")
        val callbacks = statCard("Callbacks", "0")
        elapsedValue = elapsed.second
        sessionValue = session.second
        callbackValue = callbacks.second
        grid.addView(elapsed.first, LinearLayout.LayoutParams(0, dp(80), 1f).apply { marginEnd = dp(4) })
        grid.addView(session.first, LinearLayout.LayoutParams(0, dp(80), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        grid.addView(callbacks.first, LinearLayout.LayoutParams(0, dp(80), 1f).apply { marginStart = dp(4) })
        content.addView(grid, LinearLayout.LayoutParams(-1, dp(80)).apply { topMargin = dp(14) })

        content.addView(label("TEST MODE  •  APP-SPECIFIC UUID  •  MAX 60s", 11f, Color.rgb(135, 140, 152), true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        startButton = Button(this).apply {
            text = "START TEST"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(Color.WHITE, radius = 16)
            setOnClickListener { startAdvertising() }
        }
        content.addView(startButton, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(18) })

        stopButton = Button(this).apply {
            text = "STOP TEST"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(Color.rgb(28, 30, 37), Color.rgb(62, 65, 75), 16)
            isEnabled = false
            setOnClickListener { stopAdvertising() }
        }
        content.addView(stopButton, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })

        content.addView(label("LIVE LOG", 12f, Color.rgb(150, 155, 166), true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        log = label("", 12f, Color.rgb(195, 199, 208))
        log.setPadding(dp(14), dp(12), dp(14), dp(12))
        log.background = rounded(Color.rgb(14, 16, 21), Color.rgb(38, 41, 49), 16)
        content.addView(log, LinearLayout.LayoutParams(-1, dp(150)).apply { topMargin = dp(8) })

        content.addView(label("© 2026 Awnish  •  Educational BLE testing", 11f, Color.rgb(105, 110, 122)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(6))
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT),
            permissionRequest
        )
    }

    private fun startAdvertising() {
        if (running) return
        if (!hasBluetoothPermissions()) {
            appendLog("Bluetooth permission required.")
            requestBluetoothPermissions()
            return
        }

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            appendLog("Bluetooth is unavailable or switched off.")
            return
        }
        val leAdvertiser = adapter.bluetoothLeAdvertiser
        if (leAdvertiser == null) {
            appendLog("This device does not support BLE advertising.")
            return
        }

        val nonce = ByteBuffer.allocate(8).putLong(System.nanoTime()).array()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(serviceUuid)
            .addServiceData(serviceUuid, nonce)
            .build()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                successCallbacks++
                appendLog("BLE advertiser started.")
            }
            override fun onStartFailure(errorCode: Int) {
                appendLog("BLE advertiser failed: error=$errorCode")
                stopAdvertising()
            }
        }

        try {
            leAdvertiser.startAdvertising(settings, data, callback)
            advertiser = leAdvertiser
            running = true
            startedAt = System.currentTimeMillis()
            sessionId++
            successCallbacks = 0
            status.text = "●  RUNNING"
            status.setTextColor(Color.WHITE)
            startButton.isEnabled = false
            stopButton.isEnabled = true
            appendLog("Started app-specific BLE advertisement.")
            appendLog("No spoofing or popup-triggering payloads.")
            handler.post(ticker)
        } catch (_: SecurityException) {
            appendLog("Bluetooth permission was revoked.")
        } catch (error: Exception) {
            appendLog("Start failed: ${error.javaClass.simpleName}")
        }
    }

    private fun stopAdvertising() {
        handler.removeCallbacks(ticker)
        val cb = callback
        val adv = advertiser
        if (cb != null && adv != null && hasBluetoothPermissions()) {
            try { adv.stopAdvertising(cb) } catch (_: Exception) {}
        }
        callback = null
        advertiser = null
        running = false
        if (::status.isInitialized) {
            status.text = "●  STOPPED"
            status.setTextColor(Color.rgb(150, 155, 166))
            startButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    private fun appendLog(message: String) {
        if (!::log.isInitialized) return
        val line = "[${System.currentTimeMillis() % 100000}] $message"
        log.text = (log.text.toString() + if (log.text.isEmpty()) "" else "\n" + line).takeLast(6000)
    }

    override fun onDestroy() {
        stopAdvertising()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) {
            appendLog(
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })
                    "Bluetooth permissions granted."
                else "Bluetooth permissions denied."
            )
        }
    }
}