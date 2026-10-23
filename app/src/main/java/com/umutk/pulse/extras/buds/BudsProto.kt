package com.umutk.pulse.extras.buds

import java.io.ByteArrayOutputStream

/**
 * The Bluetooth serial protocol of the Galaxy Buds, written from the public protocol notes of the GalaxyBudsClient project (GPL-3.0, github.com/ThePBone/GalaxyBudsClient):
 * start 0xFD, a two byte header (the size in the low 10 bits, bit 12 of the high byte marks a response), the message id, the payload, the CRC-16/CCITT of id and
 * payload (low byte first) and the end 0xDD. The message ids and the layout of the status messages are the ones that project documents.
 */
object BudsProto {
    const val SOM = 0xFD
    const val EOM = 0xDD
    const val STATUS_UPDATED = 96
    const val EXTENDED_STATUS_UPDATED = 97
    const val NOISE_CONTROLS_UPDATE = 119
    const val NOISE_CONTROLS = 120
    const val SET_NOISE_REDUCTION = 152
    const val SET_AMBIENT_MODE = 128
    const val MANAGER_INFO = 136
    const val UPDATE_TIME = 167

    class Msg(val id: Int, val payload: ByteArray)

    private val TAB = IntArray(256) { n ->
        var c = n shl 8
        repeat(8) { c = if (c and 0x8000 != 0) (c shl 1) xor 0x1021 else c shl 1 }
        c and 0xFFFF
    }

    fun crc(id: Int, payload: ByteArray): Int {
        var crc = 0
        fun add(b: Int) { crc = (TAB[((crc shr 8) xor b) and 0xFF] xor (crc shl 8)) and 0xFFFF }
        add(id and 0xFF); payload.forEach { add(it.toInt() and 0xFF) }
        return crc
    }

    /** A message ready to be written; [response] marks the answer to a message of the earbuds. */
    fun encode(id: Int, payload: ByteArray = ByteArray(0), response: Boolean = false): ByteArray {
        val size = payload.size + 3
        val out = ByteArrayOutputStream()
        out.write(SOM)
        out.write(size and 0xFF)
        out.write(((size shr 8) and 0x03) or (if (response) 0x10 else 0))
        out.write(id)
        out.write(payload)
        val c = crc(id, payload)
        out.write(c and 0xFF); out.write((c shr 8) and 0xFF)
        out.write(EOM)
        return out.toByteArray()
    }

    /** Takes the complete messages off the front of [buf]; what is left is the start of the next one. */
    fun parse(buf: ByteArrayOutputStream): List<Msg> {
        val data = buf.toByteArray()
        val list = ArrayList<Msg>()
        var i = 0
        while (i < data.size) {
            if ((data[i].toInt() and 0xFF) != SOM) { i++; continue }
            if (data.size - i < 6) break
            val size = ((data[i + 1].toInt() and 0xFF) or ((data[i + 2].toInt() and 0x03) shl 8))
            val total = 1 + 2 + size + 1
            if (size < 3 || size > 1000) { i++; continue }
            if (data.size - i < total) break
            if ((data[i + total - 1].toInt() and 0xFF) != EOM) { i++; continue }
            val id = data[i + 3].toInt() and 0xFF
            val payload = data.copyOfRange(i + 4, i + 4 + size - 3)
            val c = crc(id, payload)
            val got = (data[i + 4 + size - 3].toInt() and 0xFF) or ((data[i + 4 + size - 2].toInt() and 0xFF) shl 8)
            if (c == got) list += Msg(id, payload)
            i += total
        }
        buf.reset()
        if (i < data.size) buf.write(data, i, data.size - i)
        return list
    }
}
