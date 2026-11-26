package com.nesoncalo.debrun.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nesoncalo.debrun.BuildConfig
import com.nesoncalo.debrun.R
import com.nesoncalo.debrun.core.Prefs
import com.nesoncalo.debrun.core.RootfsManager

private data class LangOption(val tag: String?, val label: String)

private val LANGUAGE_OPTIONS = listOf(
    LangOption(null, "System default"),
    LangOption("en", "English"),
    LangOption("zh-CN", "简体中文"),
    LangOption("zh-TW", "繁體中文（台灣）"),
    LangOption("zh-HK", "繁體中文（香港）"),
    LangOption("ja", "日本語"),
    LangOption("ko", "한국어"),
    LangOption("ru", "Русский"),
    LangOption("de", "Deutsch"),
    LangOption("fr", "Français"),
    LangOption("es", "Español"),
    LangOption("pt-BR", "Português (Brasil)"),
    LangOption("it", "Italiano"),
    LangOption("ar", "العربية"),
    LangOption("tr", "Türkçe"),
    LangOption("vi", "Tiếng Việt"),
    LangOption("th", "ไทย"),
    LangOption("id", "Bahasa Indonesia"),
    LangOption("ms", "Bahasa Melayu"),
    LangOption("hi", "हिन्दी"),
    LangOption("fa", "فارسی"),
    LangOption("pl", "Polski"),
    LangOption("nl", "Nederlands"),
    LangOption("uk", "Українська"),
    LangOption("cs", "Čeština"),
    LangOption("sv", "Svenska"),
    LangOption("da", "Dansk"),
    LangOption("nb", "Norsk bokmål"),
    LangOption("fi", "Suomi"),
    LangOption("el", "Ελληνικά"),
    LangOption("he", "עברית"),
    LangOption("hu", "Magyar"),
    LangOption("ro", "Română"),
    LangOption("bg", "Български"),
    LangOption("sr", "Српски"),
    LangOption("hr", "Hrvatski"),
    LangOption("sk", "Slovenčina"),
    LangOption("ca", "Català"),
    LangOption("fil", "Filipino")
)

@Composable
fun SettingsScreen(
    ready: Boolean,
    onReset: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val context = LocalContext.current
    var showLanguage by remember { mutableStateOf(false) }
    var showDns by remember { mutableStateOf(false) }
    var showDistro by remember { mutableStateOf(false) }
    var dnsDraft by remember { mutableStateOf(Prefs.dns) }
    var font by remember { mutableStateOf(Prefs.fontSize) }
    var chinaMirror by remember { mutableStateOf(Prefs.useChinaMirror) }
    var fixDeps by remember { mutableStateOf(Prefs.autoFixDeps) }
    var distro by remember { mutableStateOf(Prefs.distroId) }
    var languageTag by remember { mutableStateOf(Prefs.language) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.tab_settings),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_distro), fontSize = 13.sp)
                Text(
                    RootfsManager.label(distro),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { showDistro = true }) {
                Text(stringResource(R.string.action_edit))
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_mirror), fontSize = 13.sp)
                Text(
                    stringResource(if (chinaMirror) R.string.settings_mirror_cn else R.string.settings_mirror_global),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = chinaMirror,
                onCheckedChange = {
                    chinaMirror = it
                    Prefs.useChinaMirror = it
                }
            )
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_dns), fontSize = 13.sp)
                Text(Prefs.dns, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                dnsDraft = Prefs.dns
                showDns = true
            }) { Text(stringResource(R.string.action_edit)) }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_font), fontSize = 13.sp)
                Text("${font}sp", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = font.toFloat(),
                onValueChange = {
                    font = it.toInt()
                    Prefs.fontSize = font
                },
                valueRange = 10f..22f,
                steps = 11,
                modifier = Modifier.width(150.dp)
            )
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.install_fix_deps), fontSize = 13.sp)
            }
            Switch(
                checked = fixDeps,
                onCheckedChange = {
                    fixDeps = it
                    Prefs.autoFixDeps = it
                }
            )
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_theme_lang), fontSize = 13.sp)
                Text(
                    LANGUAGE_OPTIONS.firstOrNull { it.tag == languageTag }?.label ?: "System default",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { showLanguage = true }) {
                Text(stringResource(R.string.action_edit))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SettingRow(stringResource(R.string.settings_about), null) { onOpenAbout() }
        SettingRow(stringResource(R.string.settings_licenses), null) { onOpenLicenses() }
        SettingRow(stringResource(R.string.settings_terms), null) { onOpenTerms() }
        SettingRow(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), null) { }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.settings_danger), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onReset) {
            Text(
                stringResource(R.string.btn_reset),
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(stringResource(R.string.settings_theme_lang)) },
            text = {
                Column(Modifier.height(420.dp).verticalScroll(rememberScrollState())) {
                    LANGUAGE_OPTIONS.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    languageTag = option.tag
                                    Prefs.language = option.tag
                                    showLanguage = false
                                    (context as? Activity)?.recreate()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = languageTag == option.tag,
                                onClick = {
                                    languageTag = option.tag
                                    Prefs.language = option.tag
                                    showLanguage = false
                                    (context as? Activity)?.recreate()
                                }
                            )
                            Text(option.label, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguage = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDistro) {
        AlertDialog(
            onDismissRequest = { showDistro = false },
            title = { Text(stringResource(R.string.settings_distro)) },
            text = {
                Column {
                    listOf(RootfsManager.DISTRO_UBUNTU, RootfsManager.DISTRO_DEBIAN).forEach { id ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Prefs.distroId = id
                                    distro = id
                                    showDistro = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = distro == id,
                                onClick = {
                                    Prefs.distroId = id
                                    distro = id
                                    showDistro = false
                                }
                            )
                            Column {
                                Text(RootfsManager.label(id), fontSize = 13.sp)
                                Text(
                                    if (id == RootfsManager.DISTRO_DEBIAN)
                                        stringResource(R.string.distro_debian_desc)
                                    else stringResource(R.string.distro_ubuntu_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.distro_change_hint),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDistro = false }) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }

    if (showDns) {
        AlertDialog(
            onDismissRequest = { showDns = false },
            title = { Text(stringResource(R.string.settings_dns)) },
            text = {
                OutlinedTextField(
                    value = dnsDraft,
                    onValueChange = { dnsDraft = it },
                    singleLine = true,
                    label = { Text("8.8.8.8") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.dns = dnsDraft
                    showDns = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDns = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Text(title, fontSize = 13.sp)
        if (subtitle != null) {
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
