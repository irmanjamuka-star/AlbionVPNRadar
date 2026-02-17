package com.albion.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.content.Intent
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.nio.ByteBuffer

class AlbionVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val targetPackage = intent?.getStringExtra("TARGET_PACKAGE")

        val builder = Builder()
        builder.setSession("AlbionVPN")
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)

        if (targetPackage != null) {
            try {
                builder.addAllowedApplication(targetPackage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        vpnInterface = builder.establish()
        running = true

        Thread {
            captureLoop()
        }.start()

        return START_STICKY
    }

    private fun captureLoop() {

        val input = FileInputStream(vpnInterface!!.fileDescriptor)
        val buffer = ByteArray(32767)

        val socket = DatagramSocket()
        val radarAddress = java.net.InetAddress.getByName("127.0.0.1")

        while (running) {

            val length = input.read(buffer)
            if (length > 0) {

                val byteBuffer = ByteBuffer.wrap(buffer, 0, length)

                // ===== Parse IPv4 Header =====
                val version = (byteBuffer.get(0).toInt() shr 4)
                if (version != 4) continue

                val protocol = byteBuffer.get(9).toInt() and 0xFF
                if (protocol != 17) continue  // Not UDP

                val ipHeaderLength = (byteBuffer.get(0).toInt() and 0x0F) * 4

                // ===== Parse UDP Header =====
                val srcPort = ((buffer[ipHeaderLength].toInt() and 0xFF) shl 8) or
                        (buffer[ipHeaderLength + 1].toInt() and 0xFF)

                val dstPort = ((buffer[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                        (buffer[ipHeaderLength + 3].toInt() and 0xFF)

                if (dstPort == 5055 || dstPort == 5056) {

                    val udpHeaderLength = 8
                    val payloadStart = ipHeaderLength + udpHeaderLength
                    val payloadLength = length - payloadStart

                    if (payloadLength > 0) {
                        val packet = DatagramPacket(
                            buffer,
                            payloadStart,
                            payloadLength,
                            radarAddress,
                            9000
                        )
                        socket.send(packet)
                    }
                }
            }
        }

        socket.close()
    }

    override fun onDestroy() {
        running = false
        vpnInterface?.close()
        super.onDestroy()
    }
}
