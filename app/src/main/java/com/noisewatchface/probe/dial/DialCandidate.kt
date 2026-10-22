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
