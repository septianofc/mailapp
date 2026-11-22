package com.example.security

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/**
 * Persistent on-device vault cache for full-source HTML pages (single-file PHP+HTML+CSS+Icons)
 * and external static assets, supporting conditional ETag/Last-Modified/SHA-256 updates and
 * full offline viewing.
 *
 * Security guarantees:
 * - Only caches GET requests over verified HTTPS matching WebViewUrlPolicy.
 * - Uses Android's default strict TLS certificate and hostname validation.
 * - Stored strictly inside app-private filesDir (allowBackup=false) and cleared on logout.
 */
class WebCacheManager(context: Context) {

  private val vaultRootDir =
    File(context.applicationContext.filesDir, "secure_web_vault").apply { mkdirs() }
  private val pagesDir = File(vaultRootDir, "pages").apply { mkdirs() }
  private val assetsDir = File(vaultRootDir, "assets").apply { mkdirs() }
  private val indexFile = File(vaultRootDir, "vault_index.json")

  private val bgExecutor = Executors.newFixedThreadPool(2)
  private val inFlightRevalidations = ConcurrentHashMap.newKeySet<String>()

  @Volatile private var bypassFreshCacheUntilMs: Long = 0L
  @Volatile private var forceNextPageRefresh: Boolean = false

  fun markMutation() {
    bypassFreshCacheUntilMs = System.currentTimeMillis() + MUTATION_BYPASS_WINDOW_MS
  }

  fun requestForceRefresh() {
    forceNextPageRefresh = true
  }

  fun interceptRequest(
    request: WebResourceRequest,
    isOnline: Boolean,
  ): WebResourceResponse? {
    val url = request.url?.toString() ?: return null

    // Never cache mutating requests; bypass fresh page cache briefly after POST/PUT
    if (!request.method.equals("GET", ignoreCase = true)) {
      if (WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
        markMutation()
      }
      return null
    }

    if (!WebViewUrlPolicy.isAllowedSubresourceUrl(url)) return null
    if (!url.startsWith("https://", ignoreCase = true)) return null

    if (request.isForMainFrame) {
      if (!WebViewUrlPolicy.isAllowedNavigationUrl(url)) return null
      if (isLogoutUrl(url)) {
        clearOfflinePages()
        return null
      }
      return serveOrRevalidatePage(
        url = url,
        requestHeaders = request.requestHeaders,
        isOnline = isOnline,
      )
    }

    if (isCacheableStaticAsset(url)) {
      return serveOrRevalidateAsset(
        url = url,
        requestHeaders = request.requestHeaders,
        isOnline = isOnline,
      )
    }

    return null
  }

  fun getBestOfflineStartUrl(): String {
    val lastActive = readLastActiveUrl()
    if (!lastActive.isNullOrBlank() && hasCachedHtml(lastActive)) {
      return lastActive
    }
    if (hasCachedHtml(WebViewUrlPolicy.ENTRY_URL)) {
      return WebViewUrlPolicy.ENTRY_URL
    }
    val mostRecentMeta =
      pagesDir
        .listFiles { file -> file.name.endsWith(".json") }
        ?.sortedByDescending { it.lastModified() }
        ?.firstNotNullOfOrNull { readMeta(it)?.optString("url") }
        ?.takeIf { WebViewUrlPolicy.isAllowedNavigationUrl(it) && !isLogoutUrl(it) }
    return mostRecentMeta ?: WebViewUrlPolicy.ENTRY_URL
  }

  fun hasCachedHtml(url: String): Boolean {
    val key = sha256(normalizePageUrl(url))
    val file = File(pagesDir, "$key.html")
    return file.exists() && file.length() > 0L
  }

  fun hasFreshPageCache(url: String): Boolean {
    val key = sha256(normalizePageUrl(url))
    val file = File(pagesDir, "$key.html")
    val meta = readMeta(File(pagesDir, "$key.json")) ?: return false
    val checkedAt = meta.optLong("checkedAt", 0L)
    return file.exists() &&
      file.length() > 0L &&
      (System.currentTimeMillis() - checkedAt) < PAGE_FRESH_WINDOW_MS
  }

  fun saveHtmlSnapshot(url: String, html: String) {
    if (!WebViewUrlPolicy.isAllowedNavigationUrl(url)) return
    if (isLogoutUrl(url)) return
    val bytes = html.toByteArray(Charsets.UTF_8)
    if (bytes.isEmpty() || bytes.size > MAX_SINGLE_HTML_BYTES) return

    persistPageBytes(
      url = url,
      bytes = bytes,
      encoding = "UTF-8",
      etag = "",
      lastModified = "",
    )
  }

  fun prefetchLinkedPagesAsync(baseUrl: String, html: String) {
    if (html.isBlank() || !WebViewUrlPolicy.isAllowedNavigationUrl(baseUrl)) return
    val links = extractSafeLinkedUrls(baseUrl = baseUrl, html = html)
    val assets = extractStaticAssetUrls(baseUrl = baseUrl, html = html)

    bgExecutor.execute {
      for (assetUrl in assets.take(MAX_PREFETCH_ASSETS)) {
        if (!inFlightRevalidations.add("asset:$assetUrl")) continue
        try {
          val key = sha256(assetUrl)
          val meta = readMeta(File(assetsDir, "$key.json"))
          val age = System.currentTimeMillis() - (meta?.optLong("checkedAt", 0L) ?: 0L)
          if (meta == null || age > ASSET_FRESH_WINDOW_MS) {
            serveOrRevalidateAsset(assetUrl, emptyMap(), isOnline = true)
          }
        } finally {
          inFlightRevalidations.remove("asset:$assetUrl")
        }
      }

      for (pageUrl in links.take(MAX_PREFETCH_PAGES)) {
        if (!inFlightRevalidations.add("page:$pageUrl")) continue
        try {
          val key = sha256(normalizePageUrl(pageUrl))
          val meta = readMeta(File(pagesDir, "$key.json"))
          val age = System.currentTimeMillis() - (meta?.optLong("checkedAt", 0L) ?: 0L)
          if (meta == null || age > PREFETCH_COOLDOWN_MS) {
            fetchAndStorePage(
              url = pageUrl,
              requestHeaders = emptyMap(),
              allowConditionalHeaders = true,
              updateLastActive = false,
            )
          }
        } finally {
          inFlightRevalidations.remove("page:$pageUrl")
        }
      }
    }
  }

  fun clearOfflinePages() {
    runCatching {
      pagesDir.listFiles()?.forEach { it.delete() }
      if (indexFile.exists()) indexFile.delete()
    }
  }

  private fun serveOrRevalidatePage(
    url: String,
    requestHeaders: Map<String, String>?,
    isOnline: Boolean,
  ): WebResourceResponse? {
    val cached = getCachedHtmlEntry(url)
    val forceRefresh = forceNextPageRefresh
    if (forceRefresh) {
      forceNextPageRefresh = false
    }

    if (!isOnline) {
      // Offline mode: serve exact cached page, or fallback to last active dashboard if ENTRY_URL
      if (cached != null) {
        return buildHtmlResponse(cached.first, cached.second)
      }
      if (normalizePageUrl(url) == normalizePageUrl(WebViewUrlPolicy.ENTRY_URL)) {
        val fallbackUrl = getBestOfflineStartUrl()
        if (fallbackUrl != url) {
          getCachedHtmlEntry(fallbackUrl)?.let { (bytes, meta) ->
            return buildHtmlResponse(bytes, meta)
          }
        }
      }
      return null
    }

    val now = System.currentTimeMillis()
    val isMutationBypass = now < bypassFreshCacheUntilMs

    // Fast path: already loaded page within fresh window -> serve 0ms from disk & revalidate async
    if (!forceRefresh && !isMutationBypass && cached != null) {
      val checkedAt = cached.second.optLong("checkedAt", 0L)
      if ((now - checkedAt) < PAGE_FRESH_WINDOW_MS) {
        revalidatePageInBackground(url, requestHeaders)
        return buildHtmlResponse(cached.first, cached.second)
      }
    }

    // Online fetch with ETag/Last-Modified conditional headers & full-source caching
    return when (
      val result =
        fetchAndStorePage(
          url = url,
          requestHeaders = requestHeaders,
          allowConditionalHeaders = !forceRefresh && !isMutationBypass,
          updateLastActive = true,
        )
    ) {
      is PageFetchResult.Served -> result.response
      PageFetchResult.Redirect -> null // Let WebView process 301/302 natively
      PageFetchResult.NetworkFailure -> {
        cached?.let { buildHtmlResponse(it.first, it.second) }
      }
    }
  }

  private fun revalidatePageInBackground(
    url: String,
    requestHeaders: Map<String, String>?,
  ) {
    val dedupKey = "bg:${normalizePageUrl(url)}"
    if (!inFlightRevalidations.add(dedupKey)) return
    bgExecutor.execute {
      try {
        fetchAndStorePage(
          url = url,
          requestHeaders = requestHeaders,
          allowConditionalHeaders = true,
          updateLastActive = false,
        )
      } finally {
        inFlightRevalidations.remove(dedupKey)
      }
    }
  }

  private sealed interface PageFetchResult {
    data class Served(val response: WebResourceResponse) : PageFetchResult

    data object Redirect : PageFetchResult

    data object NetworkFailure : PageFetchResult
  }

  private fun fetchAndStorePage(
    url: String,
    requestHeaders: Map<String, String>?,
    allowConditionalHeaders: Boolean,
    updateLastActive: Boolean,
  ): PageFetchResult {
    val normalized = normalizePageUrl(url)
    val key = sha256(normalized)
    val bodyFile = File(pagesDir, "$key.html")
    val metaFile = File(pagesDir, "$key.json")
    val cachedMeta = readMeta(metaFile)
    val now = System.currentTimeMillis()

    var connection: HttpsURLConnection? = null
    return try {
      connection = (URL(url).openConnection() as? HttpsURLConnection) ?: return PageFetchResult.NetworkFailure
      connection.instanceFollowRedirects = false
      connection.connectTimeout = PAGE_TIMEOUT_MS
      connection.readTimeout = PAGE_TIMEOUT_MS
      connection.requestMethod = "GET"

      requestHeaders?.get("User-Agent")?.let { connection.setRequestProperty("User-Agent", it) }
      requestHeaders?.get("Accept")?.let { connection.setRequestProperty("Accept", it) }
      runCatching { CookieManager.getInstance().getCookie(url) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let { connection.setRequestProperty("Cookie", it) }

      if (allowConditionalHeaders && cachedMeta != null && bodyFile.exists()) {
        cachedMeta.optString("etag").takeIf { it.isNotBlank() }?.let {
          connection.setRequestProperty("If-None-Match", it)
        }
        cachedMeta.optString("lastModified").takeIf { it.isNotBlank() }?.let {
          connection.setRequestProperty("If-Modified-Since", it)
        }
      }

      val statusCode = connection.responseCode
      forwardSetCookies(url, connection)

      if (statusCode in 300..399) {
        return PageFetchResult.Redirect
      }

      if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED && cachedMeta != null && bodyFile.exists()) {
        cachedMeta.put("checkedAt", now)
        metaFile.writeText(cachedMeta.toString(), Charsets.UTF_8)
        bodyFile.setLastModified(now)
        if (updateLastActive) saveLastActiveUrl(url)
        return PageFetchResult.Served(buildHtmlResponse(bodyFile.readBytes(), cachedMeta))
      }

      if (statusCode >= 400) {
        // Return clean HTTP error response with empty body so WebView triggers onReceivedHttpError
        // and native overlay immediately blocks it without rendering server error HTML.
        return PageFetchResult.Served(
          WebResourceResponse(
            "text/html",
            "UTF-8",
            statusCode,
            "Blocked Error Response",
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
          )
        )
      }

      if (statusCode in 200..299) {
        val rawContentType = connection.contentType ?: "text/html; charset=UTF-8"
        val mimeType = rawContentType.substringBefore(";").trim().ifEmpty { "text/html" }
        if (!mimeType.contains("html", ignoreCase = true)) {
          return PageFetchResult.Redirect // Non-HTML file download/stream -> let WebView handle
        }

        val encoding = extractCharset(rawContentType)
        val bytes = connection.inputStream.use { it.readBytes() }
        val textPreview = runCatching { String(bytes, charset(encoding)) }.getOrElse { "" }
        val (title, snippet, textLen) = extractRoughTitleAndBody(textPreview)

        if (WebPageErrorDetector.detectError(title, snippet, textLen) != null) {
          return PageFetchResult.Served(
            WebResourceResponse(
              "text/html",
              "UTF-8",
              500,
              "Blocked Error Content",
              emptyMap(),
              ByteArrayInputStream(ByteArray(0)),
            )
          )
        }

        val etag = connection.getHeaderField("ETag").orEmpty()
        val lastModified = connection.getHeaderField("Last-Modified").orEmpty()
        if (bytes.size <= MAX_SINGLE_HTML_BYTES) {
          persistPageBytes(
            url = url,
            bytes = bytes,
            encoding = encoding,
            etag = etag,
            lastModified = lastModified,
          )
          if (updateLastActive) saveLastActiveUrl(url)
        }

        val meta =
          JSONObject()
            .put("encoding", encoding)
            .put("checkedAt", now)
        return PageFetchResult.Served(buildHtmlResponse(bytes, meta))
      }

      PageFetchResult.NetworkFailure
    } catch (_: Exception) {
      PageFetchResult.NetworkFailure
    } finally {
      connection?.disconnect()
    }
  }

  private fun forwardSetCookies(url: String, connection: HttpsURLConnection) {
    runCatching {
      val cm = CookieManager.getInstance()
      for ((headerKey, values) in connection.headerFields) {
        if (headerKey.equals("Set-Cookie", ignoreCase = true)) {
          for (cookieValue in values) {
            if (!cookieValue.isNullOrBlank()) {
              cm.setCookie(url, cookieValue)
            }
          }
        }
      }
      cm.flush()
    }
  }

  private fun persistPageBytes(
    url: String,
    bytes: ByteArray,
    encoding: String,
    etag: String,
    lastModified: String,
  ) {
    runCatching {
      val normalized = normalizePageUrl(url)
      val key = sha256(normalized)
      val dataFile = File(pagesDir, "$key.html")
      val metaFile = File(pagesDir, "$key.json")
      val newHash = sha256Bytes(bytes)
      val oldMeta = readMeta(metaFile)
      val now = System.currentTimeMillis()

      if (oldMeta == null || oldMeta.optString("sha256") != newHash || !dataFile.exists()) {
        dataFile.writeBytes(bytes)
      } else {
        dataFile.setLastModified(now)
      }

      val meta =
        JSONObject()
          .put("url", url)
          .put("encoding", encoding)
          .put("etag", etag)
          .put("lastModified", lastModified)
          .put("sha256", newHash)
          .put("checkedAt", now)
      metaFile.writeText(meta.toString(), Charsets.UTF_8)

      saveLastActiveUrl(url)
      pruneDirectoryIfNeeded(pagesDir, MAX_HTML_CACHE_BYTES)
    }.onFailure {
      SafeLogger.w("Failed storing offline HTML page")
    }
  }

  private fun getCachedHtmlEntry(url: String): Pair<ByteArray, JSONObject>? {
    return runCatching {
      val key = sha256(normalizePageUrl(url))
      val dataFile = File(pagesDir, "$key.html")
      val metaFile = File(pagesDir, "$key.json")
      if (!dataFile.exists() || dataFile.length() == 0L) return null
      val bytes = dataFile.readBytes()
      val meta = readMeta(metaFile) ?: JSONObject().put("encoding", "UTF-8")
      bytes to meta
    }.getOrNull()
  }

  private fun buildHtmlResponse(bytes: ByteArray, meta: JSONObject): WebResourceResponse {
    val encoding = meta.optString("encoding", "UTF-8").ifBlank { "UTF-8" }
    return WebResourceResponse(
      "text/html",
      encoding,
      200,
      "OK",
      mapOf("Cache-Control" to "private, max-age=30"),
      ByteArrayInputStream(bytes),
    )
  }

  private fun serveOrRevalidateAsset(
    url: String,
    requestHeaders: Map<String, String>?,
    isOnline: Boolean,
  ): WebResourceResponse? {
    val key = sha256(url)
    val bodyFile = File(assetsDir, "$key.bin")
    val metaFile = File(assetsDir, "$key.json")
    val cachedMeta = readMeta(metaFile)
    val now = System.currentTimeMillis()

    if (bodyFile.exists() && cachedMeta != null) {
      val checkedAt = cachedMeta.optLong("checkedAt", 0L)
      if (!isOnline || (now - checkedAt) < ASSET_FRESH_WINDOW_MS) {
        return buildAssetResponse(bodyFile.readBytes(), cachedMeta)
      }
    }

    if (!isOnline) {
      return null
    }

    var connection: HttpsURLConnection? = null
    return try {
      connection = (URL(url).openConnection() as? HttpsURLConnection) ?: return null
      connection.instanceFollowRedirects = true
      connection.connectTimeout = ASSET_TIMEOUT_MS
      connection.readTimeout = ASSET_TIMEOUT_MS
      connection.requestMethod = "GET"

      requestHeaders?.get("User-Agent")?.let { connection.setRequestProperty("User-Agent", it) }
      requestHeaders?.get("Accept")?.let { connection.setRequestProperty("Accept", it) }
      if (WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
        runCatching { CookieManager.getInstance().getCookie(url) }
          .getOrNull()
          ?.let { connection.setRequestProperty("Cookie", it) }
      }

      if (cachedMeta != null && bodyFile.exists()) {
        cachedMeta.optString("etag").takeIf { it.isNotBlank() }?.let {
          connection.setRequestProperty("If-None-Match", it)
        }
        cachedMeta.optString("lastModified").takeIf { it.isNotBlank() }?.let {
          connection.setRequestProperty("If-Modified-Since", it)
        }
      }

      val statusCode = connection.responseCode
      if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED && cachedMeta != null && bodyFile.exists()) {
        cachedMeta.put("checkedAt", now)
        metaFile.writeText(cachedMeta.toString(), Charsets.UTF_8)
        bodyFile.setLastModified(now)
        return buildAssetResponse(bodyFile.readBytes(), cachedMeta)
      }

      if (statusCode in 200..299) {
        val bytes = connection.inputStream.use { it.readBytes() }
        if (bytes.size <= MAX_SINGLE_ASSET_BYTES) {
          val rawContentType = connection.contentType ?: guessMimeType(url)
          val mimeType = rawContentType.substringBefore(";").trim().ifEmpty { guessMimeType(url) }
          val encoding = extractCharset(rawContentType)
          val etag = connection.getHeaderField("ETag").orEmpty()
          val lastModified = connection.getHeaderField("Last-Modified").orEmpty()
          val acao = connection.getHeaderField("Access-Control-Allow-Origin").orEmpty()

          val newMeta =
            JSONObject()
              .put("mimeType", mimeType)
              .put("encoding", encoding)
              .put("etag", etag)
              .put("lastModified", lastModified)
              .put("acao", acao)
              .put("checkedAt", now)
          bodyFile.writeBytes(bytes)
          metaFile.writeText(newMeta.toString(), Charsets.UTF_8)
          pruneDirectoryIfNeeded(assetsDir, MAX_ASSET_CACHE_BYTES)
          return buildAssetResponse(bytes, newMeta)
        }
        return null
      }

      if (cachedMeta != null && bodyFile.exists()) {
        buildAssetResponse(bodyFile.readBytes(), cachedMeta)
      } else {
        null
      }
    } catch (_: Exception) {
      if (cachedMeta != null && bodyFile.exists()) {
        runCatching { buildAssetResponse(bodyFile.readBytes(), cachedMeta) }.getOrNull()
      } else {
        null
      }
    } finally {
      connection?.disconnect()
    }
  }

  private fun buildAssetResponse(bytes: ByteArray, meta: JSONObject): WebResourceResponse {
    val mimeType = meta.optString("mimeType", "application/octet-stream")
    val encoding = meta.optString("encoding", "UTF-8")
    val headers = mutableMapOf<String, String>()
    meta.optString("acao").takeIf { it.isNotBlank() }?.let {
      headers["Access-Control-Allow-Origin"] = it
    }
    return WebResourceResponse(
      mimeType,
      encoding,
      200,
      "OK",
      headers,
      ByteArrayInputStream(bytes),
    )
  }

  private fun saveLastActiveUrl(url: String) {
    if (!WebViewUrlPolicy.isAllowedNavigationUrl(url) || isLogoutUrl(url)) return
    runCatching {
      val json = readMeta(indexFile) ?: JSONObject()
      json.put("lastActiveUrl", url)
      indexFile.writeText(json.toString(), Charsets.UTF_8)
    }
  }

  private fun readLastActiveUrl(): String? =
    readMeta(indexFile)?.optString("lastActiveUrl")?.takeIf { it.isNotBlank() }

  private fun readMeta(file: File): JSONObject? {
    if (!file.exists()) return null
    return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
  }

  private fun pruneDirectoryIfNeeded(dir: File, maxBytes: Long) {
    val files = dir.listFiles()?.toList() ?: return
    var totalSize = files.sumOf { it.length() }
    if (totalSize <= maxBytes) return

    val sorted = files.sortedBy { it.lastModified() }
    for (file in sorted) {
      if (totalSize <= (maxBytes * 0.8).toLong()) break
      val len = file.length()
      if (file.delete()) {
        totalSize -= len
      }
    }
  }

  companion object {
    private const val PAGE_FRESH_WINDOW_MS = 45_000L // 45s instant disk load + async background check
    private const val PREFETCH_COOLDOWN_MS = 180_000L // 3 min prefetch cooldown
    private const val MUTATION_BYPASS_WINDOW_MS = 8_000L
    private const val ASSET_FRESH_WINDOW_MS = 300_000L // 5 min instant asset cache
    private const val PAGE_TIMEOUT_MS = 7_000
    private const val ASSET_TIMEOUT_MS = 5_000
    private const val MAX_SINGLE_HTML_BYTES = 6 * 1024 * 1024 // 6 MB per single-file page
    private const val MAX_SINGLE_ASSET_BYTES = 4 * 1024 * 1024 // 4 MB
    private const val MAX_HTML_CACHE_BYTES = 60L * 1024 * 1024 // 60 MB offline pages vault
    private const val MAX_ASSET_CACHE_BYTES = 30L * 1024 * 1024 // 30 MB static assets vault
    private const val MAX_PREFETCH_PAGES = 12
    private const val MAX_PREFETCH_ASSETS = 24

    private val HREF_REGEX = Regex("""<a\s[^>]*href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ASSET_ATTR_REGEX =
      Regex("""<(?:link|script|img)\s[^>]*(?:href|src)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val TITLE_REGEX = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val SCRIPT_STYLE_REGEX =
      Regex("""<(?:script|style)[^>]*>.*?</(?:script|style)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG_REGEX = Regex("""<[^>]+>""")

    private val STATIC_EXTENSIONS =
      setOf(
        "css",
        "js",
        "mjs",
        "png",
        "jpg",
        "jpeg",
        "webp",
        "gif",
        "svg",
        "ico",
        "woff",
        "woff2",
        "ttf",
        "otf",
      )

    fun isCacheableStaticAsset(rawUrl: String): Boolean {
      val path = rawUrl.substringBefore("?").substringBefore("#").lowercase(Locale.ROOT)
      val ext = path.substringAfterLast('.', "")
      return ext in STATIC_EXTENSIONS
    }

    fun normalizePageUrl(rawUrl: String): String = rawUrl.substringBefore("#").trim()

    fun isLogoutUrl(url: String): Boolean {
      val lower = url.lowercase(Locale.ROOT)
      return lower.contains("logout") || lower.contains("keluar") || lower.contains("signout")
    }

    fun extractSafeLinkedUrls(baseUrl: String, html: String): List<String> {
      val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return emptyList()
      val seen = LinkedHashSet<String>()
      for (match in HREF_REGEX.findAll(html)) {
        val rawHref = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (rawHref.isEmpty() || rawHref.startsWith("#")) continue
        val lower = rawHref.lowercase(Locale.ROOT)
        if (
          lower.startsWith("javascript:") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("tel:")
        ) {
          continue
        }
        val resolved =
          runCatching { baseUri.resolve(rawHref).toString() }.getOrNull()?.let { normalizePageUrl(it) }
            ?: continue
        if (!WebViewUrlPolicy.isAllowedNavigationUrl(resolved)) continue
        val resolvedLower = resolved.lowercase(Locale.ROOT)
        if (!resolvedLower.contains(WebViewUrlPolicy.SUPPORT_PATH_PREFIX)) continue
        if (
          isLogoutUrl(resolvedLower) ||
            resolvedLower.contains("delete") ||
            resolvedLower.contains("hapus") ||
            resolvedLower.contains("remove") ||
            resolvedLower.contains("action=") ||
            resolvedLower.contains("token=")
        ) {
          continue
        }
        if (resolved != normalizePageUrl(baseUrl)) {
          seen.add(resolved)
        }
      }
      return seen.toList()
    }

    private fun extractStaticAssetUrls(baseUrl: String, html: String): List<String> {
      val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return emptyList()
      val seen = LinkedHashSet<String>()
      for (match in ASSET_ATTR_REGEX.findAll(html)) {
        val raw = match.groupValues.getOrNull(1)?.trim().orEmpty()
        if (raw.isEmpty() || raw.startsWith("data:") || raw.startsWith("blob:")) continue
        val resolved = runCatching { baseUri.resolve(raw).toString() }.getOrNull() ?: continue
        if (
          WebViewUrlPolicy.isAllowedSubresourceUrl(resolved) &&
            resolved.startsWith("https://", ignoreCase = true) &&
            isCacheableStaticAsset(resolved)
        ) {
          seen.add(resolved)
        }
      }
      return seen.toList()
    }

    internal fun extractRoughTitleAndBody(html: String): Triple<String, String, Int> {
      val title = TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
      val stripped =
        html
          .replace(SCRIPT_STYLE_REGEX, " ")
          .replace(TAG_REGEX, " ")
          .replace(Regex("""\s+"""), " ")
          .trim()
      return Triple(title, stripped.take(750), stripped.length)
    }

    private fun extractCharset(rawContentType: String): String =
      rawContentType
        .split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter("=")
        ?.trim()
        ?.ifBlank { "UTF-8" }
        ?: "UTF-8"

    private fun sha256(input: String): String = sha256Bytes(input.toByteArray(Charsets.UTF_8))

    private fun sha256Bytes(bytes: ByteArray): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
      return digest.joinToString("") { "%02x".format(it) }
    }

    private fun guessMimeType(url: String): String {
      val ext =
        url.substringBefore("?").substringBefore("#").substringAfterLast('.', "").lowercase(Locale.ROOT)
      return when (ext) {
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        else -> "application/octet-stream"
      }
    }
  }
}
