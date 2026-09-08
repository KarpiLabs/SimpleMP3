package io.karpilabs.simplemp3.data.normalization

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlin.math.pow

/**
 * ReplayGain-based loudness normalization.
 *
 * ReplayGain stores a per-track gain (in dB) in file tags so players can play every
 * track at a consistent perceived loudness. Because Android's [android.media.audiofx]
 * chain can only attenuate (ExoPlayer volume maxes at 1.0), we never boost above unity
 * — the usual case since RG track gains are almost always negative.
 */
object VolumeNormalizer {
    private const val RG_TRACK_GAIN_KEY = "REPLAYGAIN_TRACK_GAIN"

    /** Parse a ReplayGain value like "-6.48 dB" or "3.21" into a Double, or null. */
    fun parseGainDb(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        // Strip a trailing "dB" (any case) and whitespace, keep leading sign / decimals.
        val cleaned =
            raw
                .trim()
                .removeSuffix("dB")
                .removeSuffix("DB")
                .removeSuffix("db")
                .trim()
        return cleaned.toDoubleOrNull()
    }

    /**
     * Extract the track-gain value (dB) from decoded stream [Metadata]. Handles ID3v2
     * TXXX frames (MP3/AAC) and Vorbis comments (FLAC/OGG/Opus).
     */
    @OptIn(UnstableApi::class)
    fun trackGainFromMetadata(metadata: Metadata): Double? {
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is TextInformationFrame -> {
                    if (entry.description.equals(RG_TRACK_GAIN_KEY, ignoreCase = true)) {
                        val value = entry.values.firstOrNull() ?: entry.value
                        parseGainDb(value)?.let { return it }
                    }
                }
                is VorbisComment -> {
                    if (entry.key.equals(RG_TRACK_GAIN_KEY, ignoreCase = true)) {
                        parseGainDb(entry.value)?.let { return it }
                    }
                }
                else -> {}
            }
        }
        return null
    }

    /**
     * Linear ExoPlayer volume for a track. Returns 1.0 when normalization is off or the
     * track has no known gain, so playback is never quieter than the user's own level
     * for un-tagged tracks.
     */
    fun linearVolume(
        gainDb: Double?,
        preampDb: Int,
        enabled: Boolean,
    ): Float {
        if (!enabled || gainDb == null) return 1f
        val totalDb = gainDb + preampDb
        val linear = 10.0.pow(totalDb / 20.0)
        return linear.coerceIn(0.0, 1.0).toFloat()
    }
}
