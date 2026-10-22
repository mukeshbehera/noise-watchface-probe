package com.noisewatchface.probe.dial

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageScalerTest {

    private fun solid(w: Int, h: Int, color: Int) =
        ImageScaler.Bitmap(w, h, IntArray(w * h) { color })

    /** Left half red, right half blue. */
    private fun split(w: Int, h: Int): ImageScaler.Bitmap {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            px[y * w + x] = if (x < w / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        return ImageScaler.Bitmap(w, h, px)
    }

    @Test
    fun `output is always the requested size`() {
        val out = ImageScaler.coverCrop(solid(100, 50, 0xFF00FF00.toInt()), 368, 448)
        assertEquals(368, out.width)
        assertEquals(448, out.height)
        assertEquals(368 * 448, out.pixels.size)
    }

    @Test
    fun `a solid image stays solid`() {
        val out = ImageScaler.coverCrop(solid(10, 10, 0xFF123456.toInt()), 20, 30)
        assertEquals(20 * 30, out.pixels.size)
        out.pixels.forEach { assertEquals(0xFF123456.toInt(), it) }
    }

    @Test
    fun `downscaling a wide image crops left and right edges`() {
        // 400x100 -> 100x100: scale 1.0 vertically, but width is cropped from 400 to 100.
        val src = split(400, 100)
        val out = ImageScaler.coverCrop(src, 100, 100)
        // The centre strip of a 400-wide two-colour image is still two-coloured.
        val left = out.pixels[50 * 100 + 10]
        val right = out.pixels[50 * 100 + 90]
        assertEquals(0xFFFF0000.toInt(), left)
        assertEquals(0xFF0000FF.toInt(), right)
    }

    @Test
    fun `upscaling a tall image crops top and bottom`() {
        val src = solid(50, 400, 0xFF00FFFF.toInt())
        val out = ImageScaler.coverCrop(src, 100, 100)
        assertEquals(100, out.width)
        assertEquals(100, out.height)
        out.pixels.forEach { assertEquals(0xFF00FFFF.toInt(), it) }
    }

    @Test
    fun `upscaling a small square works`() {
        val out = ImageScaler.coverCrop(solid(2, 2, 0xFF808080.toInt()), 368, 448)
        assertEquals(368, out.width)
        assertEquals(448, out.height)
    }

    @Test
    fun `same aspect ratio maps without distortion`() {
        val src = split(100, 200)
        val out = ImageScaler.coverCrop(src, 50, 100)
        assertEquals(50, out.width)
        assertEquals(100, out.height)
        assertEquals(0xFFFF0000.toInt(), out.pixels[50 * 50 + 2])
    }
}
