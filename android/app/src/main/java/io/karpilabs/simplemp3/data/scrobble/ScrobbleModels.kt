package io.karpilabs.simplemp3.data.scrobble

/** Where to send listens. */
data class ScrobbleConfig(
    val provider: String,
    val enabled: Boolean,
    val listenBrainzToken: String,
    val lastfmSessionKey: String,
    val lastfmUsername: String,
    val lastfmApiKey: String,
    val lastfmApiSecret: String,
) {
    val isListenBrainz: Boolean get() = provider == PROVIDER_LISTENBRAINZ
    val isLastfm: Boolean get() = provider == PROVIDER_LASTFM

    /** True when the selected provider has everything it needs to submit. */
    val isReady: Boolean
        get() =
            enabled &&
                when (provider) {
                    PROVIDER_LISTENBRAINZ -> listenBrainzToken.isNotBlank()
                    PROVIDER_LASTFM ->
                        lastfmSessionKey.isNotBlank() &&
                            lastfmApiKey.isNotBlank() &&
                            lastfmApiSecret.isNotBlank()
                    else -> false
                }

    companion object {
        const val PROVIDER_NONE = "none"
        const val PROVIDER_LISTENBRAINZ = "listenbrainz"
        const val PROVIDER_LASTFM = "lastfm"
    }
}

/**
 * A single listen awaiting submission. Kept minimal so it serializes cheaply and
 * survives across app / network restarts.
 */
data class PendingScrobble(
    val artist: String,
    val track: String,
    val album: String,
    val durationSec: Int,
    /** Unix seconds when the track started playing. */
    val timestamp: Long,
)
