package io.karpilabs.simplemp3.data.local

/**
 * Hierarchical helpers for [TrackEntity.folderPath] values
 * (MediaStore-style relative paths without a trailing slash).
 */
object FolderBrowser {

    data class FolderEntry(
        /** Full relative path, e.g. "Music/Rock". */
        val path: String,
        /** Last segment shown in the list, e.g. "Rock". */
        val name: String,
        /** Direct tracks in this folder only (not descendants). */
        val directTrackCount: Int,
        /** Tracks in this folder and all descendants. */
        val totalTrackCount: Int,
        val hasSubfolders: Boolean
    )

    fun normalize(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return path.trim().trim('/').replace('\\', '/')
    }

    fun displayName(path: String): String {
        val n = normalize(path)
        if (n.isEmpty()) return "Folders"
        return n.substringAfterLast('/')
    }

    fun parentPath(path: String): String? {
        val n = normalize(path)
        if (n.isEmpty()) return null
        val slash = n.lastIndexOf('/')
        return if (slash <= 0) "" else n.substring(0, slash)
    }

    /**
     * Immediate child folders of [parentPath] (empty string = library roots).
     */
    fun childFolders(
        allFolderPaths: List<String>,
        parentPath: String,
        trackCountByFolder: Map<String, Int> = emptyMap()
    ): List<FolderEntry> {
        val parent = normalize(parentPath)
        val prefix = if (parent.isEmpty()) "" else "$parent/"
        val childNames = linkedMapOf<String, String>() // name -> full child path

        for (raw in allFolderPaths) {
            val path = normalize(raw)
            if (path.isEmpty()) continue
            if (parent.isEmpty()) {
                val first = path.substringBefore('/')
                childNames.putIfAbsent(first, first)
            } else if (path.startsWith(prefix)) {
                val rest = path.removePrefix(prefix)
                if (rest.isEmpty()) continue
                val first = rest.substringBefore('/')
                childNames.putIfAbsent(first, "$parent/$first")
            } else if (path == parent) {
                // tracks live here; not a child folder
            }
        }

        return childNames.values.map { childPath ->
            val direct = trackCountByFolder[childPath] ?: 0
            val descendantPrefix = "$childPath/"
            var total = direct
            var hasSubs = false
            for ((folder, count) in trackCountByFolder) {
                if (folder.startsWith(descendantPrefix)) {
                    total += count
                    hasSubs = true
                }
            }
            // Also detect subfolders that might have zero tracks counted only via path list
            if (!hasSubs) {
                hasSubs = allFolderPaths.any {
                    val p = normalize(it)
                    p.startsWith(descendantPrefix)
                }
            }
            FolderEntry(
                path = childPath,
                name = displayName(childPath),
                directTrackCount = direct,
                totalTrackCount = total,
                hasSubfolders = hasSubs
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** Whether [folderPath] is under any of [roots] (or roots empty = allow all). */
    fun matchesAnyRoot(folderPath: String, roots: Set<String>): Boolean {
        if (roots.isEmpty()) return true
        val path = normalize(folderPath)
        if (path.isEmpty()) return false
        return roots.any { rawRoot ->
            val root = normalize(rawRoot)
            root.isNotEmpty() && (path == root || path.startsWith("$root/"))
        }
    }
}
