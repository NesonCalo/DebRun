package com.nesoncalo.debrun.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.nesoncalo.debrun.core.AppsRepository
import com.nesoncalo.debrun.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(ready: Boolean, onNeedSetup: () -> Unit, onRun: (String) -> Unit) {
    var packages by remember { mutableStateOf<List<AppsRepository.Pkg>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<AppsRepository.Pkg?>(null) }
    var command by remember { mutableStateOf("") }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(ready, reloadKey) {
        if (!ready) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        withContext(Dispatchers.IO) {
            AppsRepository.load(Prefs.dns) { list -> packages = list }
        }
        loading = false
    }

    if (!ready) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.hint_need_setup), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNeedSetup) { Text(stringResource(R.string.btn_setup)) }
        }
        return
    }

    val filtered = packages.filter {
        query.isBlank() || it.name.contains(query, true) || it.summary.contains(query, true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.apps_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.apps_count, packages.size),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { reloadKey++ }) { Text(stringResource(R.string.apps_refresh)) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.apps_search)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            filtered.isEmpty() -> Text(
                stringResource(R.string.apps_empty),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(filtered) { pkg ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = pkg
                                command = pkg.name
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Row {
                            Text(
                                pkg.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                pkg.version,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (pkg.summary.isNotBlank()) {
                            Text(
                                pkg.summary,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }

    selected?.let { pkg ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.apps_run_title)) },
            text = {
                Column {
                    Text(pkg.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.apps_run_hint)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cmd = command.trim()
                    selected = null
                    if (cmd.isNotEmpty()) onRun(cmd)
                }) { Text(stringResource(R.string.apps_run)) }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
