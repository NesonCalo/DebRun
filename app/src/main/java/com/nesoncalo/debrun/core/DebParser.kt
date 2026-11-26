package com.nesoncalo.debrun.core

import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

/**
 * Minimal reader for Debian binary packages (`ar` archive + `control.tar.{gz,xz}`),
 * used to preview package metadata before handing the file to `dpkg`.
 */
data class DebInfo(
    val packageName: String,
    val version: String,
    val architecture: String,
    val installedSize: Long,
    val depends: String,
    val description: String,
    val homepage: String,
    val maintainer: String
)

object DebParser {

    fun parse(file: File): DebInfo? {
        return try {
            val controlBytes = extractControl(file) ?: return null
            parseControl(controlBytes)
        } catch (t: Throwable) {
            null
        }
    }

    /** Returns the raw bytes of the package's `control` file, if readable. */
    private fun extractControl(file: File): ByteArray? {
        file.inputStream().use { input ->
            val magic = ByteArray(8)
            if (input.read(magic) != 8) return null
            if (String(magic, Charsets.US_ASCII) != "!<arch>\n") return null
            val header = ByteArray(60)
            while (true) {
                if (readFully(input, header, 60) != 60) return null
                val name = String(header, 0, 16, Charsets.US_ASCII).trim().removeSuffix("/")
                val size = String(header, 48, 10, Charsets.US_ASCII).trim().toLongOrNull() ?: return null
                if (!name.startsWith("control.tar")) {
                    skip(input, size)
                    continue
                }
                val body = ByteArray(size.toInt())
                if (readFully(input, body, body.size) != body.size) return null
                return readControlFromTar(decompress(name, body))
            }
        }
    }

    private fun decompress(name: String, body: ByteArray): InputStream = when {
        name.endsWith(".gz") -> GZIPInputStream(body.inputStream())
        name.endsWith(".xz") -> XZCompressorInputStream(body.inputStream())
        name.endsWith(".bz2") -> org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(body.inputStream())
        else -> body.inputStream()
    }

    private fun readControlFromTar(stream: InputStream): ByteArray? {
        TarArchiveInputStream(stream).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: return null
                val name = entry.name.trimStart('.', '/')
                if (!entry.isDirectory && name == "control") {
                    val bytes = ByteArray(entry.size.toInt())
                    var offset = 0
                    while (offset < bytes.size) {
                        val read = tar.read(bytes, offset, bytes.size - offset)
                        if (read <= 0) break
                        offset += read
                    }
                    return bytes
                }
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Int {
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return offset
    }

    private fun skip(input: InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) return
                remaining--
            } else {
                remaining -= skipped
            }
        }
        if (count % 2 == 1L) input.read()
    }

    private fun parseControl(bytes: ByteArray): DebInfo? {
        val text = bytes.toString(Charsets.UTF_8)
        if (!text.contains("Package:")) return null
        fun field(name: String): String =
            Regex("(?m)^$name:\\s*(.*)").find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val installedKb = field("Installed-Size").toLongOrNull() ?: 0L
        return DebInfo(
            packageName = field("Package"),
            version = field("Version"),
            architecture = field("Architecture"),
            installedSize = installedKb * 1024,
            depends = field("Depends"),
            description = field("Description").replace("\n ", "\n"),
            homepage = field("Homepage"),
            maintainer = field("Maintainer")
        )
    }
}
