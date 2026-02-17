package com.albion.vpn

import android.content.*
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var circleDrawable: GradientDrawable

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

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 80, 40, 40)
        layout.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        btnToggle = Button(this)
        btnToggle.text = "START"
        btnToggle.textSize = 20f
        btnToggle.setTextColor(Color.WHITE)

        circleDrawable = GradientDrawable()
        circleDrawable.shape = GradientDrawable.OVAL
        circleDrawable.setColor(Color.parseColor("#4CAF50"))
        btnToggle.background = circleDrawable

        val size = 300
        btnToggle.layoutParams = LinearLayout.LayoutParams(size, size)

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

        registerReceiver(logReceiver, IntentFilter("VPN_LOG"))

        btnToggle.setOnClickListener {
            if (!isRunning) startVpn()
            else stopVpn()
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
        circleDrawable.setColor(Color.parseColor("#4CAF50"))
        statusText.text = "Status: Stopped"
        logText.append("\nVPN Stopped")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startService(Intent(this, AlbionVpnService::class.java))
            isRunning = true
            btnToggle.text = "STOP"
            circleDrawable.setColor(Color.parseColor("#F44336"))
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
