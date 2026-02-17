package com.albion.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class AlbionVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false

    companion object {
        const val TAG = "AlbionVPN"
        const val LOCAL_MIRROR_PORT = 9000
    }

    // NAT table: sourcePort → socket
    private val udpSessions = ConcurrentHashMap<Int, DatagramSocket>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (running) return START_STICKY
        running = true

        val builder = Builder()
            .setSession("AlbionVPNRadar")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addAllowedApplication("com.albiononline")

        vpnInterface = builder.establish()

        startTunnel()

        log("VPN started (Full UDP Forward Mode)")
        return START_STICKY
    }

    private fun startTunnel() {

        thread(start = true, name = "VpnMainThread") {

            val input = FileInputStream(vpnInterface!!.fileDescriptor)
            val output = FileOutputStream(vpnInterface!!.fileDescriptor)

            val buffer = ByteArray(65535)

            while (running) {

                val length = try {
                    input.read(buffer)
                } catch (e: Exception) {
                    break
                }

                if (length <= 0) continue

                val packet = ByteBuffer.wrap(buffer, 0, length)

                if (!isUdp(packet)) continue

                val srcPort = getSourcePort(packet)
                val dstPort = getDestinationPort(packet)
                val dstIp = getDestinationIp(packet)

                val udpHeaderOffset = getIpHeaderLength(packet)
                val udpPayloadOffset = udpHeaderOffset + 8
                val payloadLength = length - udpPayloadOffset

                if (payloadLength <= 0) continue

                val payload = ByteArray(payloadLength)
                System.arraycopy(buffer, udpPayloadOffset, payload, 0, payloadLength)

                forwardUdp(
                    srcPort,
                    dstIp,
                    dstPort,
                    payload,
                    output
                )
            }
        }
    }

    private fun forwardUdp(
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        payload: ByteArray,
        tunOutput: FileOutputStream
    ) {

        val socket = udpSessions.getOrPut(srcPort) {
            val newSocket = DatagramSocket()
            protect(newSocket)

            // Thread return handler
            thread(start = true) {
                val recvBuf = ByteArray(65535)
                while (running) {
                    try {
                        val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                        newSocket.receive(recvPacket)

                        sendBackToTun(
                            srcPort,
                            recvPacket.address,
                            recvPacket.port,
                            recvPacket.data,
                            recvPacket.length,
                            tunOutput
                        )

                    } catch (_: Exception) {
                        break
                    }
                }
            }

            newSocket
        }

        try {
            val sendPacket = DatagramPacket(payload, payload.size, dstIp, dstPort)
            socket.send(sendPacket)

            if (dstPort == 5055 || dstPort == 5056) {
                mirrorPacket(payload)
            }

        } catch (e: Exception) {
            log("Forward error: ${e.message}")
        }
    }

    private fun sendBackToTun(
        originalSrcPort: Int,
        srcIp: InetAddress,
        srcPort: Int,
        data: ByteArray,
        length: Int,
        tunOutput: FileOutputStream
    ) {

        val packet = buildUdpPacket(
            srcIp,
            originalSrcPort,
            srcPort,
            data,
            length
        )

        tunOutput.write(packet)
    }

    private fun buildUdpPacket(
        srcIp: InetAddress,
        dstPort: Int,
        srcPort: Int,
        data: ByteArray,
        length: Int
    ): ByteArray {

        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + length

        val buffer = ByteBuffer.allocate(totalLen)

        // IPv4 header
        buffer.put(0x45.toByte())
        buffer.put(0x00)
        buffer.putShort(totalLen.toShort())
        buffer.putInt(0)
        buffer.put(64.toByte())
        buffer.put(17.toByte())
        buffer.putShort(0)
        buffer.put(srcIp.address)
        buffer.put(InetAddress.getByName("10.0.0.2").address)

        // UDP header
        buffer.putShort(srcPort.toShort())
        buffer.putShort(dstPort.toShort())
        buffer.putShort((udpHeaderLen + length).toShort())
        buffer.putShort(0)

        buffer.put(data, 0, length)

        return buffer.array()
    }

    private fun mirrorPacket(payload: ByteArray) {
        try {
            val mirrorSocket = DatagramSocket()
            protect(mirrorSocket)
            val packet = DatagramPacket(
                payload,
                payload.size,
                InetAddress.getByName("127.0.0.1"),
                LOCAL_MIRROR_PORT
            )
            mirrorSocket.send(packet)
            mirrorSocket.close()
        } catch (_: Exception) {}
    }

    private fun isUdp(packet: ByteBuffer): Boolean {
        return packet.get(9).toInt() == 17
    }

    private fun getIpHeaderLength(packet: ByteBuffer): Int {
        return (packet.get(0).toInt() and 0x0F) * 4
    }

    private fun getSourcePort(packet: ByteBuffer): Int {
        val offset = getIpHeaderLength(packet)
        return packet.getShort(offset).toInt() and 0xFFFF
    }

    private fun getDestinationPort(packet: ByteBuffer): Int {
        val offset = getIpHeaderLength(packet)
        return packet.getShort(offset + 2).toInt() and 0xFFFF
    }

    private fun getDestinationIp(packet: ByteBuffer): InetAddress {
        val addr = ByteArray(4)
        packet.position(16)
        packet.get(addr, 0, 4)
        return InetAddress.getByAddress(addr)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        sendBroadcast(Intent("VPN_LOG").putExtra("message", msg))
    }

    override fun onDestroy() {
        running = false
        vpnInterface?.close()
        udpSessions.values.forEach { it.close() }
        log("VPN stopped")
        super.onDestroy()
    }
}
