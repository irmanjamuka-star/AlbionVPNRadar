package com.albion.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class AlbionVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false

    companion object {
        const val TAG = "AlbionVPN"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (running) return START_STICKY
        running = true

        val builder = Builder()
            .setSession("AlbionVPNRadar")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addAllowedApplication("com.albiononline")

        vpnInterface = builder.establish()

        startPacketProcessor()

        log("VPN Started – Monitoring Albion UDP 5055/5056")

        return START_STICKY
    }

    private fun startPacketProcessor() {

        thread(start = true, name = "AlbionPacketThread") {

            val inputStream = FileInputStream(vpnInterface!!.fileDescriptor)

            val mirrorSocket = DatagramSocket()
            protect(mirrorSocket) // 🔥 CRITICAL FIX

            val buffer = ByteArray(65535) // optimized large reusable buffer

            while (running) {

                val length = try {
                    inputStream.read(buffer)
                } catch (e: Exception) {
                    break
                }

                if (length <= 0) continue

                if (isUdp(buffer, length)) {

                    val dstPort = getDestinationPort(buffer)

                    if (dstPort == 5055 || dstPort == 5056) {

                        try {
                            val mirrorPacket = DatagramPacket(
                                buffer,
                                length,
                                InetAddress.getByName("127.0.0.1"),
                                9000
                            )

                            mirrorSocket.send(mirrorPacket)

                            log("UDP Packet mirrored → Port $dstPort (${length} bytes)")

                        } catch (e: Exception) {
                            log("Mirror error: ${e.message}")
                        }
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

    private fun getDestinationPort(packet: ByteArray): Int {
        val ipHeaderLength = (packet[0].toInt() and 0x0F) * 4
        val portHigh = packet[ipHeaderLength + 2].toInt() and 0xFF
        val portLow = packet[ipHeaderLength + 3].toInt() and 0xFF
        return (portHigh shl 8) or portLow
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
