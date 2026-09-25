package com.hassangarage.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var nativeSync: NativeSyncBridge
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            callback.onReceiveValue(uris)
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.javaScriptCanOpenWindowsAutomatically = true

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    if (fileChooserParams == null) {
                        this@MainActivity.filePathCallback = null
                        return false
                    }
                    return try {
                        fileChooserLauncher.launch(fileChooserParams.createIntent())
                        true
                    } catch (_: Exception) {
                        this@MainActivity.filePathCallback = null
                        false
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    return openExternalIfNeeded(uri)
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    val uri = url?.let(Uri::parse) ?: return false
                    return openExternalIfNeeded(uri)
                }
            }
        }

        nativeSync = NativeSyncBridge(this, webView)
        webView.addJavascriptInterface(nativeSync, "AndroidSync")
        webView.loadUrl("file:///android_asset/index.html")

        val root = FrameLayout(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.setOnApplyWindowInsetsListener { _, insets ->
            val lp = webView.layoutParams as FrameLayout.LayoutParams
            lp.bottomMargin = insets.systemWindowInsetBottom
            webView.layoutParams = lp
            insets
        }
        setContentView(root)
        root.requestApplyInsets()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun openExternalIfNeeded(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme == "file" || scheme == "about" || scheme == "data" || scheme == "blob") {
            return false
        }

        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        if (::nativeSync.isInitialized) nativeSync.shutdown()
        webView.destroy()
        super.onDestroy()
    }
}

private class NativeSyncBridge(
    private val context: Context,
    private val webView: WebView
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val syncing = AtomicBoolean(false)
    @Volatile private var pendingJson: String? = null
    @Volatile private var idToken: String? = null

    private val prefs = context.getSharedPreferences("hassan_garage_sync", Context.MODE_PRIVATE)
    private val apiKey = "AIzaSyDI6EzDNhW4T4pDNTcRqRg5dO-xR6G93aM"
    private val databaseUrl = "https://hassan-garage-online-default-rtdb.europe-west1.firebasedatabase.app"
    private val statePath = "hassan-garage/v4_1/state"
    private val backupPath = "hassan-garage/v4_1/backup_last"
    private val collections = listOf("jobs", "stock", "sales", "bookings", "codes", "audit", "users")

    @JavascriptInterface
    fun getDeviceId(): String {
        var id = prefs.getString("device_id", null)
        if (id.isNullOrBlank()) {
            id = "android-" + System.currentTimeMillis().toString(36) + "-" +
                java.lang.Long.toString(java.lang.Double.doubleToLongBits(Math.random()), 36).takeLast(8)
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    @JavascriptInterface
    fun getLocalBackup(): String {
        return try {
            val current = File(context.filesDir, "garage_backup_current.json")
            if (current.exists()) current.readText() else ""
        } catch (_: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun saveLocalBackup(json: String) {
        saveBackupFiles(json)
    }

    @JavascriptInterface
    fun syncState(json: String) {
        saveBackupFiles(json)
        pendingJson = json
        startSyncLoop()
    }

    @JavascriptInterface
    fun pullState() {
        executor.execute {
            try {
                val remote = getRemoteState()
                if (remote != null) callback("hgNativeRemote", remote.toString())
            } catch (_: Exception) {
                // Keep working offline. Next poll/save will retry.
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun saveBackupFiles(json: String) {
        try {
            synchronized(this) {
                val current = File(context.filesDir, "garage_backup_current.json")
                val previous = File(context.filesDir, "garage_backup_previous.json")
                val temp = File(context.filesDir, "garage_backup_temp.json")
                temp.writeText(json)
                if (previous.exists()) previous.delete()
                if (current.exists()) current.copyTo(previous, overwrite = true)
                temp.copyTo(current, overwrite = true)
                temp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun startSyncLoop() {
        if (!syncing.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (true) {
                    val local = pendingJson ?: break
                    pendingJson = null
                    try {
                        val merged = syncOnce(local)
                        saveBackupFiles(merged.toString())
                        callback("hgNativeSynced", merged.toString())
                    } catch (_: Exception) {
                        // Keep the newest unsent copy queued for a later save/poll cycle.
                        if (pendingJson == null) pendingJson = local
                        break
                    }
                }
            } finally {
                syncing.set(false)
                if (pendingJson != null) startSyncLoop()
            }
        }
    }

    private fun syncOnce(localJson: String): JSONObject {
        val local = JSONObject(localJson)
        repeat(4) {
            val (remote, etag) = getRemoteStateWithEtag()
            val merged = mergeStates(remote, local)

            if (remote != null) {
                try {
                    putJson(backupPath, JSONObject()
                        .put("savedAt", System.currentTimeMillis())
                        .put("deviceId", getDeviceId())
                        .put("state", remote), null)
                } catch (_: Exception) {
                }
            }

            val result = putJson(statePath, merged, etag)
            if (result == 200 || result == 204) return merged
            if (result != 412) throw IllegalStateException("Firebase save failed: $result")
        }
        throw IllegalStateException("Firebase save conflict")
    }

    private fun mergeStates(remote: JSONObject?, local: JSONObject): JSONObject {
        if (remote == null) return JSONObject(local.toString())

        val out = JSONObject()
        val tombstones = mergeTombstones(
            remote.optJSONObject("_tombstones"),
            local.optJSONObject("_tombstones")
        )
        out.put("_tombstones", tombstones)

        for (collection in collections) {
            val r = remote.optJSONArray(collection) ?: JSONArray()
            val l = local.optJSONArray(collection) ?: JSONArray()
            out.put(collection, mergeArray(collection, r, l, tombstones.optJSONObject(collection)))
        }
        return out
    }

    private fun mergeTombstones(remote: JSONObject?, local: JSONObject?): JSONObject {
        val out = JSONObject()
        val cols = linkedSetOf<String>()
        if (remote != null) cols.addAll(remote.keys().asSequence().toList())
        if (local != null) cols.addAll(local.keys().asSequence().toList())

        for (col in cols) {
            val ro = remote?.optJSONObject(col)
            val lo = local?.optJSONObject(col)
            val merged = JSONObject()
            val ids = linkedSetOf<String>()
            if (ro != null) ids.addAll(ro.keys().asSequence().toList())
            if (lo != null) ids.addAll(lo.keys().asSequence().toList())
            for (id in ids) {
                val ts = maxOf(ro?.optLong(id, 0L) ?: 0L, lo?.optLong(id, 0L) ?: 0L)
                if (ts > 0) merged.put(id, ts)
            }
            out.put(col, merged)
        }
        return out
    }

    private fun mergeArray(
        collection: String,
        remote: JSONArray,
        local: JSONArray,
        tombstones: JSONObject?
    ): JSONArray {
        val map = LinkedHashMap<String, JSONObject>()

        fun take(obj: JSONObject, preferOnTie: Boolean) {
            val id = itemId(obj)
            val existing = map[id]
            if (existing == null) {
                map[id] = JSONObject(obj.toString())
                return
            }
            val a = existing.optLong("_updatedAt", 0L)
            val b = obj.optLong("_updatedAt", 0L)
            val choose = when {
                b > a -> true
                b < a -> false
                collection == "jobs" -> jobStatusRank(obj.optString("status")) >
                    jobStatusRank(existing.optString("status"))
                else -> preferOnTie
            }
            if (choose) map[id] = JSONObject(obj.toString())
        }

        for (i in 0 until remote.length()) remote.optJSONObject(i)?.let { take(it, false) }
        for (i in 0 until local.length()) local.optJSONObject(i)?.let { take(it, true) }

        val out = JSONArray()
        for ((id, obj) in map) {
            val deletedAt = tombstones?.optLong(id, 0L) ?: 0L
            val updatedAt = obj.optLong("_updatedAt", 0L)
            if (deletedAt <= updatedAt) out.put(obj)
        }
        return out
    }

    private fun itemId(obj: JSONObject): String {
        val id = obj.opt("id")
        return if (id == null || id == JSONObject.NULL) obj.toString() else id.toString()
    }

    private fun jobStatusRank(status: String): Int = when (status) {
        "delivered" -> 3
        "ready" -> 2
        else -> 1
    }

    private fun getRemoteState(): JSONObject? = getRemoteStateWithEtag().first

    private fun getRemoteStateWithEtag(): Pair<JSONObject?, String?> {
        val token = ensureToken()
        val url = URL("$databaseUrl/$statePath.json?auth=" +
            URLEncoder.encode(token, StandardCharsets.UTF_8.name()))
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("X-Firebase-ETag", "true")
        }
        val code = c.responseCode
        if (code == 401) {
            idToken = null
            return getRemoteStateWithEtag()
        }
        if (code !in 200..299) throw IllegalStateException("Firebase read failed: $code")
        val body = c.inputStream.bufferedReader().use { it.readText() }
        val etag = c.getHeaderField("ETag")
        val obj = if (body.isBlank() || body == "null") null else JSONObject(body)
        c.disconnect()
        return obj to etag
    }

    private fun putJson(path: String, json: JSONObject, etag: String?): Int {
        val token = ensureToken()
        val url = URL("$databaseUrl/$path.json?auth=" +
            URLEncoder.encode(token, StandardCharsets.UTF_8.name()))
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            if (!etag.isNullOrBlank()) setRequestProperty("if-match", etag)
        }
        c.outputStream.use { it.write(json.toString().toByteArray(StandardCharsets.UTF_8)) }
        val code = c.responseCode
        if (code == 401) {
            idToken = null
            c.disconnect()
            return putJson(path, json, etag)
        }
        try {
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            stream?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
        }
        c.disconnect()
        return code
    }

    @Synchronized
    private fun ensureToken(): String {
        idToken?.let { return it }

        val refresh = prefs.getString("refresh_token", null)
        if (!refresh.isNullOrBlank()) {
            try {
                val body = "grant_type=refresh_token&refresh_token=" +
                    URLEncoder.encode(refresh, StandardCharsets.UTF_8.name())
                val result = post(
                    "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                    body,
                    "application/x-www-form-urlencoded"
                )
                val obj = JSONObject(result)
                val token = obj.getString("id_token")
                val newRefresh = obj.optString("refresh_token", refresh)
                prefs.edit().putString("refresh_token", newRefresh).apply()
                idToken = token
                return token
            } catch (_: Exception) {
                prefs.edit().remove("refresh_token").apply()
            }
        }

        val result = post(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey",
            """{"returnSecureToken":true}""",
            "application/json; charset=UTF-8"
        )
        val obj = JSONObject(result)
        val token = obj.getString("idToken")
        prefs.edit().putString("refresh_token", obj.getString("refreshToken")).apply()
        idToken = token
        return token
    }

    private fun post(urlText: String, body: String, contentType: String): String {
        val c = (URL(urlText).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Content-Type", contentType)
        }
        c.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (code !in 200..299) throw IllegalStateException("Auth failed: $code $text")
        return text
    }

    private fun callback(name: String, json: String) {
        val script = "window.$name && window.$name(" + JSONObject.quote(json) + ");"
        webView.post { webView.evaluateJavascript(script, null) }
    }
}
