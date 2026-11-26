package com.nesoncalo.debrun.core

import android.content.Context
import java.io.File

/**
 * Installs the proot runtime (extracted from the Termux `proot` package, GPL-2.0)
 * from assets and builds the proot command lines.
 */
object Proot {

    private const val GUEST_PATH =
        "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games"

    fun ensureInstalled(): Boolean {
        val arch = Env.arch()
        val ok = copyAsset("proot/$arch/proot", Env.prootPath, executable = true) &&
            copyAsset("proot/$arch/libexec/proot/loader", Env.loaderPath, executable = true) &&
            copyAsset("proot/$arch/lib/libtalloc.so.2", File(Env.libDir, "libtalloc.so.2")) &&
            copyAsset("proot/$arch/lib/libandroid-shmem.so", File(Env.libDir, "libandroid-shmem.so"))
        // 32-bit proot builds have no separate loader32.
        if (assetExists("proot/$arch/libexec/proot/loader32")) {
            copyAsset("proot/$arch/libexec/proot/loader32", Env.loader32Path, executable = true)
        }
        Env.l2sDir.mkdirs()
        Env.logsDir.mkdirs()
        return ok
    }

    private fun assetExists(path: String): Boolean = try {
        Env.app.assets.open(path).close()
        true
    } catch (t: Throwable) {
        false
    }

    private fun copyAsset(assetPath: String, target: File, executable: Boolean = false): Boolean {
        return try {
            if (!target.exists() || target.length() == 0L) {
                target.parentFile?.mkdirs()
                Env.app.assets.open(assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            target.setReadable(true, false)
            if (executable) target.setExecutable(true, false)
            true
        } catch (t: Throwable) {
            android.util.Log.e(
                "DebRun",
                "copyAsset failed: $assetPath -> ${target.absolutePath} (${t.javaClass.simpleName}: ${t.message})"
            )
            false
        }
    }

    /** Environment handed to the proot process itself (Android side). */
    fun processEnvironment(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val root = Env.rootfsDir.absolutePath
        val map = mutableMapOf(
            "PROOT_LOADER" to Env.loaderPath.absolutePath,
            "PROOT_L2S_DIR" to Env.l2sDir.absolutePath,
            "PROOT_TMP_DIR" to File(root, "tmp").absolutePath,
            "LD_LIBRARY_PATH" to Env.libDir.absolutePath,
            "HOME" to "/root",
            "TMPDIR" to "/tmp",
            "LANG" to "C.UTF-8",
            "TERM" to "xterm-256color"
        )
        if (Env.loader32Path.isFile) {
            map["PROOT_LOADER_32"] = Env.loader32Path.absolutePath
        }
        map.putAll(extra)
        return map
    }

    /** Arguments in front of the guest command. */
    fun baseArgs(): List<String> {
        val args = mutableListOf(
            Env.prootPath.absolutePath,
            "--kill-on-exit",
            "--link2symlink",
            "-0",
            "-r", Env.rootfsDir.absolutePath
        )
        listOf("/dev", "/proc", "/sys").forEach { path ->
            if (File(path).exists()) {
                args += "-b"
                args += path
            }
        }
        // Note: no /storage bind on purpose. A bind of a path the app cannot read
        // makes proot abort, and shared storage is not needed: .deb files are pulled
        // in through SAF and copied into the rootfs tmp dir.
        args += "-w"
        args += "/root"
        return args
    }

    /** Full command running [script] through /bin/sh inside the rootfs. */
    fun shellCommand(script: String, dns: String? = null): List<String> {
        val cmd = baseArgs().toMutableList()
        cmd += "/usr/bin/env"
        cmd += "-i"
        cmd += "HOME=/root"
        cmd += "TERM=xterm-256color"
        cmd += "LANG=C.UTF-8"
        cmd += "LC_ALL=C.UTF-8"
        cmd += "PATH=$GUEST_PATH"
        cmd += "TMPDIR=/tmp"
        if (!dns.isNullOrBlank()) cmd += "DNS_SERVER=$dns"
        cmd += "/bin/sh"
        cmd += "-c"
        cmd += script
        return cmd
    }

    /** Full command for an interactive login shell. */
    fun interactiveShellCommand(useScript: Boolean): List<String> {
        val cmd = baseArgs().toMutableList()
        cmd += "/usr/bin/env"
        cmd += "-i"
        cmd += "HOME=/root"
        cmd += "TERM=xterm-256color"
        cmd += "LANG=C.UTF-8"
        cmd += "LC_ALL=C.UTF-8"
        cmd += "PATH=$GUEST_PATH"
        cmd += "TMPDIR=/tmp"
        cmd += "SHELL=/bin/bash"
        if (useScript) {
            // `script` allocates a real pty, so readline / job control behave normally.
            cmd += "/usr/bin/script"
            cmd += "-q"
            cmd += "-c"
            cmd += "exec /bin/bash -i"
            cmd += "/dev/null"
        } else {
            cmd += "/bin/bash"
            cmd += "-i"
        }
        return cmd
    }

    fun startProcess(command: List<String>, extraEnv: Map<String, String> = emptyMap()): Process {
        val builder = ProcessBuilder(command)
        val env = builder.environment()
        env.clear()
        env.putAll(processEnvironment(extraEnv))
        builder.directory(Env.rootfsDir)
        builder.redirectErrorStream(true)
        return builder.start()
    }

    fun chmodExecutable(file: File) {
        file.setReadable(true, false)
        file.setExecutable(true, false)
    }

    fun copyToGuest(context: Context, sourceUri: android.net.Uri, guestName: String): File? {
        val tmpDir = File(Env.rootfsDir, "tmp")
        tmpDir.mkdirs()
        val target = File(tmpDir, guestName)
        return try {
            context.contentResolver.openInputStream(sourceUri).use { input ->
                if (input == null) return null
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        } catch (t: Throwable) {
            null
        }
    }
}
