package com.noisewatchface.probe.dial

/**
 * Pure-Kotlin centre-crop scaler, so the resize logic is testable without an
 * Android graphics stack.
 *
 * Pixels are ARGB8888 in row-major order.
 */
object ImageScaler {

    data class Bitmap(val width: Int, val height: Int, val pixels: IntArray)

    /**
     * Scales [src] to cover [dstW] x [dstH] and centre-crops the overflow.
     *
     * Cover rather than fit: a watch face should fill the panel, and cropping the
     * long edge is less objectionable than letterboxing.
     */
    fun coverCrop(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        require(src.width > 0 && src.height > 0) { "source image is empty" }
        require(dstW > 0 && dstH > 0) { "target size must be positive" }

        val scale = maxOf(
            dstW.toDouble() / src.width,
            dstH.toDouble() / src.height,
        )
        val scaledW = (src.width * scale).toInt().coerceAtLeast(dstW)
        val scaledH = (src.height * scale).toInt().coerceAtLeast(dstH)
        val offsetX = (scaledW - dstW) / 2
        val offsetY = (scaledH - dstH) / 2

        val out = IntArray(dstW * dstH)
        for (y in 0 until dstH) {
            val sy = (((y + offsetY) / scale).toInt()).coerceIn(0, src.height - 1)
            for (x in 0 until dstW) {
                val sx = (((x + offsetX) / scale).toInt()).coerceIn(0, src.width - 1)
                out[y * dstW + x] = src.pixels[sy * src.width + sx]
            }
        }
        return Bitmap(dstW, dstH, out)
    }
}
