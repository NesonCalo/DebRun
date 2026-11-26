package com.nesoncalo.debrun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nesoncalo.debrun.R
import com.nesoncalo.debrun.core.Env
import com.nesoncalo.debrun.core.Prefs
import com.nesoncalo.debrun.core.Proot
import com.nesoncalo.debrun.core.RootfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var ready by remember { mutableStateOf(Env.isRootfsReady()) }
    var busy by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf<String?>(null) }
    var percent by remember { mutableStateOf(-1) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(AboutPage.NONE) }
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val cancelFlag = remember { AtomicBoolean(false) }

    fun addLog(line: String) {
        if (line.isBlank()) return
        logLines = (logLines + line).takeLast(300)
    }

    fun handleEvent(event: RootfsManager.Event) {
        when (event) {
            is RootfsManager.Event.Log -> addLog(event.text)
            is RootfsManager.Event.Progress -> percent = event.percent
            is RootfsManager.Event.Phase -> {
                phase = when (event.title) {
                    "download" -> context.getString(R.string.progress_download)
                    "extract" -> context.getString(R.string.progress_extract)
                    "configure" -> context.getString(R.string.progress_configure)
                    "done" -> context.getString(R.string.progress_done)
                    else -> null
                }
                if (!event.indeterminate) percent = 0
            }
            is RootfsManager.Event.Failure -> addLog("FAILED: ${event.message}")
            RootfsManager.Event.Done -> phase = null
        }
    }

    fun startSetup(reinstall: Boolean) {
        if (busy) return
        busy = true
        percent = -1
        logLines = emptyList()
        cancelFlag.set(false)
        scope.launch(Dispatchers.IO) {
            try {
                if (reinstall) {
                    addLog("removing previous rootfs…")
                    RootfsManager.wipeRootfs()
                }
                addLog("preparing proot for ${Env.primaryAbi()}")
                if (!Proot.ensureInstalled()) {
                    addLog("cannot extract proot runtime from assets")
                }
                val archive = File(Env.filesDir, "rootfs-download.tar")
                val downloaded = RootfsManager.downloadArchive(
                    Prefs.distroId, archive, cancelFlag
                ) { event -> handleEvent(event) }
                if (downloaded != null) {
                    val extracted = RootfsManager.extractArchive(
                        downloaded, Env.rootfsDir, cancelFlag
                    ) { event -> handleEvent(event) }
                    archive.delete()
                    if (extracted) {
                        RootfsManager.configure(
                            Prefs.distroId, Prefs.dns, Prefs.useChinaMirror
                        ) { event -> handleEvent(event) }
                        ready = Env.isRootfsReady()
                        if (ready) bootstrapExtraTools(::addLog)
                    } else {
                        addLog("extraction failed")
                    }
                }
            } catch (t: Throwable) {
                addLog("error: ${t.message}")
            } finally {
                busy = false
                phase = null
                percent = -1
                ready = Env.isRootfsReady()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        NavItem(0, tab, R.string.tab_home, Icons.Filled.Home) { tab = 0 }
                        NavItem(1, tab, R.string.tab_install, Icons.Filled.Add) { tab = 1 }
                        NavItem(2, tab, R.string.tab_terminal, Icons.Filled.Terminal) { tab = 2 }
                        NavItem(3, tab, R.string.tab_apps, Icons.Filled.Apps) { tab = 3 }
                        NavItem(4, tab, R.string.tab_settings, Icons.Filled.Settings) { tab = 4 }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(
                    ready = ready,
                    busy = busy,
                    phase = phase,
                    percent = percent,
                    logs = logLines,
                    onSetup = { startSetup(ready) },
                    onCancel = { cancelFlag.set(true) },
                    onReset = { showResetDialog = true },
                    onTerminal = { tab = 2 }
                )
                1 -> InstallScreen(ready = ready, onNeedSetup = { tab = 0 })
                2 -> TerminalScreen(
                    ready = ready,
                    pendingCommand = pendingCommand,
                    onCommandConsumed = { pendingCommand = null },
                    onNeedSetup = { tab = 0 }
                )
                3 -> AppsScreen(
                    ready = ready,
                    onNeedSetup = { tab = 0 },
                    onRun = { command ->
                        pendingCommand = command
                        tab = 2
                    }
                )
                4 -> SettingsScreen(
                    onOpenAbout = { showAbout = AboutPage.ABOUT },
                    onOpenLicenses = { showAbout = AboutPage.LICENSES },
                    onOpenTerms = { showAbout = AboutPage.TERMS },
                    onReset = { showResetDialog = true },
                    ready = ready
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    RootfsManager.wipeRootfs()
                    ready = false
                    startSetup(false)
                }) { Text(stringResource(R.string.delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    when (showAbout) {
        AboutPage.ABOUT -> InfoDialog(
            title = stringResource(R.string.settings_about),
            body = stringResource(R.string.about_body),
            onClose = { showAbout = AboutPage.NONE }
        )
        AboutPage.LICENSES -> InfoDialog(
            title = stringResource(R.string.settings_licenses),
            body = stringResource(R.string.license_body),
            onClose = { showAbout = AboutPage.NONE }
        )
        AboutPage.TERMS -> InfoDialog(
            title = stringResource(R.string.settings_terms),
            body = stringResource(R.string.terms_body),
            onClose = { showAbout = AboutPage.NONE }
        )
        AboutPage.NONE -> Unit
    }
}

private fun bootstrapExtraTools(addLog: (String) -> Unit) {
    val script = "echo \"probe: $(uname -m) uid=$(id -u)\"; " +
        "apt-get update -qq >/dev/null 2>&1; " +
        "apt-get install -y --no-install-recommends bsdextrautils procps >/dev/null 2>&1; " +
        "echo 'tools:' ; command -v script; command -v ps"
    try {
        val process = Proot.startProcess(Proot.shellCommand(script, Prefs.dns))
        process.inputStream.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                addLog(line)
            }
        }
        addLog("bootstrap exit=${process.waitFor()}")
    } catch (t: Throwable) {
        addLog("bootstrap skipped: ${t.message}")
    }
}

@Composable
private fun RowScope.NavItem(
    index: Int,
    selected: Int,
    labelRes: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val active = selected == index
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            stringResource(labelRes),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = tint
        )
    }
}

enum class AboutPage { NONE, ABOUT, LICENSES, TERMS }

@Composable
fun InfoDialog(title: String, body: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
            ) { Text(body, fontSize = 13.sp) }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_ok)) }
        }
    )
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            modifier = Modifier.width(120.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun HomeScreen(
    ready: Boolean,
    busy: Boolean,
    phase: String?,
    percent: Int,
    logs: List<String>,
    onSetup: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onTerminal: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.home_subtitle),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(
                                if (ready) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                RoundedCornerShape(50)
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (ready) R.string.env_ready else R.string.env_missing),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(Env.primaryAbi(), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(12.dp))
                StatusRow(stringResource(R.string.env_distro), RootfsManager.label(Prefs.distroId))
                StatusRow(stringResource(R.string.env_arch), Env.arch())
                StatusRow(stringResource(R.string.env_size), Env.humanSize(Env.rootfsSizeBytes()))
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        if (busy) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(phase ?: stringResource(R.string.setup_running), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    if (percent >= 0) {
                        LinearProgressIndicator(progress = percent / 100f, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("$percent%", fontSize = 11.sp)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .height(140.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        logs.takeLast(60).forEach {
                            Text(
                                it,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.setup_cancel)) }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!ready) {
                    Button(onClick = onSetup, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_setup))
                    }
                } else {
                    Button(onClick = onTerminal, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_terminal))
                    }
                    OutlinedButton(onClick = onReset) {
                        Text(stringResource(R.string.btn_reset))
                    }
                }
            }
            if (ready && logs.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.hint_ready),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
