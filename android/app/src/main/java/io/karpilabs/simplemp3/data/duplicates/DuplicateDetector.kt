package io.karpilabs.simplemp3.data.duplicates

import io.karpilabs.simplemp3.data.local.TrackEntity

/**
 * Groups likely-duplicate rips (same title + artist + duration) so the user can
 * hide extras or merge playlist membership onto a keeper. Never deletes files.
 */
object DuplicateDetector {
    /** Duration bucket width — 2 seconds. */
    const val DURATION_BUCKET_MS: Long = 2_000L

    data class Group(
        val key: String,
        val tracks: List<TrackEntity>,
        val keepId: Long,
    ) {
        val extras: List<TrackEntity> get() = tracks.filter { it.id != keepId }
        val keeper: TrackEntity get() = tracks.first { it.id == keepId }
    }

    fun findGroups(tracks: List<TrackEntity>): List<Group> {
        val songs = tracks.filter { !it.isStream && !it.isHidden }
        val buckets = LinkedHashMap<String, MutableList<TrackEntity>>()
        for (track in songs) {
            val key = fingerprint(track) ?: continue
            buckets.getOrPut(key) { mutableListOf() }.add(track)
        }
        return buckets.values
            .filter { it.size >= 2 }
            .map { group ->
                val ordered = group.sortedWith(keepComparator)
                Group(
                    key = fingerprint(ordered.first()) ?: ordered.first().id.toString(),
                    tracks = ordered,
                    keepId = ordered.first().id,
                )
            }.sortedBy { it.keeper.title.lowercase() }
    }

    fun fingerprint(track: TrackEntity): String? {
        val title = normalize(track.title)
        val artist = normalize(track.artist)
        if (title.isEmpty() || title == "unknown title") return null
        if (artist.isEmpty() || artist == "unknown artist") return null
        val bucket =
            if (track.duration > 0) {
                track.duration / DURATION_BUCKET_MS
            } else {
                -1L
            }
        return "$title|$artist|$bucket"
    }

    internal fun normalize(value: String): String =
        value
            .trim()
            .lowercase()
            .replace(Regex("[\\p{Punct}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Prefer the copy that looks like the "real" library rip: more plays, lives
     * under Music/, larger file, has art. Never prefers deleting.
     */
    internal val keepComparator: Comparator<TrackEntity> =
        compareByDescending<TrackEntity> { it.playCount }
            .thenByDescending { musicFolderScore(it.folderPath) }
            .thenByDescending { it.size }
            .thenByDescending { if (it.artworkUri.isNullOrBlank()) 0 else 1 }
            .thenBy { it.id }
}

private fun musicFolderScore(folderPath: String): Int {
    val path = folderPath.lowercase()
    return when {
        path == "music" || path.startsWith("music/") -> 2
        path.contains("/music/") || path.endsWith("/music") -> 1
        else -> 0
    }
}
