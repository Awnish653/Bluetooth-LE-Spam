package de.simon.dankelmann.bluetoothlespam

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.content.pm.PackageManager
import android.graphics.Color
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
import kotlin.math.max

class StressTestActivity : AppCompatActivity() {
    private val serviceUuid = ParcelUuid(UUID.fromString("7b3c9e20-6a52-4f19-8d2a-1c9b4e7a6101"))
    private val permissionRequest = 7401
    private val maxSessionMs = 60_000L

    private lateinit var status: TextView
    private lateinit var stats: TextView
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
            stats.text = "Elapsed: ${elapsed / 1000}s\nSession: #$sessionId\nAdvertiser callbacks: $successCallbacks\nMode: LOW_LATENCY • max 60s"
            if (elapsed >= maxSessionMs) {
                appendLog("60-second safety limit reached; stopping.")
                stopAdvertising()
            } else {
                handler.postDelayed(this, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        appendLog("Ready. TEST MODE uses an app-specific BLE service UUID.")
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
            setBackgroundColor(Color.BLACK)
        }

        val title = TextView(this).apply {
            text = "BLE STRESS TEST"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Authorized-device testing • TEST MODE"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 6, 0, 20)
        }
        root.addView(subtitle)

        status = TextView(this).apply {
            text = "● STOPPED"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12)
        }
        root.addView(status)

        stats = TextView(this).apply {
            text = "Elapsed: 0s\nSession: —\nAdvertiser callbacks: 0\nMode: LOW_LATENCY • max 60s"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 20)
        }
        root.addView(stats)

        startButton = Button(this).apply {
            text = "START TEST"
            setOnClickListener { startAdvertising() }
        }
        root.addView(startButton, LinearLayout.LayoutParams(-1, 60))

        stopButton = Button(this).apply {
            text = "STOP"
            isEnabled = false
            setOnClickListener { stopAdvertising() }
        }
        val stopParams = LinearLayout.LayoutParams(-1, 60)
        stopParams.topMargin = 12
        root.addView(stopButton, stopParams)

        val logTitle = TextView(this).apply {
            text = "LIVE LOG"
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 8)
        }
        root.addView(logTitle)

        log = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.TOP
        }
        val scroll = ScrollView(this).apply {
            addView(log)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT),
                permissionRequest
            )
        }
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
                appendLog("BLE advertiser started successfully.")
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
            status.text = "● RUNNING"
            startButton.isEnabled = false
            stopButton.isEnabled = true
            appendLog("Started app-specific BLE advertisement. No device spoofing or popup-triggering payloads.")
            handler.post(ticker)
        } catch (security: SecurityException) {
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
            try {
                adv.stopAdvertising(cb)
            } catch (_: Exception) {
            }
        }
        callback = null
        advertiser = null
        running = false
        status.text = "● STOPPED"
        startButton.isEnabled = true
        stopButton.isEnabled = false
        appendLog("Advertising stopped; resources released.")
    }

    private fun appendLog(message: String) {
        if (!::log.isInitialized) return
        val current = log.text.toString()
        val line = "[${System.currentTimeMillis() % 100000}] $message"
        log.text = (current + if (current.isEmpty()) "" else "\n" + line).takeLast(6000)
    }

    override fun onDestroy() {
        stopAdvertising()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequest) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                appendLog("Bluetooth permissions granted.")
            } else {
                appendLog("Bluetooth permissions denied.")
            }
        }
    }
}
