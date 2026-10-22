package com.noisewatchface.probe.dial

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Golden-file tests against a container shipped inside the official app.
 *
 * The round-trip assertion is the important one: if parse→serialise reproduces
 * the input byte for byte, the model accounts for every byte of the file.
 */
class TopStepParserTest {

    private fun sample(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("gui_dial_sample.bin")!!.readBytes()

    @Test
    fun `parses the shipped sample header`() {
        val pkg = TopStepParser.parse(sample())

        assertEquals(TopStepPackage.HEADER_SIZE, pkg.headerSize)
        assertEquals(1024, pkg.header.size)
        assertEquals(898724, pkg.totalSize)
        assertEquals(897700, pkg.payloadSize)
        assertEquals(0x00340001, pkg.version)
        assertEquals("TBUI", pkg.payloadMagic)
        assertTrue(
            "container name should describe the dial file",
            pkg.name.startsWith("gui_dial"),
        )
        assertTrue("name should mention ota", pkg.name.contains("ota"))
    }

    @Test
    fun `round-trips byte for byte`() {
        val original = sample()
        val rebuilt = TopStepParser.serialise(TopStepParser.parse(original))
        assertArrayEquals(original, rebuilt)
    }

    @Test
    fun `rejects a file that is not a TOPSTEP container`() {
        val bad = ByteArray(TopStepPackage.HEADER_SIZE) { 0x41 }
        val error = runCatching { TopStepParser.parse(bad) }.exceptionOrNull()
        assertTrue(error is DialFormatException)
        assertTrue(error!!.message!!.contains("Not a TOPSTEP container"))
    }

    @Test
    fun `rejects a truncated file`() {
        val error = runCatching { TopStepParser.parse(ByteArray(64)) }.exceptionOrNull()
        assertTrue(error is DialFormatException)
    }

    @Test
    fun `rejects a container whose declared size disagrees with its length`() {
        val truncated = sample().copyOf(1000)
        val error = runCatching { TopStepParser.parse(truncated) }.exceptionOrNull()
        assertTrue(error is DialFormatException)
    }
}
