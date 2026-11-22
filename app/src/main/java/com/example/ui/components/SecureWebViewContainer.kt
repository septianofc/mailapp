package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.R
import com.example.security.AndroidNetworkMonitor
import com.example.security.SafeLogger
import com.example.security.WebCacheManager
import com.example.security.WebPageErrorDetector
import com.example.security.WebPageErrorDetector.DetectedErrorKind
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

private const val ABOUT_BLANK = "about:blank"

private const val PAGE_INSPECTION_JS = """
  (function() {
    try {
      var title = (document.title || '').trim();
      var bodyText = (document.body && document.body.innerText ? document.body.innerText : '').trim();
      var html = (document.documentElement && document.documentElement.outerHTML ? document.documentElement.outerHTML : '');
      return {
        title: title,
        snippet: bodyText.substring(0, 750),
        length: bodyText.length,
        html: html.length <= 4000000 ? html : ''
      };
    } catch (e) {
      return { title: '', snippet: '', length: 0, html: '' };
    }
  })();
"""

@Composable
fun SecureWebViewContainer(
  webErrorState: WebErrorState,
  isNetworkOnline: Boolean,
  reloadTrigger: Int,
  goHomeTrigger: Int,
  blockedNotice: String?,
  onPageStarted: () -> Unit,
  onPageFinished: () -> Unit,
  onNetworkError: (String) -> Unit,
  onSslError: () -> Unit,
  onNavigationBlocked: (String) -> Unit,
  onClearBlockedNotice: () -> Unit,
  onRetry: () -> Unit,
  onGoHome: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val ioScope = rememberCoroutineScope()
  val webCacheManager = remember(context) { WebCacheManager(context) }
  val onlineFlag = remember {
    AtomicBoolean(isNetworkOnline && AndroidNetworkMonitor.isOnlineNow(context))
  }

  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var canGoBack by remember { mutableStateOf(false) }
  var lastAttemptedUrl by remember { mutableStateOf(WebViewUrlPolicy.ENTRY_URL) }
  var lastValidUrl by remember { mutableStateOf(WebViewUrlPolicy.ENTRY_URL) }

  // Tracks URL that just failed so Chromium's subsequent internal error-page onPageStarted/Finish
  // cannot wipe out the active native error overlay or expose "Halaman web tidak tersedia".
  var lastFailedUrl by remember { mutableStateOf<String?>(null) }
  var lastFailedAtMs by remember { mutableLongStateOf(0L) }

  // Keeps WebView invisible until both network/HTTP checks AND DOM error inspection succeed.
  var isPageVisible by remember { mutableStateOf(false) }
  var currentLoadHadError by remember { mutableStateOf(false) }

  val blockedMessage = stringResource(id = R.string.webview_blocked_url_message)
  val disconnectedMsg = stringResource(id = R.string.webview_error_disconnected)
  val timeoutMsg = stringResource(id = R.string.webview_error_timeout)
  val serverErrorMsg = stringResource(id = R.string.webview_error_server)
  val notFoundMsg = stringResource(id = R.string.webview_error_not_found)
  val forbiddenMsg = stringResource(id = R.string.webview_error_forbidden)
  val genericErrorMsg = stringResource(id = R.string.webview_error_generic)

  LaunchedEffect(isNetworkOnline) {
    onlineFlag.set(isNetworkOnline)
    webViewRef?.settings?.cacheMode =
      if (isNetworkOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
  }

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
        lastFailedUrl = null
        lastFailedAtMs = 0L
        isPageVisible = false
        currentLoadHadError = false
        webCacheManager.requestForceRefresh()
        webView.visibility = View.INVISIBLE
        val target =
          if (WebViewUrlPolicy.isAllowedNavigationUrl(lastAttemptedUrl)) {
            lastAttemptedUrl
          } else if (onlineFlag.get()) {
            WebViewUrlPolicy.ENTRY_URL
          } else {
            webCacheManager.getBestOfflineStartUrl()
          }
        webView.loadUrl(target)
      }
    }
  }

  LaunchedEffect(goHomeTrigger) {
    if (goHomeTrigger > 0) {
      webViewRef?.let { webView ->
        lastFailedUrl = null
        lastFailedAtMs = 0L
        isPageVisible = false
        currentLoadHadError = false
        val homeUrl =
          if (onlineFlag.get()) {
            WebViewUrlPolicy.ENTRY_URL
          } else {
            webCacheManager.getBestOfflineStartUrl()
          }
        lastAttemptedUrl = homeUrl
        lastValidUrl = homeUrl
        webView.visibility = View.INVISIBLE
        webView.clearHistory()
        webView.loadUrl(homeUrl)
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
        val initialOnline = AndroidNetworkMonitor.isOnlineNow(ctx)
        onlineFlag.set(initialOnline)
        val initialStartUrl =
          if (initialOnline) {
            WebViewUrlPolicy.ENTRY_URL
          } else {
            webCacheManager.getBestOfflineStartUrl()
          }
        lastAttemptedUrl = initialStartUrl
        lastValidUrl = initialStartUrl

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        WebView(ctx).apply {
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          visibility = View.INVISIBLE
          isVerticalScrollBarEnabled = false
          isHorizontalScrollBarEnabled = false
          overScrollMode = View.OVER_SCROLL_NEVER

          configureHardenedSettings(this, initialOnline)
          configureSecureCookies(this)

          fun triggerNativeFailure(
            view: WebView?,
            failedUrl: String?,
            message: String,
            isSsl: Boolean = false,
          ) {
            currentLoadHadError = true
            isPageVisible = false
            if (!failedUrl.isNullOrBlank() && failedUrl != ABOUT_BLANK) {
              lastFailedUrl = failedUrl
              lastFailedAtMs = System.currentTimeMillis()
            }
            view?.visibility = View.INVISIBLE
            view?.stopLoading()
            view?.post {
              if (view.url != ABOUT_BLANK) {
                view.loadUrl(ABOUT_BLANK)
              }
            }
            if (isSsl) {
              onSslError()
            } else {
              onNetworkError(message)
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
                  if (url != null && WebCacheManager.isLogoutUrl(url)) {
                    webCacheManager.clearOfflinePages()
                  }
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
                if (request == null) return null
                val url = request.url?.toString()
                if (url == ABOUT_BLANK) return null

                if (!WebViewUrlPolicy.isAllowedSubresourceUrl(url)) {
                  SafeLogger.w("Blocked non-HTTPS or disallowed subresource scheme")
                  return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "Blocked by Security Policy",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                  )
                }

                // Uses persistent full-source vault for fast conditional revalidation & offline mode
                return webCacheManager.interceptRequest(
                  request = request,
                  isOnline = onlineFlag.get(),
                )
              }

              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url.isNullOrBlank() || url == ABOUT_BLANK) {
                  return
                }
                if (!WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
                  view?.stopLoading()
                  return
                }

                // Guard against Chromium re-invoking onPageStarted for its built-in error page
                // on the exact URL that just failed moments earlier.
                val isRecentFailedUrl =
                  url == lastFailedUrl && (System.currentTimeMillis() - lastFailedAtMs) < 2500L
                if (isRecentFailedUrl) {
                  view?.visibility = View.INVISIBLE
                  view?.stopLoading()
                  return
                }

                lastAttemptedUrl = url
                currentLoadHadError = false
                isPageVisible = false
                view?.visibility = View.INVISIBLE
                canGoBack = view?.canGoBack() == true
                onPageStarted()
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                if (url.isNullOrBlank() || url == ABOUT_BLANK) {
                  view?.visibility = View.INVISIBLE
                  isPageVisible = false
                  return
                }
                canGoBack = view?.canGoBack() == true
                CookieManager.getInstance().flush()

                if (currentLoadHadError || !WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
                  view?.visibility = View.INVISIBLE
                  isPageVisible = false
                  return
                }

                // Inspect rendered DOM before ever making WebView visible.
                // Blocks Chromium error pages, Cloudflare 52x, HTTP 5xx/4xx HTML, PHP fatals, and blanks.
                val inspectedView = view ?: return
                inspectedView.evaluateJavascript(PAGE_INSPECTION_JS) { rawResult ->
                  if (currentLoadHadError || inspectedView.url == ABOUT_BLANK) {
                    inspectedView.visibility = View.INVISIBLE
                    isPageVisible = false
                    return@evaluateJavascript
                  }

                  val parsed = parseInspectionJson(rawResult)
                  val title = parsed?.optString("title").orEmpty()
                  val snippet = parsed?.optString("snippet").orEmpty()
                  val bodyLength = parsed?.optInt("length", 0) ?: 0
                  val html = parsed?.optString("html").orEmpty()

                  val detected =
                    WebPageErrorDetector.detectError(
                      title = title,
                      bodySnippet = snippet,
                      bodyLength = bodyLength,
                    )

                  if (detected != null) {
                    SafeLogger.w("Blocked rendered error page kind=$detected")
                    val friendly =
                      when (detected) {
                        DetectedErrorKind.CHROMIUM_DISCONNECTED -> disconnectedMsg
                        DetectedErrorKind.CHROMIUM_TIMEOUT -> timeoutMsg
                        DetectedErrorKind.NOT_FOUND -> notFoundMsg
                        DetectedErrorKind.FORBIDDEN -> forbiddenMsg
                        DetectedErrorKind.SSL_OR_HANDSHAKE -> ""
                        DetectedErrorKind.SERVER_OR_HTTP_ERROR -> serverErrorMsg
                        DetectedErrorKind.EMPTY_PAGE -> genericErrorMsg
                      }
                    triggerNativeFailure(
                      view = inspectedView,
                      failedUrl = url,
                      message = friendly,
                      isSsl = detected == DetectedErrorKind.SSL_OR_HANDSHAKE,
                    )
                  } else {
                    lastValidUrl = url
                    if (html.isNotBlank() && !WebCacheManager.isLogoutUrl(url)) {
                      ioScope.launch(Dispatchers.IO) {
                        if (!webCacheManager.hasFreshPageCache(url)) {
                          webCacheManager.saveHtmlSnapshot(url = url, html = html)
                        }
                        if (onlineFlag.get()) {
                          webCacheManager.prefetchLinkedPagesAsync(baseUrl = url, html = html)
                        }
                      }
                    }
                    inspectedView.visibility = View.VISIBLE
                    isPageVisible = true
                    onPageFinished()
                  }
                }
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
              ) {
                if (request?.isForMainFrame == true) {
                  val failedUrl = request.url?.toString()
                  val errorCode = error?.errorCode ?: ERROR_UNKNOWN
                  val rawDesc = error?.description?.toString().orEmpty()
                  SafeLogger.w("WebView main-frame error code=$errorCode")

                  val friendlyMessage =
                    mapFriendlyNetworkErrorMessage(
                      errorCode = errorCode,
                      rawDescription = rawDesc,
                      disconnectedMsg = disconnectedMsg,
                      timeoutMsg = timeoutMsg,
                      serverErrorMsg = serverErrorMsg,
                      genericMsg = genericErrorMsg,
                    )
                  triggerNativeFailure(view = view, failedUrl = failedUrl, message = friendlyMessage)
                }
              }

              override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
              ) {
                val statusCode = errorResponse?.statusCode ?: 0
                if (request?.isForMainFrame == true && statusCode >= 400) {
                  val failedUrl = request.url?.toString()
                  SafeLogger.w("WebView main-frame HTTP error status=$statusCode")

                  val friendlyMessage =
                    when (statusCode) {
                      401, 403 -> forbiddenMsg
                      404 -> notFoundMsg
                      in 500..599 -> serverErrorMsg
                      else -> genericErrorMsg
                    }
                  triggerNativeFailure(view = view, failedUrl = failedUrl, message = friendlyMessage)
                }
              }

              override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
              ) {
                // Strictly refuse untrusted SSL certificates — NEVER call handler.proceed()
                handler?.cancel()
                SafeLogger.w("Strict SSL validation rejected certificate error=${error?.primaryError}")
                triggerNativeFailure(
                  view = view,
                  failedUrl = error?.url ?: lastAttemptedUrl,
                  message = "",
                  isSsl = true,
                )
              }

              override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
              ): Boolean {
                triggerNativeFailure(
                  view = view,
                  failedUrl = lastAttemptedUrl,
                  message = genericErrorMsg,
                )
                return true
              }
            }

          loadUrl(initialStartUrl)
          webViewRef = this
        }
      },
      update = { webView ->
        webViewRef = webView
        onlineFlag.set(isNetworkOnline)
        webView.settings.cacheMode =
          if (isNetworkOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK
        webView.visibility =
          if (isPageVisible && webErrorState is WebErrorState.None) View.VISIBLE else View.INVISIBLE
      },
    )

    // Native branded loading state (no browser-style top progress bar)
    AnimatedVisibility(
      visible = !isPageVisible && webErrorState is WebErrorState.None,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(
        modifier = Modifier.fillMaxSize().background(PureWhite).testTag("native_loading_overlay"),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Surface(
            shape = CircleShape,
            color = PureWhite,
            shadowElevation = 6.dp,
            modifier = Modifier.size(72.dp).border(2.dp, BrandBluePrimary, CircleShape),
          ) {
            Image(
              painter = painterResource(id = R.drawable.brand_logo),
              contentDescription = stringResource(id = R.string.cd_brand_logo),
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize().padding(3.dp).clip(CircleShape),
            )
          }
          Spacer(modifier = Modifier.height(18.dp))
          CircularProgressIndicator(
            color = BrandBluePrimary,
            trackColor = BrandBlueSoftContainer,
            strokeWidth = 3.dp,
            modifier = Modifier.size(32.dp),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = context.getString(R.string.webview_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBlueDark,
          )
        }
      }
    }

    // Blocked navigation feedback pill
    AnimatedVisibility(
      visible = blockedNotice != null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 76.dp),
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

    // Full native error overlay for all HTTP, network, Cloudflare, or SSL errors
    if (webErrorState !is WebErrorState.None) {
      WebErrorOverlay(
        errorState = webErrorState,
        onGoHome = onGoHome,
        onRetry = onRetry,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

private fun parseInspectionJson(rawResult: String?): JSONObject? {
  if (rawResult.isNullOrBlank() || rawResult == "null") return null
  return runCatching {
    val parsed = JSONTokener(rawResult).nextValue()
    when (parsed) {
      is JSONObject -> parsed
      is String -> JSONObject(parsed)
      else -> null
    }
  }.getOrNull()
}

private fun mapFriendlyNetworkErrorMessage(
  errorCode: Int,
  rawDescription: String,
  disconnectedMsg: String,
  timeoutMsg: String,
  serverErrorMsg: String,
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
    descUpper.contains("HTTP_RESPONSE_CODE_FAILURE") ||
      errorCode == WebViewClient.ERROR_BAD_URL ||
      errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> serverErrorMsg
    else -> genericMsg
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureHardenedSettings(webView: WebView, isNetworkOnline: Boolean) {
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
    cacheMode =
      if (isNetworkOnline) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_CACHE_ELSE_NETWORK

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
fun WebErrorOverlay(
  errorState: WebErrorState,
  onGoHome: () -> Unit,
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
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
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
          onClick = onGoHome,
          colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("webview_go_home_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Home,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = stringResource(id = R.string.webview_error_go_home))
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
          onClick = onRetry,
          border = BorderStroke(1.dp, BrandBlueBorder),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("webview_retry_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = BrandBlueDark,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(id = R.string.webview_error_retry),
            color = BrandBlueDark,
          )
        }
      }
    }
  }
}
