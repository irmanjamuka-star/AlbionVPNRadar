package com.albion.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class AlbionVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false

    companion object {
        const val TAG = "AlbionVPN"
        const val LOCAL_MIRROR_PORT = 9000
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (running) return START_STICKY
        running = true

        try {
            val builder = Builder()
                .setSession("AlbionVPNRadar")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addAllowedApplication("com.albiononline")

            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                log("Failed to establish VPN")
                stopSelf()
                return START_NOT_STICKY
            }

            startPacketProcessor()

            log("VPN Started – Monitoring Albion UDP")

        } catch (e: Exception) {
            log("VPN error: ${e.message}")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startPacketProcessor() {

        thread(start = true, name = "AlbionPacketThread") {

            val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface!!.fileDescriptor)

            val mirrorSocket = DatagramSocket()
            protect(mirrorSocket)

            val buffer = ByteArray(65535)

            while (running) {

                val length = try {
                    inputStream.read(buffer)
                } catch (e: Exception) {
                    break
                }

                if (length <= 0) continue

                // Forward original packet back to VPN (so Albion keeps internet)
                try {
                    outputStream.write(buffer, 0, length)
                } catch (_: Exception) {}

                // Mirror only UDP traffic
                if (isUdp(buffer, length)) {
                    try {
                        val packet = DatagramPacket(
                            buffer.copyOf(length),
                            length,
                            InetAddress.getByName("127.0.0.1"),
                            LOCAL_MIRROR_PORT
                        )

                        mirrorSocket.send(packet)

                        log("Mirrored UDP packet (${length} bytes)")

                    } catch (e: Exception) {
                        log("Mirror error: ${e.message}")
                    }
                }
            }

            mirrorSocket.close()
        }
    }

    private fun isUdp(packet: ByteArray, length: Int): Boolean {
        if (length < 20) return false
        val protocol = packet[9].toInt() and 0xFF
        return protocol == 17
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        sendBroadcast(Intent("VPN_LOG").putExtra("message", message))
    }

    override fun onDestroy() {
        running = false
        vpnInterface?.close()
        log("VPN Stopped")
        super.onDestroy()
    }
}
