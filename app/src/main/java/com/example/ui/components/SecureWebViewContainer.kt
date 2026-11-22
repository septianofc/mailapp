package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.R
import com.example.security.SafeLogger
import com.example.security.WebViewUrlPolicy
import com.example.ui.WebErrorState
import com.example.ui.theme.BrandBlueBorder
import com.example.ui.theme.BrandBlueDark
import com.example.ui.theme.BrandBluePrimary
import com.example.ui.theme.BrandBlueSoftContainer
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecurityDangerBorder
import com.example.ui.theme.SecurityDangerRed
import com.example.ui.theme.SecurityDangerRedContainer
import com.example.ui.theme.SoftWhiteBackground
import java.io.ByteArrayInputStream

@Composable
fun SecureWebViewContainer(
  webProgress: Int,
  isWebLoading: Boolean,
  webErrorState: WebErrorState,
  reloadTrigger: Int,
  blockedNotice: String?,
  onProgressChanged: (Int) -> Unit,
  onPageStarted: () -> Unit,
  onPageFinished: () -> Unit,
  onNetworkError: (String) -> Unit,
  onSslError: () -> Unit,
  onNavigationBlocked: (String) -> Unit,
  onClearBlockedNotice: () -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var canGoBack by remember { mutableStateOf(false) }
  var lastValidUrl by remember { mutableStateOf(WebViewUrlPolicy.ENTRY_URL) }

  // Keeps WebView invisible on error and during reload so Chromium's built-in
  // error page (which exposes the URL) is never visible to the user.
  var isPageVisible by remember { mutableStateOf(false) }
  var currentLoadHadError by remember { mutableStateOf(false) }

  val blockedMessage = stringResource(id = R.string.webview_blocked_url_message)
  val disconnectedMsg = stringResource(id = R.string.webview_error_disconnected)
  val timeoutMsg = stringResource(id = R.string.webview_error_timeout)
  val genericErrorMsg = stringResource(id = R.string.webview_error_generic)

  BackHandler(enabled = canGoBack) {
    webViewRef?.let { webView ->
      if (webView.canGoBack()) {
        webView.goBack()
      }
    }
  }

  LaunchedEffect(reloadTrigger) {
    if (reloadTrigger > 0) {
      webViewRef?.let { webView ->
        isPageVisible = false
        currentLoadHadError = false
        webView.visibility = View.INVISIBLE
        webView.loadUrl(lastValidUrl)
      }
    }
  }

  LaunchedEffect(blockedNotice) {
    if (blockedNotice != null) {
      kotlinx.coroutines.delay(3200L)
      onClearBlockedNotice()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      CookieManager.getInstance().flush()
      webViewRef?.apply {
        stopLoading()
        destroy()
      }
      webViewRef = null
    }
  }

  Box(modifier = modifier.fillMaxSize().background(PureWhite).testTag("secure_webview_container")) {
    AndroidView(
      modifier = Modifier.fillMaxSize().testTag("mail_septian_webview"),
      factory = { ctx ->
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        WebView(ctx).apply {
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          visibility = View.INVISIBLE

          configureHardenedSettings(this)
          configureSecureCookies(this)

          webChromeClient =
            object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
              }
            }

          webViewClient =
            object : WebViewClient() {
              override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
              ): Boolean {
                val url = request?.url?.toString()
                return if (WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
                  false
                } else {
                  SafeLogger.w("Blocked navigation outside allowlist/HTTPS")
                  if (request?.isForMainFrame == true) {
                    onNavigationBlocked(blockedMessage)
                  }
                  true
                }
              }

              override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?,
              ): WebResourceResponse? {
                val url = request?.url?.toString()
                return if (WebViewUrlPolicy.isAllowedSubresourceUrl(url)) {
                  null
                } else {
                  SafeLogger.w("Blocked non-HTTPS or disallowed subresource scheme")
                  WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "Blocked by Security Policy",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                  )
                }
              }

              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
                  view?.stopLoading()
                  return
                }
                if (url != null) {
                  lastValidUrl = url
                }
                currentLoadHadError = false
                canGoBack = view?.canGoBack() == true
                onPageStarted()
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                canGoBack = view?.canGoBack() == true
                CookieManager.getInstance().flush()
                if (!currentLoadHadError && WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
                  view?.visibility = View.VISIBLE
                  isPageVisible = true
                  onPageFinished()
                }
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
              ) {
                if (request?.isForMainFrame == true) {
                  currentLoadHadError = true
                  isPageVisible = false
                  view?.visibility = View.INVISIBLE

                  val errorCode = error?.errorCode ?: ERROR_UNKNOWN
                  val rawDesc = error?.description?.toString().orEmpty()
                  SafeLogger.w("WebView main-frame network error code=$errorCode")

                  val friendlyMessage =
                    mapFriendlyErrorMessage(
                      errorCode = errorCode,
                      rawDescription = rawDesc,
                      disconnectedMsg = disconnectedMsg,
                      timeoutMsg = timeoutMsg,
                      genericMsg = genericErrorMsg,
                    )
                  onNetworkError(friendlyMessage)
                }
              }

              override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
              ) {
                // Strictly refuse untrusted SSL certificates — NEVER call handler.proceed()
                handler?.cancel()
                currentLoadHadError = true
                isPageVisible = false
                view?.visibility = View.INVISIBLE
                SafeLogger.w("Strict SSL validation rejected certificate error=${error?.primaryError}")
                onSslError()
              }
            }

          loadUrl(WebViewUrlPolicy.ENTRY_URL)
          webViewRef = this
        }
      },
      update = { webView ->
        webViewRef = webView
        webView.visibility =
          if (isPageVisible && webErrorState is WebErrorState.None) View.VISIBLE else View.INVISIBLE
      },
    )

    // Opaque loading veil before initial render or while reconnecting after error
    AnimatedVisibility(
      visible = !isPageVisible && webErrorState is WebErrorState.None,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(
        modifier = Modifier.fillMaxSize().background(PureWhite),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(
            color = BrandBluePrimary,
            trackColor = BrandBlueSoftContainer,
            modifier = Modifier.size(40.dp),
          )
          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = context.getString(R.string.webview_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBlueDark,
          )
        }
      }
    }

    // Slim top progress indicator while loading
    AnimatedVisibility(
      visible = isWebLoading && webErrorState is WebErrorState.None,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter),
    ) {
      LinearProgressIndicator(
        progress = { (webProgress.coerceIn(0, 100)) / 100f },
        color = BrandBluePrimary,
        trackColor = BrandBlueSoftContainer,
        modifier = Modifier.fillMaxWidth().height(3.dp).testTag("webview_progress_bar"),
      )
    }

    // Blocked navigation feedback pill
    AnimatedVisibility(
      visible = blockedNotice != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = BrandBlueDark,
        shadowElevation = 6.dp,
        modifier = Modifier.testTag("blocked_navigation_notice"),
      ) {
        Text(
          text = blockedNotice.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = PureWhite,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
      }
    }

    // Full error overlay for network or strict SSL validation errors (never displays URL)
    if (webErrorState !is WebErrorState.None) {
      WebErrorOverlay(
        errorState = webErrorState,
        onRetry = onRetry,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

private fun mapFriendlyErrorMessage(
  errorCode: Int,
  rawDescription: String,
  disconnectedMsg: String,
  timeoutMsg: String,
  genericMsg: String,
): String {
  val descUpper = rawDescription.uppercase()
  return when {
    errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
      errorCode == WebViewClient.ERROR_CONNECT ||
      descUpper.contains("DISCONNECTED") ||
      descUpper.contains("NAME_NOT_RESOLVED") ||
      descUpper.contains("ADDRESS_UNREACHABLE") ||
      descUpper.contains("NETWORK_CHANGED") -> disconnectedMsg
    errorCode == WebViewClient.ERROR_TIMEOUT || descUpper.contains("TIMED_OUT") -> timeoutMsg
    else -> genericMsg
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureHardenedSettings(webView: WebView) {
  webView.removeJavascriptInterface("searchBoxJavaBridge_")
  webView.removeJavascriptInterface("accessibility")
  webView.removeJavascriptInterface("accessibilityTraversal")

  webView.settings.apply {
    javaScriptEnabled = true
    javaScriptCanOpenWindowsAutomatically = false
    setSupportMultipleWindows(false)

    allowFileAccess = false
    allowContentAccess = false
    @Suppress("DEPRECATION")
    allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    allowUniversalAccessFromFileURLs = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

    domStorageEnabled = true
    @Suppress("DEPRECATION")
    databaseEnabled = false
    setGeolocationEnabled(false)
    @Suppress("DEPRECATION")
    saveFormData = false
    cacheMode = WebSettings.LOAD_DEFAULT

    useWideViewPort = true
    loadWithOverviewMode = true
    builtInZoomControls = false
    displayZoomControls = false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      safeBrowsingEnabled = true
    }
  }
}

private fun configureSecureCookies(webView: WebView) {
  val cookieManager = CookieManager.getInstance()
  cookieManager.setAcceptCookie(true)
  cookieManager.setAcceptThirdPartyCookies(webView, false)
}

@Composable
private fun WebErrorOverlay(
  errorState: WebErrorState,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isSslError = errorState is WebErrorState.SslValidationError
  Box(
    modifier = modifier.background(SoftWhiteBackground).padding(24.dp).testTag("webview_error_overlay"),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = PureWhite),
      elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
      border =
        BorderStroke(
          1.dp,
          if (isSslError) SecurityDangerBorder else BrandBlueBorder.copy(alpha = 0.6f),
        ),
      modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = if (isSslError) SecurityDangerRedContainer else BrandBlueSoftContainer,
          modifier = Modifier.size(64.dp),
        ) {
          Icon(
            imageVector = if (isSslError) Icons.Rounded.GppBad else Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = if (isSslError) SecurityDangerRed else BrandBluePrimary,
            modifier = Modifier.padding(16.dp),
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text =
            stringResource(
              id = if (isSslError) R.string.webview_ssl_error_title else R.string.webview_error_title
            ),
          style = MaterialTheme.typography.headlineSmall,
          color = if (isSslError) SecurityDangerRed else BrandBlueDark,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text =
            when (errorState) {
              WebErrorState.SslValidationError ->
                stringResource(id = R.string.webview_ssl_error_desc)
              is WebErrorState.NetworkError -> errorState.description
              WebErrorState.None -> ""
            },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(22.dp))

        Button(
          onClick = onRetry,
          colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("webview_retry_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(id = R.string.webview_error_retry))
        }
      }
    }
  }
}
