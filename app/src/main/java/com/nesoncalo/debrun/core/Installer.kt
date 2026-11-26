package com.nesoncalo.debrun.core

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs a `.deb` into the rootfs through `dpkg`, with an optional
 * `apt-get -f install` pass that pulls missing dependencies from the mirror.
 */
object Installer {

    data class Outcome(val success: Boolean, val packageName: String)

    fun install(
        context: Context,
        uri: Uri,
        displayName: String,
        fixDeps: Boolean,
        dns: String,
        cancel: AtomicBoolean,
        onLine: (String) -> Unit,
        onDone: (Outcome) -> Unit
    ) {
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._+-]"), "_").ifEmpty { "package.deb" }
        val copied = Proot.copyToGuest(context, uri, safeName)
        if (copied == null) {
            onDone(Outcome(false, ""))
            return
        }
        val guestFile = "/tmp/$safeName"
        val script = buildScript(guestFile, fixDeps)
        var pkg = ""
        var ok = false
        var process: Process? = null
        try {
            process = Proot.startProcess(Proot.shellCommand(script, dns))
            process.inputStream.bufferedReader().use { reader ->
                while (true) {
                    if (cancel.get()) {
                        process.destroy()
                        break
                    }
                    val line = reader.readLine() ?: break
                    if (line.startsWith("##DEBRUN_PKG=")) pkg = line.removePrefix("##DEBRUN_PKG=").trim()
                    if (line.contains("##DEBRUN_INSTALL_OK")) ok = true
                    onLine(line)
                }
            }
            process.waitFor()
            onDone(Outcome(ok, pkg))
        } catch (t: Throwable) {
            onLine("error: ${t.message}")
            onDone(Outcome(false, pkg))
        } finally {
            File(Env.rootfsDir, "tmp/$safeName").delete()
            process?.destroy()
        }
    }

    private fun buildScript(guestFile: String, fixDeps: Boolean): String {
        val forceArgs = if (fixDeps) "--force-confnew" else "--force-depends --force-confnew"
        val deps = if (fixDeps) """
            echo ">> resolving dependencies with apt"
            apt-get update -q 2>&1 | tail -n 5
            apt-get -f install -y 2>&1
            echo ">> retry: dpkg -i"
            dpkg -i --force-confnew "$guestFile" 2>&1
            RC=${'$'}?
        """.trimIndent() else ""
        return """
            export DEBIAN_FRONTEND=noninteractive
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            cd /tmp
            PKG_F=${'$'}(dpkg-deb -f "$guestFile" Package 2>/dev/null)
            echo "##DEBRUN_PKG=${'$'}PKG_F"
            echo ">> dpkg -i $guestFile"
            dpkg -i $forceArgs "$guestFile" 2>&1
            RC=${'$'}?
            if [ ${'$'}RC -ne 0 ]; then
              $deps
            fi
            ST=${'$'}(dpkg-query -W -f='${'$'}{Status}' "${'$'}PKG_F" 2>/dev/null)
            if [ "${'$'}ST" = "install ok installed" ]; then
              echo "##DEBRUN_INSTALL_OK"
            else
              echo "##DEBRUN_INSTALL_FAIL rc=${'$'}RC"
            fi
        """.trimIndent()
    }

    fun isInstalled(pkg: String, dns: String): Boolean {
        if (pkg.isEmpty()) return false
        val script = "dpkg-query -W -f='\${Status}' '${pkg.replace("'", "")}' 2>/dev/null"
        return try {
            val process = Proot.startProcess(Proot.shellCommand(script, dns))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("install ok installed")
        } catch (t: Throwable) {
            false
        }
    }
}
