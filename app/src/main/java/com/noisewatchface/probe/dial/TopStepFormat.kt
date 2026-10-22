package com.noisewatchface.probe.dial

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The `TOPSTEP` dial container, as observed in the samples shipped inside the
 * official NoiseFit APK.
 *
 * Layout, verified against five samples:
 *
 * ```
 * 0x000  "TOPSTEP" 0x01          8-byte magic
 * 0x008  u32                      observed 0
 * 0x00C  u32 total file size      matches the file length exactly
 * 0x024  u32 header size          1024 in all samples
 * 0x028  u32 payload size         equals total - header
 * 0x030  u32 version              0x00340001
 * 0x038  ASCII container name     e.g. gui_dial_binfile_watch_..._ota.bin
 * 0x400  payload                  begins with its own "TBUI" magic
 * ```
 *
 * Everything else in the header is unknown; those regions are preserved
 * verbatim on re-serialisation so a round-trip stays byte-exact.
 *
 * This is a parser for a format observed by inspection. It is not derived from
 * any vendor specification, and the unknown fields carry no meaning here.
 */
data class TopStepPackage(
    val magic: ByteArray,
    val headerSize: Int,
    val totalSize: Int,
    val version: Int,
    val name: String,
    /** Header bytes minus the fields above, kept so they can be written back. */
    val header: ByteArray,
    val payload: ByteArray,
) {
    val payloadSize: Int get() = payload.size

    /** The payload's own magic, expected to be ASCII "TBUI". */
    val payloadMagic: String
        get() = if (payload.size >= 4) String(payload, 0, 4, Charsets.US_ASCII) else ""

    companion object {
        const val MAGIC = "TOPSTEP"
        const val HEADER_SIZE = 1024
        const val SIZE_FIELD_OFFSET = 0x0C
        const val PAYLOAD_SIZE_OFFSET = 0x28
        const val HEADER_SIZE_OFFSET = 0x24
        const val VERSION_OFFSET = 0x30
        const val NAME_OFFSET = 0x38
        const val NAME_MAX = 0x100
    }
}

class DialFormatException(message: String) : Exception(message)

object TopStepParser {

    fun parse(bytes: ByteArray): TopStepPackage {
        if (bytes.size < TopStepPackage.HEADER_SIZE) {
            throw DialFormatException(
                "File is ${bytes.size} bytes; the container header alone is " +
                    "${TopStepPackage.HEADER_SIZE}",
            )
        }
        val magicBytes = bytes.copyOfRange(0, 8)
        val magicText = String(magicBytes, 0, minOf(8, magicBytes.size), Charsets.US_ASCII)
        if (!magicText.startsWith(TopStepPackage.MAGIC)) {
            throw DialFormatException(
                "Not a TOPSTEP container: first bytes are " +
                    magicBytes.joinToString(" ") { "%02X".format(it) },
            )
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val headerSize = buf.getInt(TopStepPackage.HEADER_SIZE_OFFSET)
        if (headerSize != TopStepPackage.HEADER_SIZE) {
            throw DialFormatException(
                "Unexpected header size $headerSize (expected ${TopStepPackage.HEADER_SIZE})",
            )
        }
        val totalSize = buf.getInt(TopStepPackage.SIZE_FIELD_OFFSET)
        val payloadSize = buf.getInt(TopStepPackage.PAYLOAD_SIZE_OFFSET)
        val version = buf.getInt(TopStepPackage.VERSION_OFFSET)

        // The samples agree exactly; a mismatch means this is not the format we
        // modelled, so say so rather than silently reading past the end.
        if (totalSize != bytes.size) {
            throw DialFormatException(
                "Declared size $totalSize does not match actual ${bytes.size}",
            )
        }
        if (payloadSize != totalSize - headerSize) {
            throw DialFormatException(
                "Declared payload $payloadSize does not match total-header " +
                    "${totalSize - headerSize}",
            )
        }

        val name = readName(bytes)
        val header = bytes.copyOfRange(0, headerSize)
        val payload = bytes.copyOfRange(headerSize, headerSize + payloadSize)

        return TopStepPackage(
            magic = magicBytes,
            headerSize = headerSize,
            totalSize = totalSize,
            version = version,
            name = name,
            header = header,
            payload = payload,
        )
    }

    private fun readName(bytes: ByteArray): String {
        val start = TopStepPackage.NAME_OFFSET
        val limit = minOf(bytes.size, start + TopStepPackage.NAME_MAX)
        var end = start
        while (end < limit && bytes[end] != 0.toByte()) end++
        return String(bytes, start, end - start, Charsets.US_ASCII)
    }

    /**
     * Rebuilds the container from its parts. For an unmodified package this must
     * reproduce the input byte for byte — that is the test that proves the model
     * is complete.
     */
    fun serialise(pkg: TopStepPackage): ByteArray {
        val header = pkg.header.copyOf(TopStepPackage.HEADER_SIZE)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(TopStepPackage.SIZE_FIELD_OFFSET, pkg.headerSize + pkg.payload.size)
        buf.putInt(TopStepPackage.HEADER_SIZE_OFFSET, pkg.headerSize)
        buf.putInt(TopStepPackage.PAYLOAD_SIZE_OFFSET, pkg.payload.size)
        buf.putInt(TopStepPackage.VERSION_OFFSET, pkg.version)

        val out = ByteArray(pkg.headerSize + pkg.payload.size)
        System.arraycopy(header, 0, out, 0, pkg.headerSize)
        System.arraycopy(pkg.payload, 0, out, pkg.headerSize, pkg.payload.size)
        return out
    }
}
