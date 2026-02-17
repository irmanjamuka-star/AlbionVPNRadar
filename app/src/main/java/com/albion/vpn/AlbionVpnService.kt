package com.albion.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class AlbionVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {

        if (running) return START_STICKY
        running = true

        val builder = Builder()
            .setSession("AlbionRadarVPN")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addAllowedApplication("com.albiononline")
            .allowBypass()

        vpnInterface = builder.establish()

        startPacketLoop()

        return START_STICKY
    }

    private fun startPacketLoop() {

        thread(start = true) {

            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)

            val mirrorSocket = DatagramSocket()
            val buffer = ByteArray(32767)

            while (running) {

                val length = input.read(buffer)

                if (length > 0) {

                    val packet = ByteBuffer.wrap(buffer, 0, length)

                    if (isUdp(packet)) {

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
                    }

                    // Forward original packet so Albion works normally
                    output.write(buffer, 0, length)
                }
            }
        }
    }

    private fun isUdp(packet: ByteBuffer): Boolean {
        val protocol = packet.get(9).toInt() and 0xFF
        return protocol == 17
    }

    private fun getDestinationPort(packet: ByteBuffer): Int {
        val ipHeaderLength = (packet.get(0).toInt() and 0x0F) * 4
        return ((packet.getShort(ipHeaderLength + 2).toInt()) and 0xFFFF)
    }

    override fun onDestroy() {
        running = false
        vpnInterface?.close()
        super.onDestroy()
    }
}
