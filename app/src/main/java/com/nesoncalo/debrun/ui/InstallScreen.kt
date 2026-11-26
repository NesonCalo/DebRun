package com.nesoncalo.debrun.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nesoncalo.debrun.R
import com.nesoncalo.debrun.core.DebInfo
import com.nesoncalo.debrun.core.DebParser
import com.nesoncalo.debrun.core.Env
import com.nesoncalo.debrun.core.Installer
import com.nesoncalo.debrun.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun InstallScreen(ready: Boolean, onNeedSetup: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf<String?>(null) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<DebInfo?>(null) }
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf<Boolean?>(null) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var fixDeps by remember { mutableStateOf(Prefs.autoFixDeps) }
    val cancel = remember { AtomicBoolean(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected == null) return@rememberLauncherForActivityResult
        uri = selected
        finished = null
        logLines = emptyList()
        fileName = displayName(context, selected)
        scope.launch(Dispatchers.IO) {
            val cached = copyToCache(context, selected)
            info = cached?.let { DebParser.parse(it) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.install_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.install_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (!ready) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.hint_need_setup), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onNeedSetup) { Text(stringResource(R.string.btn_setup)) }
                }
            }
            return@Column
        }

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.install_pick))
        }

        Spacer(Modifier.height(12.dp))

        fileName?.let { name ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(name, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    if (info == null) {
                        Text(
                            stringResource(R.string.install_meta_empty),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        MetaRow(stringResource(R.string.install_pkg), info?.packageName.orEmpty())
                        MetaRow(stringResource(R.string.install_version), info?.version.orEmpty())
                        MetaRow(stringResource(R.string.install_arch), info?.architecture.orEmpty())
                        MetaRow(
                            stringResource(R.string.install_size),
                            Env.humanSize(info?.installedSize ?: 0)
                        )
                        if (!info?.depends.isNullOrBlank()) {
                            MetaRow(stringResource(R.string.install_deps), info?.depends.orEmpty())
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = fixDeps,
                onCheckedChange = {
                    fixDeps = it
                    Prefs.autoFixDeps = it
                }
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.install_fix_deps), fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val selected = uri ?: return@Button
                    running = true
                    finished = null
                    logLines = emptyList()
                    cancel.set(false)
                    val name = fileName ?: "package.deb"
                    scope.launch(Dispatchers.IO) {
                        Installer.install(
                            context = context,
                            uri = selected,
                            displayName = name,
                            fixDeps = fixDeps,
                            dns = Prefs.dns,
                            cancel = cancel,
                            onLine = { line -> logLines = (logLines + line).takeLast(600) },
                            onDone = { outcome ->
                                finished = outcome.success
                                running = false
                            }
                        )
                    }
                },
                enabled = uri != null && !running,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(if (running) R.string.install_running else R.string.install_start))
            }
            if (running) {
                OutlinedButton(onClick = { cancel.set(true) }) {
                    Text(stringResource(R.string.setup_cancel))
                }
            }
        }

        finished?.let { ok ->
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (ok) R.string.install_ok else R.string.install_fail),
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (running) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (logLines.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.install_log), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    logLines.takeLast(120).forEach {
                        Text(
                            it,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.width(110.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}

fun displayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: "package.deb"
            }
        }
    } catch (_: Throwable) {
    }
    return uri.lastPathSegment ?: "package.deb"
}

private fun copyToCache(context: Context, uri: Uri): File? {
    return try {
        val target = File(context.cacheDir, "preview.deb")
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return null
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    } catch (_: Throwable) {
        null
    }
}
