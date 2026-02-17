package com.albion.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var txtStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        txtStatus = findViewById(R.id.txtStatus)

        btnStart.setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 100)
            } else {
                startVpn()
            }
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, AlbionVpnService::class.java))
            txtStatus.text = "Status: Stopped"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startVpn()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startVpn() {
        startService(Intent(this, AlbionVpnService::class.java))
        txtStatus.text = "Status: Running"
    }
}
