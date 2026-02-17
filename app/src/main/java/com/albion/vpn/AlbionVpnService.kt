package com.albion.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class AlbionVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    override fun onStartCommand(
        intent: android.content.Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (running) return START_STICKY
        running = true

        val builder = Builder()
            .setSession("Albion Monitor")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .setBlocking(false)

        vpnInterface = builder.establish()

        startPacketMonitor()

        return START_STICKY
    }

    private fun startPacketMonitor() {
        thread(start = true) {

            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val mirrorSocket = DatagramSocket()
            val buffer = ByteArray(65535)

            while (running) {

                val length = input.read(buffer)
                if (length <= 0) continue

                val packet = ByteBuffer.wrap(buffer, 0, length)

                if (!isUdp(packet)) continue

                val dstPort = getDestinationPort(packet)

                if (dstPort == 5055 || dstPort == 5056) {

                    val mirrorPacket = DatagramPacket(
                        buffer,
                        length,
                        InetAddress.getByName("127.0.0.1"),
                        9000
                    )

                    mirrorSocket.send(mirrorPacket)
                }

                // ❗ TIDAK forward manual
                // Android akan handle routing sendiri
            }
        }
    }

    private fun isUdp(packet: ByteBuffer): Boolean {
        val protocol = packet.get(9).toInt() and 0xFF
        return protocol == 17
    }

    private fun getDestinationPort(packet: ByteBuffer): Int {
        val ipHeaderLength = (packet.get(0).toInt() and 0x0F) * 4
        return (packet.getShort(ipHeaderLength + 2).toInt() and 0xFFFF)
    }

    override fun onDestroy() {
        running = false
        vpnInterface?.close()
        super.onDestroy()
    }
}
