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
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
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
    private val prefs = context.getSharedPreferences("hassan_garage_sync", Context.MODE_PRIVATE)

    private val apiKey = "AIzaSyDI6EzDNhW4T4pDNTcRqRg5dO-xR6G93aM"
    private val appId = "1:777824518461:web:965f805c1a897d09610554"
    private val projectId = "hassan-garage-online"
    private val databaseUrl =
        "https://hassan-garage-online-default-rtdb.europe-west1.firebasedatabase.app"

    private val statePath = "hassan-garage/v4_1/state_json"
    private val legacyPath = "hassan-garage/v4_1/state"

    private val collections =
        listOf("jobs", "stock", "sales", "bookings", "codes", "audit", "users")

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var stateRef: DatabaseReference
    private lateinit var legacyRef: DatabaseReference

    @Volatile private var ready = false
    @Volatile private var pendingJson: String? = null
    private val syncing = AtomicBoolean(false)
    private var listener: ValueEventListener? = null

    init {
        initializeFirebase()
    }

    @JavascriptInterface
    fun getDeviceId(): String {
        var id = prefs.getString("device_id", null)
        if (id.isNullOrBlank()) {
            id = "android-" + System.currentTimeMillis().toString(36) + "-" +
                java.lang.Long.toString(
                    java.lang.Double.doubleToLongBits(Math.random()), 36
                ).takeLast(8)
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
    fun saveLocalBackup(json: String): Boolean {
        return saveBackupFiles(json)
    }

    @JavascriptInterface
    fun getSyncStatus(): String {
        return prefs.getString("sync_status", "STARTING") ?: "STARTING"
    }

    @JavascriptInterface
    fun syncState(json: String) {
        saveBackupFiles(json)
        pendingJson = json
        startSyncLoop()
    }

    @JavascriptInterface
    fun pullState() {
        if (!ready) return
        stateRef.get()
            .addOnSuccessListener { snap ->
                val text = snap.getValue(String::class.java)
                if (!text.isNullOrBlank()) callback("hgNativeRemote", text)
            }
            .addOnFailureListener { e ->
                setStatus("PULL_ERROR: ${e.message ?: "unknown"}")
            }
    }

    fun shutdown() {
        listener?.let {
            if (::stateRef.isInitialized) stateRef.removeEventListener(it)
        }
        listener = null
    }

    private fun initializeFirebase() {
        try {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setProjectId(projectId)
                .setDatabaseUrl(databaseUrl)
                .build()

            val firebaseApp = FirebaseApp.getApps(context)
                .firstOrNull { it.name == FIREBASE_APP_NAME }
                ?: FirebaseApp.initializeApp(context, options, FIREBASE_APP_NAME)
                ?: throw IllegalStateException("Firebase initialization failed")

            auth = FirebaseAuth.getInstance(firebaseApp)
            database = FirebaseDatabase.getInstance(firebaseApp)

            try {
                database.setPersistenceCacheSizeBytes(50L * 1024L * 1024L)
                database.setPersistenceEnabled(true)
            } catch (_: Exception) {
            }

            stateRef = database.getReference(statePath)
            legacyRef = database.getReference(legacyPath)

            setStatus("AUTHENTICATING")
            auth.signInAnonymously()
                .addOnSuccessListener {
                    setStatus("AUTH_OK")
                    prepareCanonicalState()
                }
                .addOnFailureListener { e ->
                    ready = false
                    setStatus("AUTH_ERROR: ${e.message ?: "unknown"}")
                    callbackStatus()
                }
        } catch (e: Exception) {
            ready = false
            setStatus("INIT_ERROR: ${e.message ?: "unknown"}")
            callbackStatus()
        }
    }

    private fun prepareCanonicalState() {
        stateRef.get()
            .addOnSuccessListener { snap ->
                val existing = snap.getValue(String::class.java)
                if (!existing.isNullOrBlank()) {
                    startRealtimeListener()
                    return@addOnSuccessListener
                }

                legacyRef.get()
                    .addOnSuccessListener { legacy ->
                        val legacyJson = snapshotObjectToJson(legacy)
                        if (legacyJson != null) {
                            stateRef.setValue(legacyJson.toString())
                                .addOnCompleteListener {
                                    startRealtimeListener()
                                }
                        } else {
                            startRealtimeListener()
                        }
                    }
                    .addOnFailureListener {
                        startRealtimeListener()
                    }
            }
            .addOnFailureListener {
                startRealtimeListener()
            }
    }

    private fun startRealtimeListener() {
        if (ready) return
        ready = true
        setStatus("ONLINE")

        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val text = snapshot.getValue(String::class.java)
                if (!text.isNullOrBlank()) {
                    // JS merges this snapshot with the phone's current state first.
                    // Never save a raw remote snapshot over the newest local backup.
                    callback("hgNativeRemote", text)
                }
                setStatus("ONLINE")
            }

            override fun onCancelled(error: DatabaseError) {
                setStatus("LISTENER_ERROR: ${error.message}")
                callbackStatus()
            }
        }

        listener = l
        stateRef.addValueEventListener(l)
        startSyncLoop()
    }

    private fun startSyncLoop() {
        if (!ready) return
        if (!syncing.compareAndSet(false, true)) return
        runPendingTransaction()
    }

    private fun runPendingTransaction() {
        val localText = pendingJson
        if (localText.isNullOrBlank()) {
            syncing.set(false)
            if (!pendingJson.isNullOrBlank()) startSyncLoop()
            return
        }

        pendingJson = null
        val local = try {
            JSONObject(localText)
        } catch (_: Exception) {
            syncing.set(false)
            setStatus("LOCAL_JSON_ERROR")
            return
        }

        setStatus("SYNCING")

        stateRef.runTransaction(object : Transaction.Handler {
            private var mergedResult: JSONObject? = null

            override fun doTransaction(currentData: MutableData): Transaction.Result {
                return try {
                    val remoteText = currentData.getValue(String::class.java)
                    val remote =
                        if (remoteText.isNullOrBlank()) {
                            null
                        } else {
                            try { JSONObject(remoteText) } catch (_: Exception) { null }
                        }
                    val merged = mergeStates(remote, local)
                    mergedResult = merged
                    currentData.value = merged.toString()
                    Transaction.success(currentData)
                } catch (_: Exception) {
                    Transaction.abort()
                }
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null || !committed) {
                    if (pendingJson == null) pendingJson = localText
                    syncing.set(false)
                    setStatus("SYNC_ERROR: ${error?.message ?: "transaction aborted"}")
                    callbackStatus()
                    webView.postDelayed({
                        if (!pendingJson.isNullOrBlank()) startSyncLoop()
                    }, 5000L)
                    return
                }

                val finalText =
                    currentData?.getValue(String::class.java)
                        ?: mergedResult?.toString()
                        ?: localText

                saveBackupFiles(finalText)
                callback("hgNativeSynced", finalText)
                setStatus("ONLINE")

                if (!pendingJson.isNullOrBlank()) {
                    runPendingTransaction()
                } else {
                    syncing.set(false)
                    if (!pendingJson.isNullOrBlank()) startSyncLoop()
                }
            }
        }, false)
    }

    private fun saveBackupFiles(json: String): Boolean {
        return try {
            synchronized(this) {
                val current = File(context.filesDir, "garage_backup_current.json")
                val previous1 = File(context.filesDir, "garage_backup_previous_1.json")
                val previous2 = File(context.filesDir, "garage_backup_previous_2.json")
                val temp = File(context.filesDir, "garage_backup_temp.json")

                temp.writeText(json)

                if (previous2.exists()) previous2.delete()
                if (previous1.exists()) previous1.copyTo(previous2, overwrite = true)
                if (current.exists()) current.copyTo(previous1, overwrite = true)
                temp.copyTo(current, overwrite = true)
                temp.delete()

                saveDailyBackupIfNeeded(json)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveDailyBackupIfNeeded(json: String) {
        try {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val lastDay = prefs.getString("last_daily_backup_day", null)
            if (lastDay == day) return

            val dir = File(context.filesDir, "daily_backups")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "garage-$day.json").writeText(json)
            prefs.edit().putString("last_daily_backup_day", day).apply()

            val files = dir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("garage-") }
                ?.sortedByDescending { it.name }
                ?: emptyList()
            files.drop(7).forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun mergeStates(remote: JSONObject?, local: JSONObject): JSONObject {
        if (remote == null) return normalizeForMerge(JSONObject(local.toString()))

        val r = normalizeForMerge(JSONObject(remote.toString()))
        val l = normalizeForMerge(JSONObject(local.toString()))
        val out = JSONObject()
        out.put("_syncSchema", 2)

        val tombstones = mergeTombstones(
            r.optJSONObject("_tombstones"),
            l.optJSONObject("_tombstones")
        )
        out.put("_tombstones", tombstones)

        for (collection in collections) {
            val merged = mergeArray(
                collection,
                r.optJSONArray(collection) ?: JSONArray(),
                l.optJSONArray(collection) ?: JSONArray(),
                tombstones.optJSONObject(collection)
            )
            out.put(collection, merged)
        }

        return out
    }

    private fun normalizeForMerge(obj: JSONObject): JSONObject {
        if (!obj.has("_tombstones") || obj.optJSONObject("_tombstones") == null) {
            obj.put("_tombstones", JSONObject())
        }

        val tombstones = obj.optJSONObject("_tombstones") ?: JSONObject()
        if (obj.optInt("_syncSchema", 0) < 2) {
            // Old test builds could manufacture delete markers merely because
            // one phone temporarily lacked records. Never let those erase cars.
            tombstones.put("jobs", JSONObject())
            tombstones.put("audit", JSONObject())
            obj.put("_tombstones", tombstones)
            obj.put("_syncSchema", 2)
        }

        for (collection in collections) {
            if (obj.optJSONArray(collection) == null) {
                obj.put(collection, JSONArray())
            }
        }
        return obj
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
                val ts = maxOf(
                    ro?.optLong(id, 0L) ?: 0L,
                    lo?.optLong(id, 0L) ?: 0L
                )
                if (ts > 0L) merged.put(id, ts)
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
                collection == "jobs" && jobStatusRank(obj) != jobStatusRank(existing) ->
                    jobStatusRank(obj) > jobStatusRank(existing)
                else -> preferOnTie
            }

            if (choose) map[id] = JSONObject(obj.toString())
        }

        for (i in 0 until remote.length()) {
            remote.optJSONObject(i)?.let { take(it, false) }
        }
        for (i in 0 until local.length()) {
            local.optJSONObject(i)?.let { take(it, true) }
        }

        val values = mutableListOf<JSONObject>()
        for ((id, obj) in map) {
            val deletedAt = tombstones?.optLong(id, 0L) ?: 0L
            if (collection == "audit" || deletedAt <= 0L) values.add(obj)
        }

        if (collection == "audit") {
            values.sortByDescending { it.optString("time", "") }
            return JSONArray(values.take(500))
        }

        return JSONArray(values)
    }

    private fun itemId(obj: JSONObject): String {
        val id = obj.opt("id")
        return if (id == null || id == JSONObject.NULL) obj.toString() else id.toString()
    }

    private fun jobStatusRank(obj: JSONObject): Int {
        if (obj.optBoolean("exited", false) || obj.optLong("exitedAt", 0L) > 0L) return 4
        return when (obj.optString("status")) {
            "delivered" -> 3
            "ready" -> 2
            else -> 1
        }
    }

    private fun snapshotObjectToJson(snapshot: DataSnapshot): JSONObject? {
        val value = snapshot.value ?: return null
        val wrapped = JSONObject.wrap(value)
        return wrapped as? JSONObject
    }

    private fun setStatus(value: String) {
        prefs.edit().putString("sync_status", value).apply()
    }

    private fun callbackStatus() {
        val status = getSyncStatus()
        val script =
            "window.hgNativeStatus && window.hgNativeStatus(" + JSONObject.quote(status) + ");"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    private fun callback(name: String, json: String) {
        val script = "window.$name && window.$name(" + JSONObject.quote(json) + ");"
        webView.post { webView.evaluateJavascript(script, null) }
    }

    companion object {
        private const val FIREBASE_APP_NAME = "hassan-garage-online-app"
    }
}
