package com.albion.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {

    companion object {
        var logCallback: ((String) -> Unit)? = null
    }

    private lateinit var btnToggle: Button
    private lateinit var statusText: TextView
    private lateinit var logText: TextView

    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 80, 40, 40)
        layout.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        btnToggle = Button(this)
        btnToggle.text = "START"
        btnToggle.textSize = 20f
        btnToggle.setTextColor(Color.WHITE)

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(Color.parseColor("#4CAF50"))
        btnToggle.background = shape

        val buttonSize = 300
        val params = LinearLayout.LayoutParams(buttonSize, buttonSize)
        btnToggle.layoutParams = params

        statusText = TextView(this)
        statusText.text = "Status: Idle"
        statusText.textSize = 18f
        statusText.setPadding(0, 40, 0, 20)

        logText = TextView(this)
        logText.text = "Log:"
        logText.textSize = 14f
        logText.setPadding(0, 20, 0, 0)

        layout.addView(btnToggle)
        layout.addView(statusText)
        layout.addView(logText)

        setContentView(layout)

        logCallback = {
            runOnUiThread {
                logText.append("\n$it")
            }
        }

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
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startService(Intent(this, AlbionVpnService::class.java))
            isRunning = true
            btnToggle.text = "STOP"
            (btnToggle.background as GradientDrawable)
                .setColor(Color.parseColor("#F44336"))
            statusText.text = "Status: Running"
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        logCallback = null
        super.onDestroy()
    }
}
