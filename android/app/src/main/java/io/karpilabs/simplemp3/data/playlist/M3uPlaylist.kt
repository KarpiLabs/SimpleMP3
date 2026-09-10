package io.karpilabs.simplemp3.data.playlist

import io.karpilabs.simplemp3.data.local.TrackEntity
import java.net.URLDecoder

/**
 * Extended M3U (#EXTM3U) reader/writer. Locations may be content URIs, file paths,
 * or relative names — matching against the local library never auto-imports files.
 */
object M3uPlaylist {
    data class Entry(
        val location: String,
        val title: String? = null,
        val artist: String? = null,
        val durationSec: Int? = null,
    ) {
        val fileName: String
            get() =
                location
                    .substringBefore('?')
                    .substringBefore('#')
                    .trimEnd('/')
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .let { decoded ->
                        runCatching { URLDecoder.decode(decoded, Charsets.UTF_8.name()) }.getOrDefault(decoded)
                    }

        val displayTitle: String
            get() = title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
    }

    data class MatchResult(
        val matched: List<TrackEntity>,
        val unmatched: List<Entry>,
        val playlistName: String,
    )

    fun parse(
        text: String,
        defaultName: String = "Imported playlist",
    ): Pair<String, List<Entry>> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val entries = mutableListOf<Entry>()
        var pendingTitle: String? = null
        var pendingArtist: String? = null
        var pendingDuration: Int? = null
        var playlistName = defaultName

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.equals("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                    val name = line.substringAfter(':').trim()
                    if (name.isNotEmpty()) playlistName = name
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.removePrefix("#EXTINF:").removePrefix("#extinf:")
                    val comma = body.indexOf(',')
                    val durationPart = if (comma >= 0) body.substring(0, comma) else body
                    val titlePart = if (comma >= 0) body.substring(comma + 1).trim() else ""
                    pendingDuration = durationPart.trim().substringBefore('.').toIntOrNull()?.takeIf { it >= 0 }
                    val parsed = splitArtistTitle(titlePart)
                    pendingArtist = parsed.first
                    pendingTitle = parsed.second
                }
                line.startsWith("#") -> Unit
                else -> {
                    entries +=
                        Entry(
                            location = line,
                            title = pendingTitle,
                            artist = pendingArtist,
                            durationSec = pendingDuration,
                        )
                    pendingTitle = null
                    pendingArtist = null
                    pendingDuration = null
                }
            }
        }
        return playlistName to entries
    }

    fun write(
        name: String,
        tracks: List<TrackEntity>,
    ): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        if (name.isNotBlank()) {
            sb.append("#PLAYLIST:").append(name.trim()).append('\n')
        }
        for (track in tracks) {
            val seconds = if (track.duration > 0) (track.duration / 1000L).toInt() else -1
            val title = "${track.artist} - ${track.title}"
            sb.append("#EXTINF:").append(seconds).append(',').append(title).append('\n')
            sb.append(exportLocation(track)).append('\n')
        }
        return sb.toString()
    }

    fun match(
        entries: List<Entry>,
        library: List<TrackEntity>,
        playlistName: String,
    ): MatchResult {
        if (entries.isEmpty()) {
            return MatchResult(emptyList(), emptyList(), playlistName)
        }
        val songs = library.filter { !it.isStream && !it.isHidden }
        val byUri = HashMap<String, TrackEntity>(songs.size * 2)
        val byFileName = HashMap<String, MutableList<TrackEntity>>()
        for (track in songs) {
            normalizeLocation(track.uri)?.let { byUri[it] = track }
            val name = fileNameOf(track.uri)
            if (name.isNotEmpty()) {
                byFileName.getOrPut(name.lowercase()) { mutableListOf() }.add(track)
            }
        }

        val matched = LinkedHashSet<Long>()
        val matchedTracks = mutableListOf<TrackEntity>()
        val unmatched = mutableListOf<Entry>()

        for (entry in entries) {
            val hit = matchEntry(entry, byUri, byFileName, songs)
            if (hit != null && matched.add(hit.id)) {
                matchedTracks += hit
            } else if (hit == null) {
                unmatched += entry
            }
        }
        return MatchResult(matchedTracks, unmatched, playlistName)
    }

    internal fun splitArtistTitle(raw: String): Pair<String?, String?> {
        val text = raw.trim()
        if (text.isEmpty()) return null to null
        val sep = " - "
        val idx = text.indexOf(sep)
        if (idx <= 0) return null to text
        val artist = text.substring(0, idx).trim().ifBlank { null }
        val title = text.substring(idx + sep.length).trim().ifBlank { text }
        return artist to title
    }

    internal fun normalizeLocation(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        var s = trimmed
        if (s.startsWith("file://", ignoreCase = true)) {
            s = s.removePrefix("file://").removePrefix("FILE://")
            s = runCatching { URLDecoder.decode(s, Charsets.UTF_8.name()) }.getOrDefault(s)
        }
        s = s.replace('\\', '/')
        return s.lowercase()
    }

    internal fun fileNameOf(location: String): String =
        location
            .substringBefore('?')
            .substringBefore('#')
            .trimEnd('/')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .let { decoded ->
                runCatching { URLDecoder.decode(decoded, Charsets.UTF_8.name()) }.getOrDefault(decoded)
            }

    private fun exportLocation(track: TrackEntity): String = track.uri

    private fun matchEntry(
        entry: Entry,
        byUri: Map<String, TrackEntity>,
        byFileName: Map<String, MutableList<TrackEntity>>,
        songs: List<TrackEntity>,
    ): TrackEntity? {
        normalizeLocation(entry.location)?.let { loc ->
            byUri[loc]?.let { return it }
            // Absolute path may be a suffix of a stored file:// URI or vice versa.
            val suffixHit =
                byUri.entries.firstOrNull { (key, _) ->
                    key.endsWith(loc) || loc.endsWith(key)
                }
            if (suffixHit != null) return suffixHit.value
        }

        val fileName = entry.fileName.lowercase()
        if (fileName.isNotEmpty()) {
            val nameHits = byFileName[fileName]
            if (nameHits != null && nameHits.size == 1) return nameHits[0]
            if (nameHits != null && nameHits.size > 1) {
                durationPick(entry, nameHits)?.let { return it }
            }
        }

        val title = entry.title?.trim().orEmpty()
        if (title.isNotEmpty()) {
            val titleHits =
                songs.filter {
                    it.title.equals(title, ignoreCase = true) &&
                        (
                            entry.artist.isNullOrBlank() ||
                                it.artist.equals(entry.artist, ignoreCase = true)
                        )
                }
            if (titleHits.size == 1) return titleHits[0]
            durationPick(entry, titleHits)?.let { return it }
        }
        return null
    }

    private fun durationPick(
        entry: Entry,
        candidates: List<TrackEntity>,
    ): TrackEntity? {
        val want = entry.durationSec ?: return null
        if (want <= 0) return null
        val close =
            candidates.filter {
                if (it.duration <= 0) return@filter false
                kotlin.math.abs(it.duration / 1000L - want) <= 2
            }
        return close.singleOrNull()
    }
}
