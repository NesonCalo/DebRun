package com.nesoncalo.debrun.core

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Static description of where everything lives inside the app sandbox.
 *
 * Layout (all under filesDir so that proot binaries stay executable for
 * targetSdk 28):
 *
 *   usr/bin/proot
 *   usr/libexec/proot/loader
 *   usr/lib/libtalloc.so.2, libandroid-shmem.so
 *   rootfs/            extracted Linux root filesystem
 *   l2s/               PROOT_L2S_DIR (hard-link emulation scratch space)
 *   logs/
 */
object Env {

    @Volatile
    private var application: Context? = null

    fun init(context: Context) {
        application = context.applicationContext
    }

    val app: Context
        get() = application ?: error("Env.init() was not called")

    val filesDir: File get() = app.filesDir
    val usrDir: File get() = File(filesDir, "usr")
    val rootfsDir: File get() = File(filesDir, "rootfs")
    val l2sDir: File get() = File(filesDir, "l2s")
    val logsDir: File get() = File(filesDir, "logs")

    val prootPath: File get() = File(usrDir, "bin/proot")
    val loaderPath: File get() = File(usrDir, "libexec/proot/loader")
    val loader32Path: File get() = File(usrDir, "libexec/proot/loader32")
    val libDir: File get() = File(usrDir, "lib")

    private val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86")

    fun primaryAbi(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in supportedAbis }
        return abi ?: Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    }

    /** Termux package naming for the current ABI. */
    fun arch(): String = when (primaryAbi()) {
        "arm64-v8a" -> "aarch64"
        "armeabi-v7a", "armeabi" -> "arm"
        "x86_64" -> "x86_64"
        "x86" -> "i686"
        else -> throw UnsupportedArchitectureException(primaryAbi())
    }

    fun isX86(): Boolean = primaryAbi().startsWith("x86")

    fun is64Bit(): Boolean = primaryAbi().contains("64")

    /** Marker file written once the rootfs has been fully extracted and configured. */
    val readyFlag: File get() = File(rootfsDir, ".debrun-ready")

    fun isRootfsReady(): Boolean = readyFlag.isFile && File(rootfsDir, "bin").exists()

    fun rootfsSizeBytes(): Long {
        if (!rootfsDir.exists()) return 0
        return rootfsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${value.toLong()} ${units[unit]}"
        else String.format("%.1f %s", value, units[unit])
    }
}

class UnsupportedArchitectureException(val abi: String) :
    IllegalStateException("Unsupported CPU architecture: $abi")
