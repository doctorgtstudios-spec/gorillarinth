package com.gorillarinth.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.flyfishxu.kadb.Kadb
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var nsdManager: NsdManager
    private data class AdbEndpoint(val name: String, val host: String, val port: Int)
    private val pairingEndpoints = CopyOnWriteArrayList<AdbEndpoint>()
    private val connectionEndpoints = CopyOnWriteArrayList<AdbEndpoint>()
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    @Volatile private var adbClient: Kadb? = null
    private val adbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pendingImportId: Int? = null
    private var importedManifest: JSONObject? = null

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingImportId ?: return@registerForActivityResult
        pendingImportId = null
        if (uri == null) {
            complete(id, false, "Import cancelled.")
            return@registerForActivityResult
        }
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        Thread {
            val result = importArchive(uri)
            runOnUiThread {
                if (result.first) complete(id, true, JSONObject().put("name", result.second).toString())
                else complete(id, false, result.second)
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsdManager = getSystemService(NsdManager::class.java)
        startAdbDiscovery("_adb-tls-pairing._tcp", pairing = true)
        startAdbDiscovery("_adb-tls-connect._tcp", pairing = false)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(NativeBridge(), "GorillaRinthNative")
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/gorillarinth/index.html")
    }

    override fun onDestroy() {
        runCatching { adbClient?.close() }
        discoveryListeners.forEach { listener -> runCatching { nsdManager.stopServiceDiscovery(listener) } }
        adbScope.cancel()
        super.onDestroy()
    }

    private fun complete(id: Int, ok: Boolean, value: String) {
        val encoded = if (ok) value else JSONObject.quote(value)
        runOnUiThread { web.evaluateJavascript("window.__drDone($id, $ok, $encoded)", null) }
    }

    private fun onIo(id: Int, action: () -> String) {
        Thread {
            try { complete(id, true, action()) }
            catch (e: Exception) { complete(id, false, e.message ?: "ADB action failed.") }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun startAdbDiscovery(serviceType: String, pairing: Boolean) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {}
            override fun onServiceLost(service: NsdServiceInfo) {
                val target = if (pairing) pairingEndpoints else connectionEndpoints
                target.removeAll { it.name == service.serviceName }
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val endpoint = AdbEndpoint(info.serviceName, host, info.port)
                        val target = if (pairing) pairingEndpoints else connectionEndpoints
                        target.removeAll { it.name == endpoint.name }
                        target.add(endpoint)
                        if (!pairing && getSharedPreferences("device", MODE_PRIVATE).getBoolean("autoReconnect", true)
                            && adbClient?.connectionCheck() != true) {
                            val savedHost = getSharedPreferences("device", MODE_PRIVATE).getString("host", null)
                            if (savedHost == null || savedHost == host) {
                                adbScope.launch { runCatching { connectTo(host, info.port) } }
                            }
                        }
                    }
                })
            }
        }
        discoveryListeners += listener
        runCatching { nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun pairPort(): Int = pairingEndpoints.firstOrNull()?.port ?: 0

    private fun connectTo(host: String, port: Int): Kadb {
        require(port in 1..65535) { "Wireless debugging connection port was not found." }
        adbClient?.let { runCatching { it.close() } }
        val client = Kadb.create(host, port, connectTimeout = 8000, socketTimeout = 15000)
        val response = client.shell("echo gorillarinth")
        check(response.exitCode == 0 && response.allOutput.contains("gorillarinth")) {
            "ADB connected, but the headset did not answer the test command."
        }
        adbClient = client
        getSharedPreferences("device", MODE_PRIVATE).edit()
            .putString("host", host).putInt("connectPort", port).apply()
        return client
    }

    private fun pairWireless(port: Int, code: String, requestedConnectPort: Int): String {
        require(code.matches(Regex("\\d{6}"))) { "Enter the six-digit Wireless debugging pairing code." }
        val endpoint = pairingEndpoints.firstOrNull { it.port == port }
            ?: pairingEndpoints.firstOrNull()
            ?: error("Pairing port not detected. Open Wireless debugging > Pair device with pairing code, then wait a moment.")
        val actualPairPort = if (port in 1..65535) port else endpoint.port
        runBlocking { Kadb.pair(endpoint.host, actualPairPort, code) }
        getSharedPreferences("device", MODE_PRIVATE).edit().putString("host", endpoint.host).apply()

        var connectEndpoint: AdbEndpoint? = null
        for (attempt in 0 until 20) {
            connectEndpoint = connectionEndpoints.firstOrNull {
                it.host == endpoint.host && (requestedConnectPort == 0 || it.port == requestedConnectPort)
            } ?: connectionEndpoints.firstOrNull { requestedConnectPort == 0 || it.port == requestedConnectPort }
            if (connectEndpoint != null) break
            Thread.sleep(250)
        }
        val connectPort = if (requestedConnectPort > 0) requestedConnectPort else
            connectEndpoint?.port ?: error("Pairing succeeded, but the ADB connection port was not discovered. Leave Wireless debugging enabled and retry Connect.")
        connectTo(endpoint.host, connectPort)
        return JSONObject().put("paired", true).put("host", endpoint.host)
            .put("port", connectPort).toString()
    }

    private fun reconnectWireless(): Boolean {
        val prefs = getSharedPreferences("device", MODE_PRIVATE)
        val savedHost = prefs.getString("host", null) ?: return false
        val live = connectionEndpoints.firstOrNull { it.host == savedHost }
        val host = live?.host ?: savedHost
        val port = live?.port ?: prefs.getInt("connectPort", 0)
        return runCatching { connectTo(host, port); true }.getOrDefault(false)
    }


    private fun requireAdb(): Kadb {
        adbClient?.takeIf { it.connectionCheck() }?.let { return it }
        check(reconnectWireless()) { "Quest is not connected. Pair Wireless debugging first." }
        return adbClient ?: error("ADB connection was lost.")
    }

    private fun shellChecked(client: Kadb, command: String): String {
        val result = client.shell(command)
        check(result.exitCode == 0) { result.allOutput.ifBlank { "Quest command failed." } }
        return result.allOutput
    }

    private fun pullOriginalObb(): String {
        val client = requireAdb()
        val dir = "/sdcard/Android/obb/com.AnotherAxiom.GorillaTag"
        val listing = client.shell("ls -1 $dir/*.obb 2>/dev/null").allOutput
        val remote = listing.lineSequence().map { it.trim() }
            .firstOrNull { it.endsWith(".obb") && !it.endsWith(".gorillarinth.bak") }
            ?: error("No Gorilla Tag OBB found. Install or launch Gorilla Tag once, then retry.")
        val original = File(filesDir, "base_original.obb")
        val remoteBackup = "$remote.gorillarinth.bak"
        if (!original.exists() || original.length() == 0L) {
            val backupCheck = client.shell("if [ -f '$remoteBackup' ]; then echo exists; else echo missing; fi").allOutput.trim()
            // Migrate an older release's headset-side backup into GorillaRinth's private app storage.
            client.pull(original, if (backupCheck == "exists") remoteBackup else remote)
            check(original.length() > 0L) { "The downloaded OBB is empty." }
        }
        // Remove any backup left by older versions; the clean copy now lives only in filesDir.
        shellChecked(client, "rm -f '$remoteBackup'")
        getSharedPreferences("device", MODE_PRIVATE).edit().putString("obbPath", remote).apply()
        return remote
    }

    private fun patchObb(): JSONObject {
        val client = requireAdb()
        val original = File(filesDir, "base_original.obb")
        if (!original.exists()) pullOriginalObb()
        val remote = getSharedPreferences("device", MODE_PRIVATE).getString("obbPath", null)
            ?: error("Pull the original game OBB before applying mods.")
        val manifest = readManifest()
        val modId = manifest?.optString("id", manifest.optString("name", "local-mod"))
        val enabled = modId != null && getSharedPreferences("mods", MODE_PRIVATE).getBoolean(modId, false)
        shellChecked(client, "am force-stop com.AnotherAxiom.GorillaTag")
        if (!enabled) {
            val tempRemote = "$remote.gorillarinth.tmp"
            client.push(original, tempRemote)
            shellChecked(client, "mv '$tempRemote' '$remote'")
            File(filesDir, "base_patched.obb").delete()
            getSharedPreferences("device", MODE_PRIVATE).edit().remove("appliedModId").remove("appliedAt").apply()
            return JSONObject().put("mods", 0).put("removed", JSONArray()).put("bytes", original.length())
                .put("backup", "GorillaRinth app data")
        }
        val manifestFile = findManifestFile() ?: error("The imported mod manifest is missing.")
        val overlayRoot = File(manifestFile.parentFile, "obb")
        check(overlayRoot.isDirectory) { "This mod has no obb/ folder. Put files to add or replace inside obb/ in the mod ZIP." }
        val overlays = overlayRoot.walkTopDown().filter { it.isFile }.associateBy {
            it.relativeTo(overlayRoot).invariantSeparatorsPath
        }
        check(overlays.isNotEmpty()) { "The mod's obb/ folder is empty." }
        val patched = File(filesDir, "base_patched.obb")
        val names = overlays.keys
        ZipFile(original).use { source ->
            ZipOutputStream(patched.outputStream().buffered()).use { target ->
                val entries = source.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name in names) continue
                    target.putNextEntry(ZipEntry(entry.name))
                    source.getInputStream(entry).use { it.copyTo(target) }
                    target.closeEntry()
                }
                overlays.forEach { (name, file) ->
                    require(name.isNotBlank() && !name.startsWith("/") &&
                        !name.contains('\\') && name.split('/').none { it == ".." || it.isBlank() }) {
                        "Unsafe OBB patch path: $name"
                    }
                    target.putNextEntry(ZipEntry(name))
                    file.inputStream().buffered().use { it.copyTo(target) }
                    target.closeEntry()
                }
            }
        }
        check(patched.length() > 0L) { "Patched OBB output is empty." }
        ZipFile(patched).use { archive ->
            check(archive.entries().hasMoreElements()) { "Patched OBB has no archive entries." }
        }
        val tempRemote = "$remote.gorillarinth.tmp"
        client.push(patched, tempRemote)
        shellChecked(client, "mv '$tempRemote' '$remote'")
        getSharedPreferences("device", MODE_PRIVATE).edit()
            .putString("appliedModId", modId).putLong("appliedAt", System.currentTimeMillis()).apply()
        return JSONObject().put("mods", 1).put("removed", JSONArray())
            .put("bytes", patched.length()).put("backup", "$remote.gorillarinth.bak")
    }

    private fun restoreOriginalObb(): String {
        val client = requireAdb()
        val remote = getSharedPreferences("device", MODE_PRIVATE).getString("obbPath", null)
            ?: error("No saved Gorilla Tag OBB path. Connect and use Copy from Quest first.")
        val local = File(filesDir, "base_original.obb")
        check(local.exists() && local.length() > 0L) { "No original OBB backup was found in GorillaRinth's app data. Use Copy from Quest first." }
        val tempRemote = "$remote.gorillarinth.restore"
        client.push(local, tempRemote)
        shellChecked(client, "mv '$tempRemote' '$remote'")
        shellChecked(client, "rm -f '$remote.gorillarinth.bak'")
        File(filesDir, "base_patched.obb").delete()
        getSharedPreferences("device", MODE_PRIVATE).edit().remove("appliedModId").remove("appliedAt").apply()
        return "Original Gorilla Tag OBB restored. Backup is stored privately in GorillaRinth app data."
    }

    private fun state(): JSONObject {
        val library = JSONArray()
        val manifest = readManifest()
        if (manifest != null) {
            val id = manifest.optString("id", manifest.optString("name", "local-mod"))
            val enabled = getSharedPreferences("mods", MODE_PRIVATE).getBoolean(id, false)
            val mod = JSONObject()
                .put("id", id)
                .put("name", manifest.optString("name", "Imported mod"))
                .put("creator", manifest.optString("author", "Unknown"))
                .put("summary", "Imported locally • v" + manifest.optString("version", "unknown"))
                .put("version", manifest.optString("version", "unknown"))
                .put("enabled", enabled)
                .put("bytes", File(filesDir, "imported_mod").walkTopDown().filter { it.isFile }.sumOf { it.length() })
                .put("applied", getSharedPreferences("device", MODE_PRIVATE).getString("appliedModId", null) == id)
                .put("added", getSharedPreferences("mods", MODE_PRIVATE).getLong("added:$id", 0L))
            library.put(mod)
        }
        val gameIntent = packageManager.getLaunchIntentForPackage("com.AnotherAxiom.GorillaTag")
        val game = if (gameIntent == null) JSONObject.NULL else {
            val info = packageManager.getPackageInfo("com.AnotherAxiom.GorillaTag", 0)
            JSONObject().put("versionName", info.versionName ?: "Unknown")
                .put("versionCode", info.versionCode.toLong()).put("running", false)
        }
        val prefs = getSharedPreferences("device", MODE_PRIVATE)
        val paired = prefs.getString("host", null) != null
        val connected = adbClient?.connectionCheck() == true
        val baseFile = File(filesDir, "base_original.obb")
        val baseUsable = baseFile.exists() && baseFile.length() > 0L
        val modId = manifest?.optString("id", manifest.optString("name", "local-mod"))
        val enabledMod = modId != null && getSharedPreferences("mods", MODE_PRIVATE).getBoolean(modId, false)
        val appliedModId = prefs.getString("appliedModId", null)
        val pending = if (appliedModId == null) enabledMod else appliedModId != modId || !enabledMod
        return JSONObject()
            .put("library", library)
            .put("device", JSONObject().put("state", if (connected) "ready" else if (paired) "lost" else "none")
                .put("paired", paired).put("autoReconnect", prefs.getBoolean("autoReconnect", true)).put("model", "Quest"))
            .put("game", game)
            .put("base", JSONObject().put("usable", baseUsable).put("fromModded", File(filesDir, "base_patched.obb").exists())
                .put("name", if (baseUsable) "Original Gorilla Tag OBB" else ""))
            .put("checks", JSONArray())
            .put("busy", JSONObject.NULL)
            .put("appliedAt", if (appliedModId != null) prefs.getLong("appliedAt", 0L).takeIf { it > 0L } ?: JSONObject.NULL else JSONObject.NULL)
            .put("capacity", 0).put("used", 0)
            .put("pending", pending).put("canApply", connected && baseUsable && pending)
            .put("version", "0.1.0")
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun call(id: Int, method: String, rawArgs: String) {
            try {
                val args = JSONArray(rawArgs)
                when (method) {
                    "state" -> complete(id, true, state().toString())
                    "storeList" -> complete(id, true, JSONObject()
                        .put("configured", false).put("mods", JSONArray())
                        .put("offline", true).put("offlineReason", "The GorillaRinth server catalog will be connected later.")
                        .put("name", "GorillaRinth Store").toString())
                    "installLocal" -> runOnUiThread {
                        pendingImportId = id
                        importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                    "launch" -> {
                        val intent = packageManager.getLaunchIntentForPackage("com.AnotherAxiom.GorillaTag")
                            ?: throw IllegalStateException("Gorilla Tag is not installed.")
                        runOnUiThread { startActivity(intent) }
                        complete(id, true, "true")
                    }
                    "openDevSettings" -> {
                        runOnUiThread {
                            val intent = Intent("android.settings.SETTINGS")
                                .setPackage("com.android.settings")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { startActivity(intent) }.onFailure {
                                val fallback = Intent("android.settings.SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { startActivity(fallback) }.onFailure {
                                    Toast.makeText(this@MainActivity, "Android Settings are unavailable on this device.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        complete(id, true, "true")
                    }
                    "pairingPort" -> complete(id, true, pairPort().toString())
                    "pair" -> onIo(id) {
                        pairWireless(args.optInt(0), args.optString(1), args.optInt(2)).also { }
                    }
                    "reconnect" -> onIo(id) { reconnectWireless().toString() }
                    "pullBase", "chooseBase" -> onIo(id) { pullOriginalObb(); JSONObject().put("saved", true).toString() }
                    "apply" -> onIo(id) { patchObb().toString() }
                    "restoreBase" -> onIo(id) { JSONObject().put("message", restoreOriginalObb()).toString() }
                    "stopGame" -> onIo(id) {
                        val client = requireAdb()
                        shellChecked(client, "am force-stop com.AnotherAxiom.GorillaTag")
                        "true"
                    }
                    "toggle" -> {
                        val modId = args.optString(0)
                        val prefs = getSharedPreferences("mods", MODE_PRIVATE)
                        prefs.edit().putBoolean(modId, !prefs.getBoolean(modId, false)).apply()
                        complete(id, true, "true")
                    }
                    "setMany" -> {
                        val ids = args.optJSONArray(0) ?: JSONArray()
                        val on = args.optBoolean(1)
                        val edit = getSharedPreferences("mods", MODE_PRIVATE).edit()
                        for (i in 0 until ids.length()) edit.putBoolean(ids.optString(i), on)
                        edit.apply()
                        complete(id, true, "true")
                    }
                    "remove" -> {
                        val requestedId = args.optString(0)
                        val manifest = readManifest()
                        val actualId = manifest?.optString("id", manifest.optString("name", "local-mod"))
                        if (actualId != null && requestedId == actualId) {
                            File(filesDir, "imported_mod").deleteRecursively()
                            getSharedPreferences("mods", MODE_PRIVATE).edit().remove(actualId).remove("added:$actualId").apply()
                        }
                        complete(id, true, "true")
                    }
                    "setAutoReconnect" -> {
                        val enabled = args.optBoolean(0, false)
                        getSharedPreferences("device", MODE_PRIVATE).edit().putBoolean("autoReconnect", enabled).apply()
                        complete(id, true, enabled.toString())
                    }
                    "storeConfig" -> complete(id, true, JSONObject().put("url", "").put("configured", false).toString())
                    "storeSetUrl", "storeInstall" ->
                        throw UnsupportedOperationException(when (method) {
                            "storeSetUrl", "storeInstall" -> "The GorillaRinth server catalog is not connected yet."
                            else -> "This game-management action is not implemented yet."
                        })
                    else -> throw IllegalArgumentException("Unsupported action: $method")
                }
            } catch (e: Exception) {
                complete(id, false, e.message ?: "Action failed.")
            }
        }
    }

    private fun importArchive(uri: Uri): Pair<Boolean, String> {
        val temp = File(filesDir, "imported_mod_tmp")
        val root = File(filesDir, "imported_mod")
        return try {
            temp.deleteRecursively()
            if (!temp.mkdirs()) error("Could not create import folder.")
            var count = 0
            var bytes = 0L
            val canonicalRoot = temp.canonicalPath + File.separator
            contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        count++
                        if (count > 500) error("Archive contains too many entries (limit 500).")
                        val out = File(temp, entry.name)
                        if (!out.canonicalPath.startsWith(canonicalRoot) && out.canonicalPath != temp.canonicalPath) error("Unsafe ZIP path.")
                        if (entry.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { sink ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    bytes += n
                                    if (bytes > 256L * 1024 * 1024) error("Archive exceeds the 256 MB extraction limit.")
                                    sink.write(buffer, 0, n)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("Could not read selected archive.")
            val modJson = temp.walkTopDown().firstOrNull { it.isFile && it.name.equals("mod.json", true) }
                ?: error("Archive extracted, but no mod.json manifest was found.")
            val parsed = JSONObject(modJson.readText())
            val modName = parsed.optString("name").trim()
            require(modName.isNotEmpty()) { "The mod.json manifest must contain a name." }
            val modId = parsed.optString("id", modName).trim()
            require(modId.length <= 128 && modId.matches(Regex("[A-Za-z0-9._-]+"))) {
                "The mod.json id may only contain letters, numbers, dots, underscores and hyphens."
            }
            val previous = File(filesDir, "imported_mod_previous")
            previous.deleteRecursively()
            if (root.exists() && !root.renameTo(previous)) error("Could not preserve the existing mod before importing.")
            if (!temp.renameTo(root)) {
                if (previous.exists()) previous.renameTo(root)
                error("Could not save imported archive.")
            }
            previous.deleteRecursively()
            importedManifest = parsed
            getSharedPreferences("mods", MODE_PRIVATE).edit()
                .putLong("added:$modId", System.currentTimeMillis()).apply()
            true to modName
        } catch (e: Exception) {
            temp.deleteRecursively()
            false to (e.message ?: "Import failed.")
        }
    }

    private fun findManifestFile(): File? {
        val root = File(filesDir, "imported_mod")
        if (!root.isDirectory) return null
        return root.walkTopDown().firstOrNull { it.isFile && it.name.equals("mod.json", true) }
    }

    private fun readManifest(): JSONObject? {
        val file = findManifestFile() ?: return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }
}
