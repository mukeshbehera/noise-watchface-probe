package com.noisewatchface.probe.dial

/**
 * A container the user selected, plus whatever the parser could tell us.
 *
 * Nothing here is sent to the watch. Every stage after selection is a local
 * inspection step until the push path exists.
 */
data class DialCandidate(
    val displayName: String,
    val byteCount: Int,
    val parsed: TopStepPackage?,
    val error: String?,
) {
    val valid: Boolean get() = parsed != null

    val payloadSummary: String
        get() = parsed?.let { "payload ${it.payloadSize} bytes, magic \"${it.payloadMagic}\"" }
            ?: error.orEmpty()

    companion object {
        fun from(displayName: String, bytes: ByteArray): DialCandidate =
            try {
                val pkg = TopStepParser.parse(bytes)
                DialCandidate(displayName, bytes.size, pkg, null)
            } catch (e: DialFormatException) {
                DialCandidate(displayName, bytes.size, null, e.message ?: "Unrecognised container")
            }
    }
}

/** A template container available for building a face from an image. */
data class DialTemplate(
    val displayName: String,
    val packageBytes: ByteArray,
    val width: Int,
    val height: Int,
) {
    /** Parsed on demand; templates are read once and used a few times. */
    val parsed: TopStepPackage
        get() = TopStepParser.parse(packageBytes)

    companion object {
        /** [bytes] must be a complete TOPSTEP container. */
        fun from(displayName: String, bytes: ByteArray): DialTemplate? {
            val pkg = runCatching { TopStepParser.parse(bytes) }.getOrNull() ?: return null
            val bg = runCatching { DialBuilder.backgroundEntry(pkg.payload) }.getOrNull()
                ?: return null
            return DialTemplate(displayName, bytes, bg.width, bg.height)
        }
    }
}

/**
 * What the user is building: a template plus their chosen image, once processed.
 */
data class FaceDraft(
    val template: DialTemplate,
    val imageName: String?,
    val ready: Boolean,
    val note: String,
)
