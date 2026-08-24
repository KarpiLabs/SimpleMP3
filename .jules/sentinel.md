## 2026-08-24 - Content-Security-Policy for QuickConnect HTTP Portal
**Vulnerability:** Embedded LAN HTTP server (`QuickConnectServer`) lacked CSP and XSS protection headers, which could allow script execution or clickjacking if untrusted input or content was rendered.
**Learning:** NanoHTTPD responses in local HTTP servers need security headers (`Content-Security-Policy`, `X-XSS-Protection`) explicitly attached to prevent web vulnerabilities in embedded portals.
**Prevention:** Always set strict security headers via a centralized helper function (`applySecurityHeaders`) on all HTTP responses served by embedded local web services.
