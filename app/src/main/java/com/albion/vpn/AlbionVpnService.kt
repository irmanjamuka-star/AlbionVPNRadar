
package com.albion.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class AlbionVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val albionPorts = setOf(5055, 5056)

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("AlbionVPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)

        vpnInterface = builder.establish()

        thread(start = true) {
            captureTraffic()
        }

        return START_STICKY
    }

    private fun captureTraffic() {
        val input = FileInputStream(vpnInterface!!.fileDescriptor)
        val buffer = ByteArray(32767)
        val socket = DatagramSocket()
        val target = InetAddress.getByName("127.0.0.1")

        while (true) {
            val length = input.read(buffer)
            if (length > 0) {
                // Simple UDP port detection (very basic parsing)
                val dstPort = ((buffer[22].toInt() and 0xFF) shl 8) or (buffer[23].toInt() and 0xFF)
                if (albionPorts.contains(dstPort)) {
                    val packet = DatagramPacket(buffer, length, target, 9000)
                    socket.send(packet)
                }
            }
        }
    }
}
