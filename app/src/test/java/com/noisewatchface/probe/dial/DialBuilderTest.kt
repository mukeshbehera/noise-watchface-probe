package com.noisewatchface.probe.dial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialBuilderTest {

    private fun template(): TopStepPackage =
        TopStepParser.parse(
            javaClass.classLoader!!.getResourceAsStream("gui_dial_sample.bin")!!.readBytes(),
        )

    @Test
    fun `reads the image table and finds a BMP background`() {
        val pkg = template()
        val table = DialBuilder.readImageTable(pkg.payload)

        assertTrue("expected several image entries", table.size >= 2)
        val bmp = table.filter { it.isBmp }
        assertEquals("sample has two BMP entries", 2, bmp.size)
        assertEquals(368, bmp[0].width)
        assertEquals(448, bmp[0].height)
    }

    @Test
    fun `background is the largest image`() {
        val entry = DialBuilder.backgroundEntry(template().payload)
        assertNotNull(entry)
        assertEquals(368, entry!!.width)
        assertEquals(448, entry.height)
    }

    @Test
    fun `replaces the background and keeps every length identical`() {
        val pkg = template()
        val entry = DialBuilder.backgroundEntry(pkg.payload)!!
        val pixels = ByteArray(entry.width * entry.height * 2) { 0x18 }
        val replaced = DialBuilder.replaceBackground(pkg, pixels)

        assertEquals("payload length must not change", pkg.payloadSize, replaced.payload.size)
        assertEquals("total length must not change", pkg.totalSize, replaced.totalSize)

        // The rebuilt container must still parse and round-trip.
        val roundTripped = TopStepParser.parse(TopStepParser.serialise(replaced))
        assertEquals(pkg.payloadSize, roundTripped.payloadSize)

        // Table entries must still resolve to BMPs at the same offsets.
        val origTable = DialBuilder.readImageTable(pkg.payload)
        val newTable = DialBuilder.readImageTable(replaced.payload)
        assertEquals(origTable.size, newTable.size)
        origTable.zip(newTable).forEach { (a, b) ->
            assertEquals(a.offset, b.offset)
            assertEquals(a.isBmp, b.isBmp)
        }
    }

    @Test
    fun `the replaced pixels are actually written`() {
        val pkg = template()
        val entry = DialBuilder.backgroundEntry(pkg.payload)!!
        val pixels = ByteArray(entry.width * entry.height * 2) { 0x5A }
        val replaced = DialBuilder.replaceBackground(pkg, pixels)

        val dataOffset = java.nio.ByteBuffer.wrap(replaced.payload, entry.offset + 10, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        val start = entry.offset + dataOffset
        assertEquals(0x5A.toByte(), replaced.payload[start])
        assertEquals(0x5A.toByte(), replaced.payload[start + pixels.size - 1])
        // Bytes outside the image data are untouched.
        assertEquals(pkg.payload[entry.offset], replaced.payload[entry.offset])
    }

    @Test
    fun `rejects a wrongly sized image`() {
        val pkg = template()
        val error = runCatching {
            DialBuilder.replaceBackground(pkg, ByteArray(1024))
        }.exceptionOrNull()
        assertTrue(error is DialFormatException)
        assertTrue(error!!.message!!.contains("RGB565"))
    }

    @Test
    fun `converts ARGB to little-endian RGB565`() {
        // Pure red: r=0xFF g=0 b=0 -> 0xF800, stored low byte first.
        val bytes = DialBuilder.argbToRgb565Le(intArrayOf(0xFFFF0000.toInt()))
        assertEquals(2, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0xF8.toByte(), bytes[1])

        // Pure white -> 0xFFFF
        val white = DialBuilder.argbToRgb565Le(intArrayOf(0xFFFFFFFF.toInt()))
        assertEquals(0xFF.toByte(), white[0])
        assertEquals(0xFF.toByte(), white[1])

        // Pure black -> 0x0000
        val black = DialBuilder.argbToRgb565Le(intArrayOf(0xFF000000.toInt()))
        assertEquals(0x00.toByte(), black[0])
        assertEquals(0x00.toByte(), black[1])
    }

    @Test
    fun `rgb565 conversion preserves channel ordering, not just brightness`() {
        // Pure blue 0x0000FF -> 0x001F
        val blue = DialBuilder.argbToRgb565Le(intArrayOf(0xFF0000FF.toInt()))
        assertEquals(0x1F.toByte(), blue[0])
        assertEquals(0x00.toByte(), blue[1])

        // A green-dominant value must land in the middle bits.
        val green = DialBuilder.argbToRgb565Le(intArrayOf(0xFF00FF00.toInt()))
        val v = (green[0].toInt() and 0xFF) or ((green[1].toInt() and 0xFF) shl 8)
        assertEquals(0x07E0, v)
        assertFalse(v == 0)
    }
}
