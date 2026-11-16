package com.gorillarinth.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
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
        web.loadUrl("file:///android_asset/driftrinth/index.html")
    }

    private fun complete(id: Int, ok: Boolean, value: String) {
        val encoded = if (ok) value else JSONObject.quote(value)
        runOnUiThread { web.evaluateJavascript("window.__drDone($id, $ok, $encoded)", null) }
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
                .put("added", System.currentTimeMillis())
            library.put(mod)
        }
        val gameIntent = packageManager.getLaunchIntentForPackage("com.AnotherAxiom.GorillaTag")
        val game = if (gameIntent == null) JSONObject.NULL else {
            val info = packageManager.getPackageInfo("com.AnotherAxiom.GorillaTag", 0)
            JSONObject().put("versionName", info.versionName ?: "Unknown")
                .put("versionCode", info.versionCode.toLong()).put("running", false)
        }
        return JSONObject()
            .put("library", library)
            .put("device", JSONObject().put("state", "none").put("paired", false).put("autoReconnect", false).put("model", "Quest"))
            .put("game", game)
            .put("base", JSONObject().put("usable", false).put("fromModded", false).put("name", ""))
            .put("checks", JSONArray())
            .put("busy", JSONObject.NULL)
            .put("appliedAt", JSONObject.NULL)
            .put("capacity", 0).put("used", 0)
            .put("pending", false).put("canApply", false)
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
                            val developerSettings = Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
                                .setPackage("com.android.settings")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            val androidSettings = Intent(android.provider.Settings.ACTION_SETTINGS)
                                .setPackage("com.android.settings")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                if (developerSettings.resolveActivity(packageManager) != null) {
                                    startActivity(developerSettings)
                                } else if (androidSettings.resolveActivity(packageManager) != null) {
                                    startActivity(androidSettings)
                                } else {
                                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                }
                            } catch (_: Exception) {
                                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                            }
                        }
                        complete(id, true, "true")
                    }
                    "pairingPort" -> complete(id, true, "0")
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
                        File(filesDir, "imported_mod").deleteRecursively()
                        getSharedPreferences("mods", MODE_PRIVATE).edit().clear().apply()
                        complete(id, true, "true")
                    }
                    "setAutoReconnect" -> complete(id, true, "false")
                    "reconnect" -> complete(id, true, "false")
                    "storeConfig" -> complete(id, true, JSONObject().put("url", "").put("configured", false).toString())
                    "pair", "pullBase", "chooseBase", "restoreBase", "stopGame", "apply", "storeSetUrl", "storeInstall" ->
                        throw UnsupportedOperationException(when (method) {
                            "pair" -> "Wireless ADB pairing is not implemented yet."
                            "apply" -> "Game-side mod installation is not implemented yet. Imported files are only inspected."
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
            if (root.exists()) root.deleteRecursively()
            if (!temp.renameTo(root)) error("Could not save imported archive.")
            val modJson = root.walkTopDown().firstOrNull { it.isFile && it.name.equals("mod.json", true) }
                ?: error("Archive extracted, but no mod.json manifest was found.")
            importedManifest = JSONObject(modJson.readText())
            true to importedManifest?.optString("name", "Imported mod").orEmpty()
        } catch (e: Exception) {
            temp.deleteRecursively()
            false to (e.message ?: "Import failed.")
        }
    }

    private fun readManifest(): JSONObject? {
        val root = File(filesDir, "imported_mod")
        val file = root.walkTopDown().firstOrNull { it.isFile && it.name.equals("mod.json", true) } ?: return null
        return runCatching { JSONObject(file.readText()) }.getOrNull()
    }
}
