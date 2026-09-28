## 2026-08-24 - Content-Security-Policy for QuickConnect HTTP Portal
**Vulnerability:** Embedded LAN HTTP server (`QuickConnectServer`) lacked CSP and XSS protection headers, which could allow script execution or clickjacking if untrusted input or content was rendered.
**Learning:** NanoHTTPD responses in local HTTP servers need security headers (`Content-Security-Policy`, `X-XSS-Protection`) explicitly attached to prevent web vulnerabilities in embedded portals.
**Prevention:** Always set strict security headers via a centralized helper function (`applySecurityHeaders`) on all HTTP responses served by embedded local web services.

## 2026-08-25 - Path Traversal Prevention in Jellyfin Downloads
**Vulnerability:** Downloader constructed destination file URLs directly from server-provided metadata (`item.title`, `item.Id`, `item.Container`) without sanitization or path verification, allowing malicious Jellyfin servers to write outside target directories via `..` or path separators.
**Learning:** External file metadata from remote media servers must be sanitized (stripping `/`, `\`, leading dots/spaces, `..`) and destination paths checked with `dest.path.hasPrefix(...)` against target directory.
**Prevention:** Always sanitize external file metadata and verify destination file paths reside strictly within allowed app directories prior to file write operations.

## 2026-08-26 - URI Deletion Path Traversal Prevention in YouTube Downloads
**Vulnerability:** `YoutubeDownloadManager.deleteFileUriSafely` parsed track URIs without validating the `file:` scheme or enforcing directory confinement within `offline/youtube`, potentially allowing arbitrary file deletion if a track URI was crafted with path traversal.
**Learning:** Deleting files by URI string requires verifying the `file:` scheme, parsing URI paths robustly across both Android SDK and JVM unit test environments, and checking `canonicalPath.startsWith(allowedDir.canonicalPath + File.separator)`.
**Prevention:** Always check `uri.startsWith("file:")` and validate canonical target path confinement within the allowed base directory before performing file deletion operations.
