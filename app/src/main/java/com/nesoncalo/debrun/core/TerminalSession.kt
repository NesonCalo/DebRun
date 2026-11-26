package com.nesoncalo.debrun.core

import java.io.File
import kotlin.concurrent.thread

/**
 * One interactive shell running inside the rootfs. When `script` is available
 * (util-linux / bsdextrautils) the shell gets a real pty, which keeps readline,
 * colours and job control working.
 */
class TerminalSession(
    private val onOutput: (String) -> Unit,
    private val onExit: (Int) -> Unit
) {

    @Volatile
    private var process: Process? = null

    @Volatile
    private var closed = false

    var cols: Int = 80
    var rows: Int = 24

    private val pendingInput = StringBuilder()

    val isRunning: Boolean get() = process?.isAlive == true

    fun scriptAvailable(): Boolean = File(Env.rootfsDir, "usr/bin/script").exists()

    fun start() {
        stop(notify = false)
        closed = false
        val command = Proot.interactiveShellCommand(scriptAvailable())
        val proc = Proot.startProcess(
            command,
            mapOf("COLUMNS" to cols.toString(), "LINES" to rows.toString())
        )
        android.util.Log.i("DebRun", "terminal start: ${command.joinToString(" ")}")
        process = proc
        synchronized(pendingInput) {
            if (pendingInput.isNotEmpty()) {
                runCatching {
                    proc.outputStream.write(pendingInput.toString().toByteArray(Charsets.UTF_8))
                    proc.outputStream.flush()
                }
                pendingInput.setLength(0)
            }
        }
        thread(name = "debrun-terminal", isDaemon = true) {
            try {
                proc.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        onOutput(String(buffer, 0, read, Charsets.UTF_8))
                    }
                }
            } catch (_: Throwable) {
            }
            val code = runCatching { proc.waitFor() }.getOrDefault(-1)
            android.util.Log.i("DebRun", "terminal exited: $code (closed=$closed)")
            if (!closed) onExit(code)
        }
    }

    fun write(text: String) {
        val proc = process
        if (proc == null || !proc.isAlive) {
            synchronized(pendingInput) { pendingInput.append(text) }
            return
        }
        try {
            proc.outputStream.write(text.toByteArray(Charsets.UTF_8))
            proc.outputStream.flush()
        } catch (_: Throwable) {
            synchronized(pendingInput) { pendingInput.append(text) }
        }
    }

    fun sendCtrlC() = write("\u0003")

    fun stop(notify: Boolean = true) {
        closed = !notify
        process?.let { runCatching { it.destroy() } }
        process = null
    }
}
