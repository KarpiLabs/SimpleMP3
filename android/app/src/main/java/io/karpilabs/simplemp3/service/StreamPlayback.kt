package io.karpilabs.simplemp3.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Live-stream extras on the media session, plus helpers to read bitrate from the
 * player and format a compact "Live · 128 kbps" label.
 */
object StreamPlayback {
    const val EXTRA_IS_LIVE = "simplemp3.is_live"
    const val EXTRA_BITRATE_BPS = "simplemp3.bitrate_bps"
    const val EXTRA_THROUGHPUT_BPS = "simplemp3.throughput_bps"

    fun isLivePlayback(player: Player): Boolean {
        if (player.isCurrentMediaItemLive) return true
        val item = player.currentMediaItem ?: return false
        if (item.mediaId.startsWith(MediaIds.STREAM_PREFIX)) return true
        val uri = item.localConfiguration?.uri ?: return false
        val http =
            uri.scheme.equals("http", ignoreCase = true) ||
                uri.scheme.equals("https", ignoreCase = true)
        if (!http) return false
        val duration = player.duration
        return duration <= 0L || duration == C.TIME_UNSET
    }

    @OptIn(UnstableApi::class)
    fun selectedAudioBitrateBps(player: Player): Int {
        val tracks = player.currentTracks
        for (i in 0 until tracks.groups.size) {
            val group = tracks.groups[i]
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (j in 0 until group.length) {
                if (!group.isTrackSelected(j)) continue
                val format = group.getTrackFormat(j)
                val bitrate = firstPositive(format.bitrate, format.averageBitrate, format.peakBitrate)
                if (bitrate > 0) return bitrate
            }
        }
        return 0
    }

    fun formatDataRate(bps: Long): String {
        if (bps <= 0L) return ""
        val kbps = (bps + 500L) / 1000L
        return if (kbps >= 1000L) {
            val mbps = kbps / 1000.0
            if (mbps >= 10) "${mbps.toInt()} Mbps" else "%.1f Mbps".format(mbps)
        } else {
            "$kbps kbps"
        }
    }

    fun dataRateLabel(
        isLive: Boolean,
        bitrateBps: Int,
        throughputBps: Long,
    ): String? {
        if (!isLive) return null
        val bps = if (bitrateBps > 0) bitrateBps.toLong() else throughputBps
        return if (bps > 0L) "Live · ${formatDataRate(bps)}" else "Live"
    }

    private fun firstPositive(vararg values: Int): Int {
        for (v in values) {
            if (v != Format.NO_VALUE && v > 0) return v
        }
        return 0
    }
}
