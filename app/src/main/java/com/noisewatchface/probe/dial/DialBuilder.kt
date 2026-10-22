package com.noisewatchface.probe.dial

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a dial from a user image by replacing the background image inside a
 * template container.
 *
 * Why a template: the payload holds more than images. Alongside the BMP blobs
 * there are compressed "PA" blocks and widget records whose layout is not yet
 * understood. Rather than fabricate those, this reuses a container the vendor
 * shipped and swaps only the background bitmap, keeping every byte length
 * identical. That means all offsets and sizes in the file stay valid.
 *
 * Verified against the sample in `app/src/test/resources`:
 *  - image table at payload 0x1560, 6 entries of 12 bytes
 *    (u16 id, u16 group, u16 width, u16 height, u32 file offset)
 *  - entries 180 and 181 point at 16bpp BMP blobs
 *  - the blobs are contiguous and end at payload size - 4
 *
 * The "PA" blocks in entries 176-179 are a compressed format that this class
 * never modifies.
 */
object DialBuilder {

    const val IMAGE_TABLE_OFFSET = 0x1560
    const val IMAGE_ENTRY_SIZE = 12
    const val MAX_IMAGE_ENTRIES = 16

    data class ImageEntry(
        val id: Int,
        val group: Int,
        val width: Int,
        val height: Int,
        val offset: Int,
        val isBmp: Boolean,
    )

    /** Reads the image table. Entries that do not point at a BMP are still listed. */
    fun readImageTable(payload: ByteArray): List<ImageEntry> {
        val out = mutableListOf<ImageEntry>()
        var i = IMAGE_TABLE_OFFSET
        while (i + IMAGE_ENTRY_SIZE <= payload.size && out.size < MAX_IMAGE_ENTRIES) {
            val buf = ByteBuffer.wrap(payload, i, IMAGE_ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            val id = buf.short.toInt() and 0xFFFF
            val group = buf.short.toInt() and 0xFFFF
            val w = buf.short.toInt() and 0xFFFF
            val h = buf.short.toInt() and 0xFFFF
            val off = buf.int
            // The table is NUL-terminated; a zero id means we reached the end.
            if (id == 0) break
            if (off <= 0 || off >= payload.size) break
            val isBmp = payload[off] == 'B'.code.toByte() && payload[off + 1] == 'M'.code.toByte()
            out += ImageEntry(id, group, w, h, off, isBmp)
            i += IMAGE_ENTRY_SIZE
        }
        return out
    }

    /** The largest BMP entry, which is the full-screen background. */
    fun backgroundEntry(payload: ByteArray): ImageEntry? =
        readImageTable(payload)
            .filter { it.isBmp }
            .maxByOrNull { it.width.toLong() * it.height }

    /**
     * Replaces the background image with [rgb565] pixels.
     *
     * [rgb565] must be exactly width*height pixels in row-major order, top row
     * first, as 16-bit little-endian RGB565. The size must match the existing
     * blob exactly; a different size would move every following offset.
     */
    fun replaceBackground(template: TopStepPackage, rgb565: ByteArray): TopStepPackage {
        val payload = template.payload
        val entry = backgroundEntry(payload)
            ?: throw DialFormatException("Template has no BMP background image")

        val expected = entry.width * entry.height * 2
        if (rgb565.size != expected) {
            throw DialFormatException(
                "Image must be ${entry.width}x${entry.height} " +
                    "(${expected} bytes of RGB565), got ${rgb565.size}",
            )
        }

        val blobOffset = entry.offset
        val fileSize = ByteBuffer.wrap(payload, blobOffset + 2, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        if (blobOffset + fileSize > payload.size) {
            throw DialFormatException("BMP blob runs past the end of the payload")
        }

        val header = payload.copyOfRange(blobOffset, blobOffset + fileSize)
        val dataOffset = ByteBuffer.wrap(header, 10, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int
        val pixelBytes = fileSize - dataOffset
        if (pixelBytes != expected) {
            throw DialFormatException(
                "Template BMP holds $pixelBytes pixel bytes, expected $expected",
            )
        }

        val out = payload.copyOf()
        System.arraycopy(rgb565, 0, out, blobOffset + dataOffset, rgb565.size)

        return template.copy(payload = out, totalSize = template.headerSize + out.size)
    }

    /**
     * Converts ARGB8888 pixels to the byte order the watch expects.
     *
     * The template BMPs declare compression=3 (BI_BITFIELDS) with masks
     * 0xF800 / 0x07E0 / 0x001F, which is standard RGB565. The samples store the
     * low byte first, so output is little-endian.
     */
    fun argbToRgb565Le(argb: IntArray): ByteArray {
        val out = ByteArray(argb.size * 2)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val v = ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }
}
