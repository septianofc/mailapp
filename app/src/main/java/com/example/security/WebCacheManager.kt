package com.example.security

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/**
 * Private on-device disk cache for static web assets (with ETag/Last-Modified revalidation)
 * and offline HTML snapshots of verified visited pages.
 *
 * Security guarantees:
 * - Only caches HTTPS GET requests permitted by WebViewUrlPolicy.
 * - Uses standard system TLS/Hostname verification (never trusts invalid certificates).
 * - Stores files strictly in app-private cacheDir (not world-readable; allowBackup=false).
 */
class WebCacheManager(context: Context) {

  private val cacheRootDir = File(context.applicationContext.cacheDir, "secure_web_cache").apply {
    mkdirs()
  }
  private val assetsDir = File(cacheRootDir, "assets").apply { mkdirs() }
  private val htmlDir = File(cacheRootDir, "pages").apply { mkdirs() }

  fun interceptGetRequest(
    request: WebResourceRequest,
    isOnline: Boolean,
  ): WebResourceResponse? {
    val url = request.url?.toString() ?: return null
    if (!request.method.equals("GET", ignoreCase = true)) return null
    if (!WebViewUrlPolicy.isAllowedSubresourceUrl(url)) return null
    if (!url.startsWith("https://", ignoreCase = true)) return null

    // 1. Offline HTML fallback for previously loaded & verified pages on septianfr.web.id
    if (request.isForMainFrame) {
      if (!isOnline && WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
        return getCachedHtmlResponse(url)
      }
      return null
    }

    // 2. Static assets cache (CSS, JS, images, fonts, icons)
    if (isCacheableStaticAsset(url)) {
      return serveOrRevalidateAsset(url, request.requestHeaders, isOnline)
    }

    return null
  }

  fun saveHtmlSnapshot(url: String, html: String) {
    if (!WebViewUrlPolicy.isAllowedNavigationUrl(url)) return
    if (url.contains("logout", ignoreCase = true)) return
    if (html.isBlank() || html.length > MAX_HTML_SNAPSHOT_BYTES) return

    runCatching {
      val key = sha256(normalizePageUrl(url))
      val dataFile = File(htmlDir, "$key.html")
      val metaFile = File(htmlDir, "$key.json")
      dataFile.writeText(html, Charsets.UTF_8)
      val meta =
        JSONObject()
          .put("url", url)
          .put("savedAt", System.currentTimeMillis())
      metaFile.writeText(meta.toString(), Charsets.UTF_8)
      pruneDirectoryIfNeeded(htmlDir, MAX_HTML_CACHE_BYTES)
    }.onFailure {
      SafeLogger.w("Failed saving offline HTML snapshot")
    }
  }

  fun clearHtmlSnapshots() {
    runCatching {
      htmlDir.listFiles()?.forEach { it.delete() }
    }
  }

  private fun getCachedHtmlResponse(url: String): WebResourceResponse? {
    return runCatching {
      val key = sha256(normalizePageUrl(url))
      val dataFile = File(htmlDir, "$key.html")
      if (!dataFile.exists() || dataFile.length() == 0L) return null
      val bytes = dataFile.readBytes()
      dataFile.setLastModified(System.currentTimeMillis())
      WebResourceResponse(
        "text/html",
        "UTF-8",
        200,
        "OK",
        mapOf("Cache-Control" to "private, max-age=60"),
        ByteArrayInputStream(bytes),
      )
    }.getOrNull()
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

    // Fast path: offline or recently validated within fresh window -> serve straight from disk (0ms)
    if (bodyFile.exists() && cachedMeta != null) {
      val checkedAt = cachedMeta.optLong("checkedAt", 0L)
      if (!isOnline || (now - checkedAt) < ASSET_FRESH_WINDOW_MS) {
        return buildAssetResponse(bodyFile.readBytes(), cachedMeta)
      }
    }

    if (!isOnline) {
      return null
    }

    // Conditional revalidation / download over strict HTTPS
    var connection: HttpsURLConnection? = null
    return try {
      connection = (URL(url).openConnection() as? HttpsURLConnection) ?: return null
      connection.instanceFollowRedirects = true
      connection.connectTimeout = ASSET_TIMEOUT_MS
      connection.readTimeout = ASSET_TIMEOUT_MS
      connection.requestMethod = "GET"

      requestHeaders?.get("User-Agent")?.let { ua ->
        connection.setRequestProperty("User-Agent", ua)
      }
      requestHeaders?.get("Accept")?.let { accept ->
        connection.setRequestProperty("Accept", accept)
      }
      if (WebViewUrlPolicy.isAllowedNavigationUrl(url)) {
        CookieManager.getInstance().getCookie(url)?.let { cookie ->
          connection.setRequestProperty("Cookie", cookie)
        }
      }

      if (cachedMeta != null && bodyFile.exists()) {
        cachedMeta.optString("etag").takeIf { it.isNotBlank() }?.let { etag ->
          connection.setRequestProperty("If-None-Match", etag)
        }
        cachedMeta.optString("lastModified").takeIf { it.isNotBlank() }?.let { lm ->
          connection.setRequestProperty("If-Modified-Since", lm)
        }
      }

      val statusCode = connection.responseCode
      if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED && cachedMeta != null && bodyFile.exists()) {
        // 304 Not Modified: content unchanged -> update freshness timestamp and serve disk copy
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
          val encoding =
            rawContentType
              .split(";")
              .map { it.trim() }
              .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
              ?.substringAfter("=")
              ?.trim()
              ?: "UTF-8"
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

      // Server returned error for asset: fall back to stale cache if available
      if (cachedMeta != null && bodyFile.exists()) {
        buildAssetResponse(bodyFile.readBytes(), cachedMeta)
      } else {
        null
      }
    } catch (_: Exception) {
      // Network hiccup: serve cached asset if present rather than letting page look broken
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
    meta.optString("acao").takeIf { it.isNotBlank() }?.let { acao ->
      headers["Access-Control-Allow-Origin"] = acao
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
    private const val ASSET_FRESH_WINDOW_MS = 120_000L // 2 minutes instant disk hit before revalidating
    private const val ASSET_TIMEOUT_MS = 5_000
    private const val MAX_SINGLE_ASSET_BYTES = 4 * 1024 * 1024 // 4 MB
    private const val MAX_HTML_SNAPSHOT_BYTES = 1024 * 1024 // 1 MB
    private const val MAX_ASSET_CACHE_BYTES = 30L * 1024 * 1024 // 30 MB
    private const val MAX_HTML_CACHE_BYTES = 10L * 1024 * 1024 // 10 MB

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

    private fun sha256(input: String): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
      return digest.joinToString("") { "%02x".format(it) }
    }
  }
}
