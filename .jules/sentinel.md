## 2026-08-24 - Content-Security-Policy for QuickConnect HTTP Portal
**Vulnerability:** Embedded LAN HTTP server (`QuickConnectServer`) lacked CSP and XSS protection headers, which could allow script execution or clickjacking if untrusted input or content was rendered.
**Learning:** NanoHTTPD responses in local HTTP servers need security headers (`Content-Security-Policy`, `X-XSS-Protection`) explicitly attached to prevent web vulnerabilities in embedded portals.
**Prevention:** Always set strict security headers via a centralized helper function (`applySecurityHeaders`) on all HTTP responses served by embedded local web services.

## 2026-08-25 - Path Traversal Prevention in Jellyfin Downloads
**Vulnerability:** Downloader constructed destination file URLs directly from server-provided metadata (`item.title`, `item.Id`, `item.Container`) without sanitization or path verification, allowing malicious Jellyfin servers to write outside target directories via `..` or path separators.
**Learning:** External file metadata from remote media servers must be sanitized (stripping `/`, `\`, leading dots/spaces, `..`) and destination paths checked with `dest.path.hasPrefix(...)` against target directory.
**Prevention:** Always sanitize external file metadata and verify destination file paths reside strictly within allowed app directories prior to file write operations.

## 2026-08-26 - URL Scheme Validation in Stream Artwork Resolution
**Vulnerability:** iOS `StreamSaver` parsed artwork/station URLs from untrusted HTML meta tags, HLS playlists, and ICY headers and fetched them via `URLSession` without scheme validation, allowing `file://` scheme resolution to read local files.
**Learning:** `URLSession` natively handles `file://` URLs in Foundation. Untrusted URLs extracted from remote metadata must be validated to ensure they strictly use `http` or `https` schemes before requesting data.
**Prevention:** Always enforce `url.scheme == "http" || url.scheme == "https"` when resolving or fetching external metadata URLs in iOS network routines.
