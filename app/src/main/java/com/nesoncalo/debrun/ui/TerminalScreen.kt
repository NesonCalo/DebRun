package com.nesoncalo.debrun.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nesoncalo.debrun.R
import com.nesoncalo.debrun.core.Env
import com.nesoncalo.debrun.core.Prefs
import com.nesoncalo.debrun.core.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    ready: Boolean,
    pendingCommand: String?,
    onCommandConsumed: () -> Unit,
    onNeedSetup: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    val pending = remember { StringBuilder() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var started by remember { mutableStateOf(false) }

    val session = remember {
        TerminalSession(
            onOutput = { text ->
                synchronized(pending) { pending.append(text) }
                webView?.post { flushPending(webView, pending) }
            },
            onExit = { code ->
                synchronized(pending) { pending.append("\r\n\u001b[90m[process exited: $code]\u001b[0m\r\n") }
                webView?.post { flushPending(webView, pending) }
                started = false
            }
        )
    }

    if (!ready) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.hint_need_setup), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onNeedSetup) { Text(stringResource(R.string.btn_setup)) }
        }
        return
    }

    LaunchedEffect(ready, webView) {
        if (ready && webView != null && !started) {
            started = true
            scope.launch(Dispatchers.IO) { session.start() }
        }
    }

    LaunchedEffect(pendingCommand) {
        val command = pendingCommand ?: return@LaunchedEffect
        session.write(command + "\n")
        onCommandConsumed()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0D1014))) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .background(
                        if (session.isRunning) Color(0xFF7BD88F) else Color(0xFF555D68),
                        RoundedCornerShape(50)
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (session.isRunning) R.string.term_status_running else R.string.term_status_idle),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF9AA4B2)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${Env.arch()} · ${Prefs.distroId}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF9AA4B2)
            )
            TextButton(onClick = {
                webView?.evaluateJavascript("window.dr.clear()", null)
                session.stop(notify = false)
                session.start()
                started = true
            }) { Text(stringResource(R.string.term_restart), fontSize = 11.sp) }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(AndroidColor.rgb(13, 16, 20))
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.setSupportZoom(false)
                        settings.textZoom = 100
                        settings.defaultFontSize = 13
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun sendData(data: String) {
                                session.write(data)
                            }

                            @JavascriptInterface
                            fun resize(cols: Int, rows: Int) {
                                session.cols = cols
                                session.rows = rows
                            }

                            @JavascriptInterface
                            fun ready() {
                                webView?.post {
                                    webView?.evaluateJavascript(
                                        "window.dr.fontSize(${Prefs.fontSize})", null
                                    )
                                }
                            }
                        }, "DrBridge")
                        webViewClient = WebViewClient()
                        loadUrl("file:///android_asset/terminal/index.html")
                    }
                },
                update = { view -> webView = view }
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(EXTRA_KEYS) { key ->
                OutlinedButton(
                    onClick = { sendKey(webView, key.second) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 2.dp
                    )
                ) {
                    Text(key.first, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }
        }
    }
}

private val EXTRA_KEYS = listOf(
    "ESC" to "\u001b",
    "TAB" to "\t",
    "^C" to "\u0003",
    "^D" to "\u0004",
    "↑" to "\u001b[A",
    "↓" to "\u001b[B",
    "→" to "\u001b[C",
    "←" to "\u001b[D",
    "/" to "/",
    "-" to "-",
    "|" to "|",
    "~" to "~"
)

private fun sendKey(webView: WebView?, sequence: String) {
    val quoted = JSONObject.quote(sequence)
    webView?.evaluateJavascript("window.dr.inject($quoted)", null)
    webView?.evaluateJavascript("window.dr.focus()", null)
}

private fun flushPending(webView: WebView?, pending: StringBuilder) {
    val chunk = synchronized(pending) {
        val text = pending.toString()
        pending.setLength(0)
        text
    }
    if (chunk.isEmpty()) return
    webView?.evaluateJavascript("window.dr.write(${JSONObject.quote(chunk)})", null)
}
