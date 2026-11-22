package com.example

import com.example.security.WebCacheManager
import com.example.security.WebPageErrorDetector
import com.example.security.WebPageErrorDetector.DetectedErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPageProtectionTest {

  @Test
  fun `detects chromium error pages such as ERR_HTTP_RESPONSE_CODE_FAILURE and disconnected`() {
    assertEquals(
      DetectedErrorKind.SERVER_OR_HTTP_ERROR,
      WebPageErrorDetector.detectError(
        title = "Halaman web tidak tersedia",
        bodySnippet =
          "Halaman web di https://septianfr.web.id/mail/support/settings.php tidak dapat dimuat karena: net::ERR_HTTP_RESPONSE_CODE_FAILURE",
        bodyLength = 150,
      ),
    )

    assertEquals(
      DetectedErrorKind.CHROMIUM_DISCONNECTED,
      WebPageErrorDetector.detectError(
        title = "Webpage not available",
        bodySnippet = "net::ERR_INTERNET_DISCONNECTED",
        bodyLength = 80,
      ),
    )
  }

  @Test
  fun `detects cloudflare 525 and http 500 or php fatal pages`() {
    assertEquals(
      DetectedErrorKind.SERVER_OR_HTTP_ERROR,
      WebPageErrorDetector.detectError(
        title = "septianfr.web.id | 525: SSL handshake failed",
        bodySnippet = "SSL handshake failed Error code 525 Cloudflare Origin Web Server",
        bodyLength = 240,
      ),
    )

    assertEquals(
      DetectedErrorKind.SERVER_OR_HTTP_ERROR,
      WebPageErrorDetector.detectError(
        title = "500 Internal Server Error",
        bodySnippet = "The server encountered an internal error and was unable to complete your request.",
        bodyLength = 120,
      ),
    )

    assertEquals(
      DetectedErrorKind.SERVER_OR_HTTP_ERROR,
      WebPageErrorDetector.detectError(
        title = "",
        bodySnippet = "Fatal error: Uncaught Error: Call to undefined function foo() in /var/www/html/settings.php on line 42",
        bodyLength = 104,
      ),
    )

    assertEquals(
      DetectedErrorKind.EMPTY_PAGE,
      WebPageErrorDetector.detectError(title = "", bodySnippet = "", bodyLength = 0),
    )
  }

  @Test
  fun `allows valid support septian pages without false positive`() {
    assertNull(
      WebPageErrorDetector.detectError(
        title = "Support Septian - Dashboard",
        bodySnippet = "Selamat datang di layanan Support Septian. Tiket aktif: 5. Kirim Email Bantuan.",
        bodyLength = 420,
      )
    )
  }

  @Test
  fun `identifies cacheable static assets accurately`() {
    assertTrue(WebCacheManager.isCacheableStaticAsset("https://septianfr.web.id/assets/app.css?v=2"))
    assertTrue(WebCacheManager.isCacheableStaticAsset("https://septianfr.web.id/js/bundle.min.js"))
    assertTrue(WebCacheManager.isCacheableStaticAsset("https://septianfr.web.id/fonts/inter.woff2"))
    assertTrue(WebCacheManager.isCacheableStaticAsset("https://septianfr.web.id/img/logo.png"))

    assertFalse(WebCacheManager.isCacheableStaticAsset("https://septianfr.web.id/mail/support/login.php"))
    assertFalse(WebCacheManager.isCacheableStaticAsset("https://septianfr.web.id/mail/support/api/send"))
  }

  @Test
  fun `extracts safe sibling support links and excludes logout or mutating links`() {
    val html =
      """
      <html><body>
        <a href="dashboard.php">Dashboard</a>
        <a href="/mail/support/inbox.php">Inbox</a>
        <a href="settings.php#section">Settings</a>
        <a href="logout.php">Keluar</a>
        <a href="inbox.php?action=delete&id=1">Hapus</a>
        <a href="https://other.example.com/phish">External</a>
      </body></html>
      """
    val links =
      WebCacheManager.extractSafeLinkedUrls(
        baseUrl = "https://septianfr.web.id/mail/support/index.php",
        html = html,
      )
    assertEquals(
      listOf(
        "https://septianfr.web.id/mail/support/dashboard.php",
        "https://septianfr.web.id/mail/support/inbox.php",
        "https://septianfr.web.id/mail/support/settings.php",
      ),
      links,
    )
    assertTrue(WebCacheManager.isLogoutUrl("https://septianfr.web.id/mail/support/logout.php"))
    assertFalse(WebCacheManager.isLogoutUrl("https://septianfr.web.id/mail/support/dashboard.php"))
  }
}
