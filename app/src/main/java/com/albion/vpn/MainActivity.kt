package com.albion.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {

    private lateinit var btnVpn: Button
    private lateinit var logText: TextView

    private var vpnRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 80, 40, 40)

        btnVpn = Button(this)
        btnVpn.text = "START"
        btnVpn.textSize = 20f
        btnVpn.setTextColor(Color.WHITE)

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor("#2E7D32"))
        btnVpn.background = shape

        val size = 350
        btnVpn.layoutParams = LinearLayout.LayoutParams(size, size)

        logText = TextView(this)
        logText.text = "Log:\n"
        logText.setPadding(0, 60, 0, 0)

        layout.addView(btnVpn)
        layout.addView(logText)

        setContentView(layout)

        btnVpn.setOnClickListener {
            if (!vpnRunning) {
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 100)
                } else {
                    startVpn()
                }
            } else {
                stopService(Intent(this, AlbionVpnService::class.java))
                updateUi(false)
                appendLog("VPN Stopped")
            }
        }
    }

    private fun startVpn() {
        startService(Intent(this, AlbionVpnService::class.java))
        updateUi(true)
        appendLog("VPN Started - Monitoring Albion")
    }

    private fun updateUi(running: Boolean) {
        vpnRunning = running

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL

        if (running) {
            btnVpn.text = "STOP"
            shape.setColor(Color.RED)
        } else {
            btnVpn.text = "START"
            shape.setColor(Color.parseColor("#2E7D32"))
        }

        btnVpn.background = shape
    }

    private fun appendLog(message: String) {
        logText.append("\n$message")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startVpn()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
