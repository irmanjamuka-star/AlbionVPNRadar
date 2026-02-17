package com.albion.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.*
import android.content.pm.PackageManager

class MainActivity : Activity() {

    private lateinit var spinner: Spinner
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var statusText: TextView

    private var selectedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 60, 40, 40)

        spinner = Spinner(this)
        btnStart = Button(this)
        btnStop = Button(this)
        statusText = TextView(this)

        btnStart.text = "Start Monitoring"
        btnStop.text = "Stop Monitoring"
        statusText.text = "Status: Idle"

        layout.addView(spinner)
        layout.addView(btnStart)
        layout.addView(btnStop)
        layout.addView(statusText)

        setContentView(layout)

        loadInstalledApps()

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
            statusText.text = "Status: Stopped"
        }
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val appNames = mutableListOf<String>()
        val packageMap = mutableMapOf<String, String>()

        for (app in apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val name = pm.getApplicationLabel(app).toString()
                appNames.add(name)
                packageMap[name] = app.packageName
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, appNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val name = appNames[position]
                selectedPackage = packageMap[name]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun startVpn() {
        val intent = Intent(this, AlbionVpnService::class.java)
        intent.putExtra("TARGET_PACKAGE", selectedPackage)
        startService(intent)
        statusText.text = "Status: Running"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode == RESULT_OK) {
            startVpn()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
