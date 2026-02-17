package com.albion.vpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {

    private lateinit var btnToggle: Button
    private lateinit var statusText: TextView
    private lateinit var logText: TextView

    private var isRunning = false

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("message") ?: return
            runOnUiThread {
                logText.append("\n$message")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout utama
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 80, 40, 40)
        layout.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        // Tombol bulat
        btnToggle = Button(this)
        btnToggle.text = "START"
        btnToggle.textSize = 20f
        btnToggle.setTextColor(Color.WHITE)

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor("#4CAF50")) // hijau
        btnToggle.background = shape

        val buttonSize = 300
        val params = LinearLayout.LayoutParams(buttonSize, buttonSize)
        btnToggle.layoutParams = params

        // Status
        statusText = TextView(this)
        statusText.text = "Status: Idle"
        statusText.textSize = 18f
        statusText.setPadding(0, 40, 0, 20)

        // Log
        logText = TextView(this)
        logText.text = "Log:"
        logText.textSize = 14f
        logText.setPadding(0, 20, 0, 0)

        layout.addView(btnToggle)
        layout.addView(statusText)
        layout.addView(logText)

        setContentView(layout)

        registerReceiver(logReceiver, IntentFilter("VPN_LOG"))

        btnToggle.setOnClickListener {
            if (!isRunning) {
                startVpn()
            } else {
                stopVpn()
            }
        }
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 100)
        } else {
            onActivityResult(100, RESULT_OK, null)
        }
    }

    private fun stopVpn() {
        stopService(Intent(this, AlbionVpnService::class.java))
        isRunning = false
        btnToggle.text = "START"
        (btnToggle.background as GradientDrawable)
            .setColor(Color.parseColor("#4CAF50"))
        statusText.text = "Status: Stopped"
        logText.append("\nVPN Stopped")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startService(Intent(this, AlbionVpnService::class.java))
            isRunning = true
            btnToggle.text = "STOP"
            (btnToggle.background as GradientDrawable)
                .setColor(Color.parseColor("#F44336")) // merah
            statusText.text = "Status: Running (Albion Monitoring)"
            logText.append("\nVPN Started")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        unregisterReceiver(logReceiver)
        super.onDestroy()
    }
}
