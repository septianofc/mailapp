package com.example

import com.example.security.WebViewUrlPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewUrlPolicyTest {

  @Test
  fun `allows exact https septianfr web id navigation urls`() {
    org.junit.Assert.assertEquals("septianfr.web.id", WebViewUrlPolicy.ALLOWED_HOST)
    org.junit.Assert.assertEquals(
      "https://septianfr.web.id/mail/support/login.php",
      WebViewUrlPolicy.ENTRY_URL,
    )
    org.junit.Assert.assertEquals("/mail/support/", WebViewUrlPolicy.SUPPORT_PATH_PREFIX)

    assertTrue(WebViewUrlPolicy.isAllowedNavigationUrl(WebViewUrlPolicy.ENTRY_URL))
    assertTrue(WebViewUrlPolicy.isAllowedNavigationUrl("https://septianfr.web.id/mail/support/"))
    assertTrue(WebViewUrlPolicy.isAllowedNavigationUrl("https://SEPTIANFR.WEB.ID/dashboard"))
  }

  @Test
  fun `blocks http cleartext and external domains`() {
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("http://septianfr.web.id/mail/support/login.php"))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("https://google.com"))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("https://evil-septianfr.web.id/login"))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("https://septianfr.web.id.evil.com/login"))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("https://septianfr.web.id@evil.com/login"))
  }

  @Test
  fun `blocks dangerous schemes for navigation and subresources`() {
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("file:///android_asset/test.html"))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("content://com.example/file"))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("javascript:alert(1)"))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("intent://scan/#Intent;end"))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl(null))
    assertFalse(WebViewUrlPolicy.isAllowedNavigationUrl("   "))

    assertFalse(WebViewUrlPolicy.isAllowedSubresourceUrl("http://cdn.example.com/app.js"))
    assertFalse(WebViewUrlPolicy.isAllowedSubresourceUrl("file:///etc/passwd"))
    assertFalse(WebViewUrlPolicy.isAllowedSubresourceUrl("content://media/external/file"))
    assertTrue(WebViewUrlPolicy.isAllowedSubresourceUrl("https://septianfr.web.id/assets/app.css"))
    assertTrue(WebViewUrlPolicy.isAllowedSubresourceUrl("data:image/png;base64,iVBORw0KGgo="))
  }
}
