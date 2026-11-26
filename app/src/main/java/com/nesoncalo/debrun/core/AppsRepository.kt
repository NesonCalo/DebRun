package com.nesoncalo.debrun.core

import java.util.concurrent.TimeUnit

object AppsRepository {

    data class Pkg(val name: String, val version: String, val summary: String)

    private const val FORMAT = "\${Package}\t\${Version}\t\${binary:Summary}\n"

    fun load(dns: String, callback: (List<Pkg>) -> Unit) {
        val result = mutableListOf<Pkg>()
        try {
            val process = Proot.startProcess(
                Proot.shellCommand("dpkg-query -W -f='$FORMAT' 2>/dev/null")
            )
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(25, TimeUnit.SECONDS)
            output.lineSequence().forEach { line ->
                val parts = line.split('\t')
                if (parts.size >= 2 && parts[0].isNotBlank()) {
                    result += Pkg(
                        name = parts[0].trim(),
                        version = parts[1].trim(),
                        summary = parts.getOrNull(2)?.trim().orEmpty()
                    )
                }
            }
            process.destroy()
        } catch (_: Throwable) {
        }
        callback(result.sortedBy { it.name })
    }
}
