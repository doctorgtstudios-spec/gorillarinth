package com.gorillarinth.app

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Bundle
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AppTheme(val name: String, val accent: Color, val dark: Boolean)
private val themes = listOf(
    AppTheme("Gorilla Yellow", Color(0xFFFFD21F), true),
    AppTheme("Green", Color(0xFF54D68B), true),
    AppTheme("Blue", Color(0xFF62A9FF), true),
    AppTheme("Purple", Color(0xFFB58AFF), true),
    AppTheme("Dark", Color(0xFFB7BBC5), true),
    AppTheme("Light", Color(0xFFB18A00), false)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GorillaRinthApp() }
    }
}

@Composable
private fun GorillaRinthApp() {
    var page by remember { mutableStateOf("Home") }
    var selectedTheme by remember { mutableStateOf(themes.first()) }
    var archiveName by remember { mutableStateOf<String?>(null) }
    var archiveUri by remember { mutableStateOf<Uri?>(null) }
    var importStatus by remember { mutableStateOf("No archive imported yet.") }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pairingCode by remember { mutableStateOf("") }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val name = context.contentResolver.query(
                it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: it.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
            if (name.endsWith(".zip", ignoreCase = true)) {
                archiveName = name
                archiveUri = it
                importStatus = "ZIP selected. Ready to inspect and extract."
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } else {
                archiveName = null
                archiveUri = null
                importStatus = "Please select a .zip archive."
            }
        }
    }
    val bg = if (selectedTheme.dark) Color(0xFF111318) else Color(0xFFF4F4F0)
    val panel = if (selectedTheme.dark) Color(0xFF1B1F26) else Color.White
    val fg = if (selectedTheme.dark) Color(0xFFF4F4F4) else Color(0xFF171717)
    val muted = if (selectedTheme.dark) Color(0xFF9AA1AD) else Color(0xFF666666)

    MaterialTheme(
        colorScheme = if (selectedTheme.dark)
            darkColorScheme(primary = selectedTheme.accent, background = bg, surface = panel, onPrimary = Color(0xFF171717), onBackground = fg, onSurface = fg)
        else lightColorScheme(primary = selectedTheme.accent, background = bg, surface = panel, onPrimary = Color.White, onBackground = fg, onSurface = fg)
    ) {
        Scaffold(
            containerColor = bg,
            bottomBar = {
                NavigationBar(containerColor = panel, tonalElevation = 0.dp) {
                    listOf(
                        Triple("Home", Icons.Filled.Home, "Home"),
                        Triple("Mods", Icons.Filled.Extension, "Mods"),
                        Triple("Patcher", Icons.Filled.Build, "Patcher"),
                        Triple("Themes", Icons.Filled.Palette, "Themes"),
                        Triple("Settings", Icons.Filled.Settings, "Settings")
                    ).forEach { (label, icon, _) ->
                        NavigationBarItem(
                            selected = page == label,
                            onClick = { page = label },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = selectedTheme.accent, indicatorColor = selectedTheme.accent.copy(alpha = .16f))
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().background(bg).padding(padding).padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(selectedTheme.accent, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Text("G", color = Color(0xFF171717), fontWeight = FontWeight.Black, fontSize = 25.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("GorillaRinth", color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("QUEST MOD MANAGER", color = muted, fontSize = 9.sp, letterSpacing = 2.sp)
                    }
                }
                when (page) {
                    "Home" -> {
                        Text("Welcome back.", color = fg, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Your Gorilla Tag mod workspace.", color = muted)
                        Spacer(Modifier.height(20.dp))
                        CardBlock(panel, selectedTheme.accent) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Wifi, null, tint = selectedTheme.accent)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Quest connection", color = fg, fontWeight = FontWeight.Bold)
                                    Text("Not connected", color = muted, fontSize = 13.sp)
                                }
                                StatusPill("OFFLINE", selectedTheme.accent)
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { page = "Settings" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.accent, contentColor = Color(0xFF171717))) { Text("Set up wireless debugging") }
                        }
                        Spacer(Modifier.height(14.dp))
                        CardBlock(panel, selectedTheme.accent) {
                            Text("Quick actions", color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.height(12.dp))
                            ActionRow("Patch OBB", "Create a backup and prepare the game files", Icons.Filled.Build, selectedTheme.accent) { page = "Patcher" }
                            ActionRow("Choose a ZIP", archiveName ?: "Select one mod archive", Icons.Filled.FileOpen, selectedTheme.accent) { page = "Mods" }
                            ActionRow("Launch Gorilla Tag", "Open the installed game", Icons.Filled.PlayArrow, selectedTheme.accent) { launchGorillaTag(context) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("ZIP import is available. Game launch opens the installed Gorilla Tag app; mod installation and patching are not active yet.", color = muted, fontSize = 11.sp)
                    }
                    "Mods" -> {
                        Text("Your mods", color = fg, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("One ZIP archive per game session.", color = muted)
                        Spacer(Modifier.height(18.dp))
                        CardBlock(panel, selectedTheme.accent) {
                            Icon(Icons.Filled.Extension, tint = selectedTheme.accent, contentDescription = null, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Select a ZIP archive", color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Choose one .zip mod archive from your device.", color = muted, fontSize = 13.sp)
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.accent, contentColor = Color(0xFF171717))) {
                                Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Browse ZIP archives")
                            }
                            archiveName?.let { Text("Selected: $it", color = selectedTheme.accent, modifier = Modifier.padding(top = 10.dp)) }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val uri = archiveUri ?: return@Button
                                    importing = true
                                    importStatus = "Inspecting archive…"
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { extractModArchive(context, uri) }
                                        importStatus = result
                                        importing = false
                                    }
                                },
                                enabled = archiveUri != null && !importing,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (importing) "Importing…" else "Inspect & extract ZIP") }
                            Text(importStatus, color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("ZIP contents are extracted into GorillaRinth's private app storage. This does not install or load them into Gorilla Tag; the archive layout and compatible loader must be verified first.", color = muted, fontSize = 12.sp)
                    }
                    "Patcher" -> {
                        Text("OBB patcher", color = fg, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Backup first. Never overwrite the original.", color = muted)
                        Spacer(Modifier.height(18.dp))
                        CardBlock(panel, selectedTheme.accent) {
                            Text("Patch status", color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth(), color = selectedTheme.accent, trackColor = muted.copy(alpha = .2f))
                            Text("Waiting for Quest connection", color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 9.dp))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { }, enabled = false, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.accent, disabledContainerColor = muted.copy(alpha = .2f))) { Text("Patch OBB (not configured)") }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("OBB patching is intentionally disabled until the exact Driftrinth patch procedure and a compatible, authorized mod-loader format are verified.", color = muted, fontSize = 12.sp)
                    }
                    "Themes" -> {
                        Text("Appearance", color = fg, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Choose your GorillaRinth accent and mode.", color = muted)
                        Spacer(Modifier.height(18.dp))
                        themes.forEach { theme ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { selectedTheme = theme },
                                colors = CardDefaults.cardColors(containerColor = panel),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(28.dp).background(theme.accent, RoundedCornerShape(9.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Text(theme.name, color = fg, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    if (selectedTheme.name == theme.name) Icon(Icons.Filled.CheckCircle, null, tint = selectedTheme.accent)
                                }
                            }
                        }
                    }
                    else -> {
                        Text("Settings", color = fg, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Quest connection setup", color = muted)
                        Spacer(Modifier.height(18.dp))
                        CardBlock(panel, selectedTheme.accent) {
                            Text("Wireless debugging", color = fg, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Pairing-code connection is planned. Enable Developer Mode and Wireless Debugging on your Quest, then enter the pairing details here once the ADB pairing component is added.", color = muted, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = pairingCode, onValueChange = { pairingCode = it }, label = { Text("Pairing code (UI preview)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text("Connection is not active in this prototype.", color = muted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        CardBlock(panel, selectedTheme.accent) {
                            Text("About GorillaRinth", color = fg, fontWeight = FontWeight.Bold)
                            Text("Version 0.1.0 • Early UI prototype", color = muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardBlock(panel: Color, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun StatusPill(label: String, accent: Color) {
    Surface(color = accent.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 11.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
    }
}

private suspend fun extractModArchive(context: android.content.Context, uri: Uri): String {
    val root = File(context.filesDir, "imported_mod")
    val temp = File(context.filesDir, "imported_mod_tmp")
    return try {
        if (temp.exists()) temp.deleteRecursively()
        if (!temp.mkdirs()) return "Could not create the temporary import folder."
        var entries = 0
        var totalBytes = 0L
        var files = 0
        val canonicalRoot = temp.canonicalPath + File.separator
        val input = context.contentResolver.openInputStream(uri)
            ?: return "Could not open the selected ZIP archive."
        input.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    if (entries > 500) throw IllegalArgumentException("Archive contains too many entries (limit: 500).")
                    val output = File(temp, entry.name)
                    val canonicalOutput = output.canonicalPath
                    if (!canonicalOutput.startsWith(canonicalRoot) && canonicalOutput != temp.canonicalPath) {
                        throw IllegalArgumentException("Unsafe ZIP path detected: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        if (!output.exists() && !output.mkdirs()) throw IllegalStateException("Could not create an archive folder.")
                    } else {
                        output.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IllegalStateException("Could not create an archive folder.") }
                        FileOutputStream(output).use { sink ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                if (totalBytes > 256L * 1024L * 1024L) {
                                    throw IllegalArgumentException("Extracted data exceeds the 256 MB safety limit.")
                                }
                                sink.write(buffer, 0, count)
                            }
                        }
                        files++
                    }
                    zip.closeEntry()
                }
            }
        }
        if (files == 0) throw IllegalArgumentException("The ZIP archive contains no files.")
        if (root.exists() && !root.deleteRecursively()) throw IllegalStateException("Could not replace the previous imported archive.")
        if (!temp.renameTo(root)) throw IllegalStateException("Could not finalize the extracted archive.")
        "Imported successfully: $files files extracted (${totalBytes / 1024} KB). Stored privately in GorillaRinth."
    } catch (e: Exception) {
        temp.deleteRecursively()
        "Import failed: ${e.message ?: "Unknown ZIP error"}"
    }
}

 
private fun launchGorillaTag(context: android.content.Context) {
    val packageName = "com.AnotherAxiom.GorillaTag"
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent == null) {
        Toast.makeText(context, "Gorilla Tag is not installed or cannot be launched.", Toast.LENGTH_LONG).show()
        return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Could not launch Gorilla Tag: ${it.message}", Toast.LENGTH_LONG).show() }
}
