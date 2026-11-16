package com.gorillarinth.app

import android.content.Intent
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.widget.EditText
import android.text.InputType
import android.provider.Settings
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.util.Base64
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
    private var pendingApkImportId: Int? = null

    // Kept separate from the existing OBB backup and mod archive import.
    private val apkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingApkImportId ?: return@registerForActivityResult
        pendingApkImportId = null
        if (uri == null) {
            complete(id, false, "APK import cancelled.")
            return@registerForActivityResult
        }
        Thread {
            try {
                val stagedTemp = File(filesDir, "gorilla_tag_download.apk.tmp")
                val staged = File(filesDir, "gorilla_tag_download.apk")
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Could not open the selected APK." }
                    FileOutputStream(stagedTemp).use { output -> input.copyTo(output) }
                }
                val info = packageManager.getPackageArchiveInfo(stagedTemp.absolutePath, 0)
                    ?: throw IllegalArgumentException("The selected file is not a valid Android APK.")
                require(info.packageName == "com.AnotherAxiom.GorillaTag") {
                    "This APK is for ${info.packageName}, not Gorilla Tag."
                }
                if (staged.exists()) require(staged.delete()) { "Could not replace the previously imported APK." }
                require(stagedTemp.renameTo(staged)) { "Could not save the imported Gorilla Tag APK." }
                val result = JSONObject()
                    .put("packageName", info.packageName)
                    .put("versionName", info.versionName ?: "Unknown")
                    .put("versionCode", if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong())
                    .put("fileName", staged.name)
                    .put("size", staged.length())
                runOnUiThread { complete(id, true, result.toString()) }
            } catch (e: Exception) {
                File(filesDir, "gorilla_tag_download.apk.tmp").delete()
                runOnUiThread { complete(id, false, e.message ?: "Could not import APK.") }
            }
        }.start()
    }

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
                if (result.first) {
                    val plan = runCatching {
                        JSONObject(getSharedPreferences("qmod", MODE_PRIVATE).getString("lastGameVersionMatch", "{}") ?: "{}")
                    }.getOrDefault(JSONObject())
                    complete(id, true, JSONObject().put("name", result.second).put("gameVersionMatch", plan).toString())
                } else complete(id, false, result.second)
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
        val manifest = readManifest()
        if (manifest?.optString("format") == "native-library") {
            return installNativeLibraries(client, manifest)
        }
        val original = File(filesDir, "base_original.obb")
        if (!original.exists()) pullOriginalObb()
        val remote = getSharedPreferences("device", MODE_PRIVATE).getString("obbPath", null)
            ?: error("Pull the original game OBB before applying mods.")
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

    private fun installNativeLibraries(client: Kadb, manifest: JSONObject): JSONObject {
        val gamePackage = "com.AnotherAxiom.GorillaTag"
        val importedRoot = File(filesDir, "imported_mod")
        val libraries = importedRoot.walkTopDown().filter {
            it.isFile && it.extension.equals("so", ignoreCase = true)
        }.toList()
        check(libraries.isNotEmpty()) { "No .so files were found in the imported mod." }

        libraries.forEach { library ->
            val info = inspectNativeLibrary(library)
            check(info.optBoolean("isElf", false)) {
                "${library.name} is not a valid ELF shared library."
            }
            check(info.optBoolean("deviceAbiSupported", false)) {
                "${library.name} targets ${info.optString("abi")}, not this headset's ABI (${android.os.Build.SUPPORTED_ABIS.joinToString()})."
            }
        }

        // QuestLoader's native modloader must be injected into the game's APK first.
        val packageInfo = shellChecked(client, "dumpsys package $gamePackage")
        val nativeDir = Regex("nativeLibraryDir=([^\\s]+)")
            .find(packageInfo)?.groupValues?.getOrNull(1)
            ?: error("Could not locate Gorilla Tag's native library directory.")
        val loaderCheck = client.shell("test -f '$nativeDir/libmodloader.so' && echo installed").allOutput
        check(loaderCheck.contains("installed")) {
            "Gorilla Tag does not have QuestLoader installed yet. A native loader must be patched into the game's APK before .so mods can run. GorillaRinth has not patched the APK, so no library was copied."
        }

        val modId = manifest.optString("id", "native-mod")
        val remoteDir = "/sdcard/Android/data/$gamePackage/files/libs"
        shellChecked(client, "am force-stop $gamePackage")
        shellChecked(client, "mkdir -p '$remoteDir' && chmod 755 '$remoteDir'")
        libraries.forEach { library ->
            client.push(library, "$remoteDir/${library.name}")
            shellChecked(client, "chmod 644 '$remoteDir/${library.name}'")
        }
        getSharedPreferences("mods", MODE_PRIVATE).edit()
            .putBoolean(modId, true)
            .putString("nativeLibDir", remoteDir)
            .putString("nativeLibNames", JSONArray(libraries.map { it.name }).toString())
            .apply()
        return JSONObject()
            .put("mods", libraries.size)
            .put("nativeLibraries", JSONArray(libraries.map { it.name }))
            .put("loadedOnNextLaunch", true)
            .put("loader", "QuestLoader")
            .put("destination", remoteDir)
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
                .put("summary", manifest.optString("description", "Imported locally • v" + manifest.optString("version", "unknown")))
                .put("version", manifest.optString("version", "unknown"))
                .put("thumb", coverDataUri())
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
            .put("qmodGameVersionMatch", runCatching {
                JSONObject(getSharedPreferences("qmod", MODE_PRIVATE).getString("lastGameVersionMatch", "{}") ?: "{}")
            }.getOrDefault(JSONObject()))
            .put("stagedApk", File(filesDir, "gorilla_tag_download.apk").let { it.exists() && it.length() > 0L })
            .put("patchedApk", File(filesDir, "gorilla_tag_patched.apk").let { it.exists() && it.length() > 0L })
            .put("version", "0.1.0")
    }

    private fun downloadMatchedBuild(accessToken: String): String {
        require(accessToken.matches(Regex("[A-Za-z0-9._-]{20,512}"))) {
            "That Meta access token does not look valid. Generate a fresh Oculus platform utility token in Meta Developer Dashboard."
        }
        val plan = JSONObject(getSharedPreferences("qmod", MODE_PRIVATE)
            .getString("lastGameVersionMatch", "{}") ?: "{}")
        require(plan.optString("status") == "download") { "Import a QMOD with a specific Gorilla Tag packageVersion first." }
        val versionId = plan.optString("versionId")
        require(versionId.matches(Regex("[0-9]+"))) { "The matched OculusDB build ID is invalid." }
        val downloadUrl = "https://securecdn.oculus.com/binaries/download/?id=" +
            java.net.URLEncoder.encode(versionId, "UTF-8") + "&access_token=" +
            java.net.URLEncoder.encode(accessToken, "UTF-8")
        val temp = File(filesDir, "gorilla_tag_download.apk.tmp")
        val staged = File(filesDir, "gorilla_tag_download.apk")
        val connection = java.net.URL(downloadUrl).openConnection().apply {
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("User-Agent", "GorillaRinth/0.1")
            setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
        }
        val http = connection as? java.net.HttpURLConnection
        try {
            if (http != null) require(http.responseCode in 200..299) {
                "Meta download failed (HTTP ${http.responseCode}). Check that your token is current and your account owns Gorilla Tag."
            }
            var total = 0L
            connection.getInputStream().use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 1500L * 1024 * 1024) { "Downloaded APK is larger than the 1.5 GB safety limit." }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(total > 1024L * 1024) { "Meta returned an empty or incomplete APK download." }
            val info = packageManager.getPackageArchiveInfo(temp.absolutePath, 0)
                ?: error("Meta did not return a valid APK. The token may have expired or the build may be unavailable.")
            require(info.packageName == "com.AnotherAxiom.GorillaTag") {
                "Downloaded package ${info.packageName}; expected Gorilla Tag."
            }
            val expected = plan.optString("targetVersion")
            val actual = info.versionName.orEmpty()
            require(actual.equals(expected, ignoreCase = true)) {
                "Downloaded Gorilla Tag version $actual, but this QMOD requires $expected. The APK was not installed."
            }
            val backup = File(filesDir, "gorilla_tag_download.apk.previous")
            if (staged.exists()) {
                backup.delete()
                check(staged.renameTo(backup)) { "Could not preserve the previous APK download." }
            }
            if (!temp.renameTo(staged)) {
                if (backup.exists()) backup.renameTo(staged)
                error("Could not save the downloaded Gorilla Tag APK.")
            }
            backup.delete()
            return JSONObject().put("packageName", info.packageName)
                .put("versionName", actual).put("size", staged.length()).toString()
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            http?.disconnect()
        }
    }

    private fun startPackageInstall(apk: File): String {
        require(apk.exists() && apk.length() > 0L) { "No downloaded Gorilla Tag APK is available." }
        if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            runOnUiThread { startActivity(settings) }
            error("Allow GorillaRinth to install unknown apps in Android settings, then tap Install again.")
        }
        val info = packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: error("The staged file is not a valid APK.")
        require(info.packageName == "com.AnotherAxiom.GorillaTag") { "Only a Gorilla Tag APK can be installed here." }
        val installer = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(info.packageName)
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("gorilla-tag.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                val callback = Intent(this, ApkInstallReceiver::class.java)
                    .setAction(ApkInstallReceiver.ACTION_INSTALL_RESULT)
                val pending = PendingIntent.getBroadcast(
                    this, sessionId, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
        return "Android package installer opened for Gorilla Tag ${info.versionName ?: "APK"}."
    }

    private fun promptForMetaToken(id: Int) {
        val input = EditText(this).apply {
            hint = "Oculus platform utility token"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            singleLine = true
        }
        AlertDialog.Builder(this)
            .setTitle("Download matched Gorilla Tag build")
            .setMessage("Sign in at Meta Developer Dashboard, then open Account settings → Get Oculus platform utility token. Paste that token here. GorillaRinth uses it only for this download and does not save it.")
            .setView(input)
            .setNegativeButton("Cancel") { _, _ -> complete(id, false, "Download cancelled.") }
            .setPositiveButton("Download") { _, _ ->
                val token = input.text?.toString()?.trim().orEmpty()
                onIo(id) {
                    val result = JSONObject(downloadMatchedBuild(token))
                    result.toString()
                }
            }
            .setOnCancelListener { complete(id, false, "Download cancelled.") }
            .show()
    }

    private fun resolveQmodGameVersion(manifest: JSONObject): JSONObject {
        val packageId = manifest.optString("packageId").trim()
        if (packageId.isNotEmpty() && packageId != "com.AnotherAxiom.GorillaTag") {
            return JSONObject().put("status", "not-gorilla-tag")
                .put("message", "This QMOD targets $packageId, not Gorilla Tag.")
        }
        val targetVersion = manifest.optString("packageVersion").trim()
        if (targetVersion.isEmpty()) {
            return JSONObject().put("status", "unspecified")
                .put("message", "This QMOD does not specify packageVersion; no game build was selected.")
        }
        val installedVersion = runCatching {
            packageManager.getPackageInfo("com.AnotherAxiom.GorillaTag", 0).versionName.orEmpty()
        }.getOrDefault("")
        if (installedVersion.equals(targetVersion, ignoreCase = true)) {
            return JSONObject().put("status", "already-installed")
                .put("targetVersion", targetVersion).put("installedVersion", installedVersion)
                .put("message", "The installed Gorilla Tag version already matches this QMOD.")
        }
        val catalog = fetchOculusVersions()
        val versions = catalog.optJSONArray("versions") ?: JSONArray()
        var match: JSONObject? = null
        for (i in 0 until versions.length()) {
            val item = versions.optJSONObject(i) ?: continue
            if (item.optString("version").equals(targetVersion, ignoreCase = true)) {
                match = item
                break
            }
        }
        if (match == null) {
            return JSONObject().put("status", "not-found")
                .put("targetVersion", targetVersion).put("installedVersion", installedVersion)
                .put("message", "OculusDB has no downloadable Gorilla Tag build matching QMOD packageVersion $targetVersion.")
        }
        return JSONObject().put("status", "download")
            .put("targetVersion", targetVersion).put("installedVersion", installedVersion)
            .put("appId", catalog.optString("appId"))
            .put("versionId", match.optString("id"))
            .put("versionCode", match.optLong("versionCode"))
            .put("message", "Found the exact Gorilla Tag build required by this QMOD.")
    }

    private fun openPendingQmodBuild(plan: JSONObject) {
        if (plan.optString("status") != "download") return
        val appId = plan.optString("appId")
        val versionId = plan.optString("versionId")
        if (!appId.matches(Regex("[0-9]+")) || !versionId.matches(Regex("[0-9]+"))) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://developer.oculus.com/manage/applications/$appId/versions/$versionId"
        ))
        runCatching { startActivity(intent) }
    }

    private fun fetchOculusVersions(): JSONObject {
        fun getJson(path: String): JSONObject {
            var last: Exception? = null
            for (host in listOf("https://oculusdb-rewrite.rui2015.me", "https://oculusdb.rui2015.me")) {
                try {
                    val connection = java.net.URL(host + path).openConnection().apply {
                        connectTimeout = 12000
                        readTimeout = 20000
                        setRequestProperty("User-Agent", "GorillaRinth/0.1")
                        setRequestProperty("Accept", "application/json")
                    }
                    val http = connection as? java.net.HttpURLConnection
                    try {
                        if (http != null) require(http.responseCode in 200..299) { "OculusDB HTTP " + http.responseCode }
                        return JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
                    } finally { http?.disconnect() }
                } catch (e: Exception) { last = e }
            }
            throw IllegalStateException("OculusDB could not be reached: " + (last?.message ?: "unknown error"))
        }
        val app = getJson("/api/v1/packagename/com.AnotherAxiom.GorillaTag")
        val appId = app.optString("id").ifBlank {
            app.optJSONObject("application")?.optString("id").orEmpty()
        }
        require(appId.matches(Regex("[0-9]+"))) { "OculusDB did not return the Gorilla Tag application ID." }
        val versions = JSONArray()
        val list = getArray("/api/v1/versions/" + appId + "?onlydownloadable=true")
        for (i in 0 until list.length()) {
            val v = list.optJSONObject(i) ?: continue
            val channels = v.optJSONArray("releaseChannels") ?: JSONArray()
            if (channels.length() == 0) continue
            versions.put(JSONObject()
                .put("id", v.optString("id"))
                .put("version", v.optString("version"))
                .put("versionCode", v.optLong("versionCode"))
                .put("uploadedDate", v.optString("uploadedDate"))
                .put("size", v.optLong("size"))
                .put("filename", v.optString("filename"))
                .put("appId", appId))
        }
        return JSONObject()
            .put("source", "OculusDB")
            .put("appId", appId)
            .put("versions", versions)
            .put("note", "OculusDB provides version metadata. Meta requires an owner account signed into the developer portal to retrieve protected APK/OBB files.")
    }

    private fun getArray(path: String): JSONArray {
        var last: Exception? = null
        for (host in listOf("https://oculusdb-rewrite.rui2015.me", "https://oculusdb.rui2015.me")) {
            try {
                val connection = java.net.URL(host + path).openConnection().apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "GorillaRinth/0.1")
                    setRequestProperty("Accept", "application/json")
                }
                val http = connection as? java.net.HttpURLConnection
                try {
                    if (http != null) require(http.responseCode in 200..299) { "OculusDB HTTP " + http.responseCode }
                    return JSONArray(connection.getInputStream().bufferedReader().use { it.readText() })
                } finally { http?.disconnect() }
            } catch (e: Exception) { last = e }
        }
        throw IllegalStateException("Could not retrieve Gorilla Tag versions from OculusDB: " + (last?.message ?: "unknown error"))
    }

    inner class NativeBridge {
        @JavascriptInterface
        fun call(id: Int, method: String, rawArgs: String) {
            try {
                val args = JSONArray(rawArgs)
                when (method) {
                    "state" -> complete(id, true, state().toString())
                    "oculusVersions" -> onIo(id) { fetchOculusVersions().toString() }
                    "openOculusVersion" -> {
                        val appId = args.optString(0)
                        val versionId = args.optString(1)
                        require(appId.matches(Regex("[0-9]+")) && versionId.matches(Regex("[0-9]+"))) { "Invalid OculusDB version selection." }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.oculus.com/manage/applications/" + appId + "/versions/" + versionId))
                        runOnUiThread { startActivity(intent) }
                        complete(id, true, "true")
                    }
                    "downloadMatchedApk" -> runOnUiThread { promptForMetaToken(id) }
                    "installImportedApk" -> onIo(id) { startPackageInstall(File(filesDir, "gorilla_tag_download.apk")) }
                    "patchGameApk" -> onIo(id) {
                        val patched = QuestLoaderApkPatcher.patch(this, File(filesDir, "gorilla_tag_download.apk"))
                        JSONObject().put("fileName", patched.name).put("size", patched.length()).toString()
                    }
                    "installPatchedApk" -> onIo(id) { startPackageInstall(File(filesDir, "gorilla_tag_patched.apk")) }
                    "uninstallOfficialGame" -> runOnUiThread {
                        val uninstall = Intent(Intent.ACTION_DELETE, Uri.parse("package:com.AnotherAxiom.GorillaTag"))
                        startActivity(uninstall)
                        complete(id, true, "Android opened the Gorilla Tag uninstall confirmation. Confirm it, then return and install the patched APK.")
                    }
                    "importDownloadedApk" -> runOnUiThread {
                        pendingApkImportId = id
                        apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
                    }
                    "openMetaLogin" -> {
                        // Delegate authentication to Meta's official HTTPS sign-in page.
                        // GorillaRinth never handles or stores Meta passwords or browser cookies.
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://developers.meta.com/vr/manage/"))
                        runOnUiThread {
                            try {
                                startActivity(intent)
                                complete(id, true, "true")
                            } catch (e: Exception) {
                                complete(id, false, "Could not open Meta sign-in. Install or enable a web browser and try again.")
                            }
                        }
                    }
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
                            // Use Meta's Horizon OS system deep link, not Android's generic Settings intent.
                            val intent = packageManager.getLaunchIntentForPackage("com.oculus.vrshell")
                                ?.putExtra("intent_data", "systemux://settings")
                                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (intent != null) {
                                runCatching { startActivity(intent) }.onFailure {
                                    Toast.makeText(this@MainActivity, "Quest Settings could not be opened.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "Quest Settings are unavailable on this headset.", Toast.LENGTH_LONG).show()
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
                ?: temp.walkTopDown().firstOrNull { it.isFile && it.name.equals("manifest.json", true) }
            var nativeLibraries = temp.walkTopDown().filter {
                it.isFile && it.extension.equals("so", true)
            }.toList()
            val parsed = if (modJson != null) {
                JSONObject(modJson.readText())
            } else {
                require(nativeLibraries.isNotEmpty()) {
                    "No mod.json, manifest.json, or .so native library files were found in this archive."
                }
                val labels = nativeLibraries.map { it.name }.distinct()
                val baseName = labels.first().removeSuffix(".so").removePrefix("lib")
                    .replace(Regex("[^A-Za-z0-9]+"), " ").trim()
                JSONObject()
                    .put("name", baseName.ifBlank { "Native library mod" })
                    .put("id", "native-" + labels.joinToString("-").hashCode().toUInt().toString(16))
                    .put("version", "unknown")
                    .put("author", "Unknown")
                    .put("description", "Detected ${nativeLibraries.size} .so native library file(s). A compatible Quest native mod loader is required to load them.")
                    .put("format", "native-library")
                    .also { it.put("nativeLibraries", JSONArray(labels)) }
                    .also { it.writeTo(File(temp, "manifest.json")) }
            }
            // QMOD manifests declare native payloads in modFiles (not only libraryFiles).
            // QMOD packageVersion is the target Android game's version (not the mod's own "version").
            val gameVersionPlan = runCatching { resolveQmodGameVersion(parsed) }.getOrElse { error ->
                JSONObject().put("status", "lookup-failed")
                    .put("targetVersion", parsed.optString("packageVersion"))
                    .put("message", "Could not check OculusDB right now: ${error.message ?: "network error"}. The mod was still imported.")
            }
            parsed.put("gameVersionMatch", gameVersionPlan)
            val dependencyReport = resolveQmodDependencies(temp, parsed)
            if (dependencyReport.length() > 0) parsed.put("resolvedDependencies", dependencyReport)
            nativeLibraries = temp.walkTopDown().filter {
                it.isFile && it.extension.equals("so", true)
            }.toList()
            val declaredModFiles = parsed.optJSONArray("modFiles") ?: JSONArray()
            val declaredLibraryFiles = parsed.optJSONArray("libraryFiles") ?: JSONArray()
            val declaresNativePayload = (0 until declaredModFiles.length()).any {
                declaredModFiles.optString(it).endsWith(".so", true)
            } || (0 until declaredLibraryFiles.length()).any {
                declaredLibraryFiles.optString(it).endsWith(".so", true)
            }
            if (nativeLibraries.isNotEmpty() && (declaresNativePayload || parsed.has("packageId"))) {
                parsed.put("format", "native-library")
            }
            // Inspect every native library before saving the imported mod.
            if (nativeLibraries.isNotEmpty()) {
                val reports = JSONArray()
                nativeLibraries.forEach { reports.put(inspectNativeLibrary(it)) }
                parsed.put("nativeLibraryInfo", reports)
                val abiCompatible = (0 until reports.length()).all { index ->
                    reports.optJSONObject(index)?.optBoolean("deviceAbiSupported", false) == true
                }
                parsed.put("nativeAbiCompatible", abiCompatible)
                val unityVersion = installedGorillaTagUnityVersion()
                if (unityVersion != null) parsed.put("installedGameUnityVersion", unityVersion)
                val note = if (abiCompatible) "ELF architecture matches this device." else
                    "One or more libraries do not match this device's supported ABI."
                parsed.put("nativeCompatibilityNote", "$note Unity/game API compatibility and a native loader are still required.")
                val oldDescription = parsed.optString("description")
                parsed.put("description", (oldDescription.takeIf { it.isNotBlank() }?.plus(" ") ?: "") +
                    "Native inspection: $note")
            }
            // Align declared Unity metadata with the Unity version actually shipped in the installed Gorilla Tag build.
            installedGorillaTagUnityVersion()?.let { gameUnityVersion ->
                listOf("unityVersion", "unity_version", "unity").forEach { key ->
                    if (parsed.has(key) && !parsed.isNull(key)) parsed.put(key, gameUnityVersion)
                }
            }
            parsed.writeTo(modJson ?: File(temp, "manifest.json"))
            val modName = parsed.optString("name").trim()
            require(modName.isNotEmpty()) { "The mod manifest must contain a name." }
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
            getSharedPreferences("qmod", MODE_PRIVATE).edit()
                .putString("lastGameVersionMatch", gameVersionPlan.toString()).apply()
            true to modName
        } catch (e: Exception) {
            temp.deleteRecursively()
            false to (e.message ?: "Import failed.")
        }
    }

    private fun downloadDependency(url: String, destination: File) {
        val connection = java.net.URL(url).openConnection().apply {
            connectTimeout = 12000
            readTimeout = 30000
            setRequestProperty("User-Agent", "GorillaRinth/0.1")
        }
        val http = connection as? java.net.HttpURLConnection
        try {
            if (http != null) require(http.responseCode in 200..299) { "HTTP " + http.responseCode }
            connection.getInputStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 128L * 1024 * 1024) { "QMOD exceeds the 128 MB download limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(destination.length() > 100) { "Downloaded file is empty or incomplete." }
        } finally {
            http?.disconnect()
        }
    }

    private fun isValidDependencyQmod(file: File, expectedId: String): Boolean {
        return try {
            var found = false
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && (entry.name.substringAfterLast('/') == "mod.json" ||
                                entry.name.substringAfterLast('/') == "manifest.json")) {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        val json = JSONObject(text)
                        val actualId = json.optString("id", json.optString("packageId", ""))
                        if (actualId.equals(expectedId, ignoreCase = true)) found = true
                    }
                    zip.closeEntry()
                }
            }
            found
        } catch (_: Exception) { false }
    }

    private fun discoverDependencyQmodUrls(id: String): List<String> {
        val results = LinkedHashSet<String>()
        val queries = listOf("\"$id\" Gorilla Tag Quest mod", "$id GorillaTag QMOD")
        for (query in queries) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val api = java.net.URL("https://api.github.com/search/repositories?q=" + encoded + "&per_page=8")
                val connection = api.openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "GorillaRinth/0.1")
                }
                val json = connection.getInputStream().bufferedReader().use { JSONObject(it.readText()) }
                val repos = json.optJSONArray("items") ?: continue
                for (i in 0 until minOf(repos.length(), 8)) {
                    val repo = repos.optJSONObject(i) ?: continue
                    val fullName = repo.optString("full_name")
                    val branch = repo.optString("default_branch", "main")
                    if (fullName.isBlank()) continue
                    // Check release assets first.
                    try {
                        val releasesUrl = java.net.URL("https://api.github.com/repos/" + fullName + "/releases?per_page=10")
                        val relConn = releasesUrl.openConnection().apply {
                            connectTimeout = 8000
                            readTimeout = 12000
                            setRequestProperty("User-Agent", "GorillaRinth/0.1")
                        }
                        val releases = org.json.JSONArray(relConn.getInputStream().bufferedReader().use { it.readText() })
                        for (r in 0 until releases.length()) {
                            val assets = releases.optJSONObject(r)?.optJSONArray("assets") ?: continue
                            for (a in 0 until assets.length()) {
                                val asset = assets.optJSONObject(a) ?: continue
                                val name = asset.optString("name")
                                val downloadUrl = asset.optString("browser_download_url")
                                if (name.endsWith(".qmod", true) && downloadUrl.startsWith("https://")) results.add(downloadUrl)
                            }
                        }
                    } catch (_: Exception) { }
                    // Also inspect repository files for directly hosted QMOD archives.
                    try {
                        val treeUrl = java.net.URL("https://api.github.com/repos/" + fullName + "/git/trees/" + branch + "?recursive=1")
                        val treeConn = treeUrl.openConnection().apply {
                            connectTimeout = 8000
                            readTimeout = 12000
                            setRequestProperty("User-Agent", "GorillaRinth/0.1")
                        }
                        val tree = JSONObject(treeConn.getInputStream().bufferedReader().use { it.readText() })
                        val nodes = tree.optJSONArray("tree") ?: continue
                        for (n in 0 until nodes.length()) {
                            val path = nodes.optJSONObject(n)?.optString("path") ?: continue
                            if (path.endsWith(".qmod", true)) {
                                results.add("https://raw.githubusercontent.com/" + fullName + "/" + branch + "/" +
                                    path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") })
                            }
                        }
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
            if (results.size >= 30) break
        }
        return results.toList()
    }

    private fun resolveQmodDependencies(root: File, manifest: JSONObject): JSONArray {
        val dependencies = manifest.optJSONArray("dependencies") ?: return JSONArray()
        val report = JSONArray()
        val dependencyRoot = File(root, "resolved_dependencies")
        for (index in 0 until dependencies.length()) {
            val dependency = dependencies.optJSONObject(index) ?: continue
            val id = dependency.optString("id").trim()
            val download = dependency.optString("downloadIfMissing").trim()
            if (id.isBlank() || download.isBlank()) continue
            val url = Regex("\\[([^]]+)]\\((https?://[^)]+)\\)").find(download)?.groupValues?.get(2) ?: download
            require(url.startsWith("https://") || url.startsWith("http://")) {
                "Dependency " + id + " has an invalid download URL."
            }
            val depDir = File(dependencyRoot, id)
            if (!depDir.exists()) {
                depDir.mkdirs()
                val archive = File(depDir, "dependency.qmod")
                val fallbackUrls = when (id.lowercase()) {
                    "monkecodegen" -> listOf("https://github.com/GalaxyOnVR/MonkeCodegen-and-Utils/releases/download/1.1.2/monkecodgen.qmod")
                    "monkecomputer" -> listOf("https://raw.githubusercontent.com/GalaxyOnVR/MonkeComputer/main/MonkeComputer%20(5).qmod")
                    else -> emptyList()
                }
                var downloaded = false
                var lastError: String? = null
                // Try the manifest URL and known mirrors. A 200 response is not enough:
                // verify that the file is a readable QMOD whose manifest matches this dependency.
                for (candidate in (listOf(url) + fallbackUrls).distinct()) {
                    try {
                        downloadDependency(candidate, archive)
                        require(isValidDependencyQmod(archive, id)) { "Downloaded file is not a valid QMOD for " + id }
                        downloaded = true
                        break
                    } catch (e: Exception) {
                        archive.delete()
                        lastError = e.message
                    }
                }
                // If all supplied URLs fail, discover public repositories/releases online.
                if (!downloaded) {
                    for (candidate in discoverDependencyQmodUrls(id)) {
                        try {
                            downloadDependency(candidate, archive)
                            require(isValidDependencyQmod(archive, id)) { "Discovered file is not a valid QMOD for " + id }
                            downloaded = true
                            break
                        } catch (e: Exception) {
                            archive.delete()
                            lastError = e.message
                        }
                    }
                }
                check(downloaded) {
                    "Could not find a working QMOD for dependency " + id +
                        (lastError?.let { ": " + it } ?: "")
                }
                val extracted = File(depDir, "contents").apply { mkdirs() }
                ZipInputStream(archive.inputStream().buffered()).use { zip ->
                    var entries = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entries++
                        require(entries <= 500) { "Dependency " + id + " contains too many files." }
                        val out = File(extracted, entry.name)
                        val base = extracted.canonicalPath + File.separator
                        require(out.canonicalPath.startsWith(base) || out.canonicalPath == extracted.canonicalPath) {
                            "Dependency " + id + " contains an unsafe archive path."
                        }
                        if (entry.isDirectory) out.mkdirs() else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { sink -> zip.copyTo(sink) }
                        }
                        zip.closeEntry()
                    }
                }
                archive.delete()
            }
            val depManifestFile = depDir.walkTopDown().firstOrNull {
                it.isFile && it.name.equals("mod.json", true)
            } ?: error("Dependency " + id + " downloaded, but its mod.json is missing.")
            val depManifest = JSONObject(depManifestFile.readText())
            val depId = depManifest.optString("id", id)
            require(depId.equals(id, true)) {
                "Dependency URL for " + id + " returned mod " + depManifest.optString("id") + "."
            }
            val payloadNames = JSONArray()
            listOf("modFiles", "libraryFiles").forEach { key ->
                val files = depManifest.optJSONArray(key) ?: return@forEach
                for (i in 0 until files.length()) payloadNames.put(files.optString(i))
            }
            val copied = JSONArray()
            for (i in 0 until payloadNames.length()) {
                val relative = payloadNames.optString(i)
                if (!relative.endsWith(".so", true)) continue
                val source = depManifestFile.parentFile?.let { File(it, relative) }
                    ?: error("Dependency " + id + " has an invalid manifest location.")
                require(source.isFile) { "Dependency " + id + " is missing native file " + relative + "." }
                val destination = File(root, "dependency_native/" + id + "/" + source.name)
                destination.parentFile?.mkdirs()
                source.copyTo(destination, overwrite = true)
                copied.put(destination.relativeTo(root).invariantSeparatorsPath)
            }
            report.put(JSONObject().put("id", id).put("version", depManifest.optString("version"))
                .put("nativeFiles", copied))
        }
        return report
    }

    private fun inspectNativeLibrary(file: File): JSONObject {
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= 64 * 1024 * 1024) { "Native library is too large to inspect (64 MB limit)." }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
        val validElf = bytes.size >= 20 && bytes[0] == 0x7f.toByte() &&
            bytes[1] == 0x45.toByte() && bytes[2] == 0x4c.toByte() && bytes[3] == 0x46.toByte()
        if (!validElf) return JSONObject()
            .put("file", file.name).put("isElf", false)
            .put("deviceAbiSupported", false).put("error", "Not a valid ELF shared library.")
        val elfClass = bytes[4].toInt() and 0xff
        val littleEndian = (bytes[5].toInt() and 0xff) == 1
        val machine = if (littleEndian) {
            (bytes[18].toInt() and 0xff) or ((bytes[19].toInt() and 0xff) shl 8)
        } else {
            ((bytes[18].toInt() and 0xff) shl 8) or (bytes[19].toInt() and 0xff)
        }
        val abi = when (machine) {
            183 -> if (elfClass == 2) "arm64-v8a" else "unknown-arm"
            40 -> "armeabi-v7a"
            62 -> "x86_64"
            3 -> "x86"
            else -> "unknown"
        }
        val printable = String(bytes, Charsets.ISO_8859_1)
        val dependencies = Regex("lib[A-Za-z0-9_.+-]+\\.so")
            .findAll(printable).map { it.value }.distinct().toList()
        val supported = abi in android.os.Build.SUPPORTED_ABIS
        return JSONObject()
            .put("file", file.name).put("isElf", true)
            .put("elfClass", if (elfClass == 2) "64-bit" else if (elfClass == 1) "32-bit" else "unknown")
            .put("machine", machine).put("abi", abi)
            .put("deviceAbiSupported", supported)
            .put("unityNative", dependencies.any { it == "libunity.so" || it == "libil2cpp.so" })
            .put("dependencies", JSONArray(dependencies))
    }

    private fun installedGorillaTagUnityVersion(): String? {
        return runCatching {
            val gameContext = createPackageContext(
                "com.AnotherAxiom.GorillaTag",
                android.content.Context.CONTEXT_IGNORE_SECURITY
            )
            gameContext.assets.open("bin/Data/globalgamemanagers").use { input ->
                val header = ByteArray(128)
                val count = input.read(header)
                if (count <= 0) return@runCatching null
                val text = String(header, 0, count, Charsets.UTF_8)
                Regex("20\\d{2}\\.\\d+\\.\\d+[abfp]\\d+").find(text)?.value
            }
        }.getOrNull()
    }

    private fun JSONObject.writeTo(file: File) {
        file.writeText(toString(2))
    }

    private fun findManifestFile(): File? {
        val root = File(filesDir, "imported_mod")
        if (!root.isDirectory) return null
        return root.walkTopDown().firstOrNull { it.isFile && it.name.equals("mod.json", true) }
            ?: root.walkTopDown().firstOrNull { it.isFile && it.name.equals("manifest.json", true) }
    }

    private fun coverDataUri(): String? {
        val root = File(filesDir, "imported_mod")
        val cover = root.walkTopDown().firstOrNull {
            it.isFile && (it.name.equals("cover.png", true) || it.name.equals("cover.jpg", true) ||
                it.name.equals("cover.jpeg", true))
        } ?: return null
        if (cover.length() <= 0L || cover.length() > 2L * 1024 * 1024) return null
        val mime = if (cover.extension.equals("png", true)) "image/png" else "image/jpeg"
        return "data:$mime;base64," + Base64.encodeToString(cover.readBytes(), Base64.NO_WRAP)
    }

    private fun readManifest(): JSONObject? {
        val file = findManifestFile() ?: return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }
}
