package com.example.security

/**
 * Inspects rendered WebView page title/text for browser error pages, Cloudflare errors,
 * HTTP 4xx/5xx server error pages, PHP fatal crashes, or blank broken responses.
 */
object WebPageErrorDetector {

  enum class DetectedErrorKind {
    CHROMIUM_DISCONNECTED,
    CHROMIUM_TIMEOUT,
    SERVER_OR_HTTP_ERROR,
    NOT_FOUND,
    FORBIDDEN,
    SSL_OR_HANDSHAKE,
    EMPTY_PAGE,
  }

  fun detectError(
    title: String?,
    bodySnippet: String?,
    bodyLength: Int,
  ): DetectedErrorKind? {
    val safeTitle = title.orEmpty().trim().lowercase()
    val safeSnippet = bodySnippet.orEmpty().trim().lowercase()
    val combined = "$safeTitle\n$safeSnippet"

    // 1. Blank / completely empty document
    if (bodyLength <= 0 && safeTitle.isEmpty()) {
      return DetectedErrorKind.EMPTY_PAGE
    }

    // 2. Chromium built-in error page signatures ("Halaman web tidak tersedia" / "Webpage not available")
    val hasChromiumErrorTitle =
      safeTitle.contains("tidak tersedia") ||
        safeTitle.contains("not available") ||
        safeTitle.contains("net::err_")
    val hasChromiumErrorBody =
      combined.contains("halaman web tidak tersedia") ||
        combined.contains("webpage not available") ||
        combined.contains("web page not available") ||
        combined.contains("net::err_")

    if (hasChromiumErrorTitle || hasChromiumErrorBody) {
      return when {
        combined.contains("err_internet_disconnected") ||
          combined.contains("err_name_not_resolved") ||
          combined.contains("err_address_unreachable") ||
          combined.contains("err_network_changed") -> DetectedErrorKind.CHROMIUM_DISCONNECTED
        combined.contains("err_timed_out") ||
          combined.contains("err_connection_timed_out") -> DetectedErrorKind.CHROMIUM_TIMEOUT
        combined.contains("err_ssl") ||
          combined.contains("err_cert") -> DetectedErrorKind.SSL_OR_HANDSHAKE
        else -> DetectedErrorKind.SERVER_OR_HTTP_ERROR
      }
    }

    // 3. Cloudflare error pages (e.g., Error code 525 SSL handshake failed, 520-526, 502/504)
    val isCloudflareError =
      combined.contains("cloudflare") &&
        (combined.contains("error code 52") ||
          combined.contains("ssl handshake failed") ||
          combined.contains("origin web server") ||
          combined.contains("host error") ||
          combined.contains("bad gateway") ||
          combined.contains("connection timed out"))
    if (isCloudflareError || combined.contains("error code 525") || combined.contains("ssl handshake failed")) {
      return DetectedErrorKind.SERVER_OR_HTTP_ERROR
    }

    // 4. Short HTTP server error pages (Apache/Nginx/LiteSpeed/PHP fatal crashes)
    if (bodyLength < 2200) {
      when {
        safeTitle.contains("500 internal server error") ||
          safeTitle.contains("http error 500") ||
          safeTitle.contains("502 bad gateway") ||
          safeTitle.contains("503 service unavailable") ||
          safeTitle.contains("504 gateway timeout") ||
          combined.contains("internal server error") && combined.contains("the server encountered") ||
          combined.contains("bad gateway") && combined.contains("nginx") ||
          combined.contains("service unavailable") && combined.contains("temporarily") ->
          return DetectedErrorKind.SERVER_OR_HTTP_ERROR

        safeTitle.contains("404 not found") ||
          safeTitle.contains("http error 404") ||
          safeTitle == "404" ||
          (combined.contains("404 not found") && combined.contains("requested url")) ->
          return DetectedErrorKind.NOT_FOUND

        safeTitle.contains("403 forbidden") ||
          safeTitle.contains("http error 403") ||
          (combined.contains("403 forbidden") && combined.contains("you don't have permission")) ->
          return DetectedErrorKind.FORBIDDEN

        (combined.contains("fatal error:") || combined.contains("parse error:") || combined.contains("uncaught error:")) &&
          combined.contains("on line") ->
          return DetectedErrorKind.SERVER_OR_HTTP_ERROR
      }
    }

    return null
  }
}
