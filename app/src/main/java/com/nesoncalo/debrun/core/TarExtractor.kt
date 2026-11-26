package com.nesoncalo.debrun.core

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.zip.GZIPInputStream

/**
 * Extracts Linux rootfs tarballs (`.tar`, `.tar.gz`, `.tar.xz`) into the app sandbox,
 * preserving symlinks, hard links and the executable bit.
 */
object TarExtractor {

    private fun openStream(file: File): InputStream {
        val head = ByteArray(6)
        file.inputStream().use { it.read(head) }
        val raw = BufferedInputStream(file.inputStream(), 64 * 1024)
        return when {
            head[0] == 0x1f.toByte() && head[1] == 0x8b.toByte() -> GZIPInputStream(raw)
            head[0] == 0xfd.toByte() && head[1] == 0x37.toByte() &&
                head[2] == 0x7a.toByte() -> XZCompressorInputStream(raw)
            else -> raw
        }
    }

    fun extract(archive: File, dest: File, onEntry: (Int, String) -> Unit): Boolean {
        dest.mkdirs()
        val destPath = dest.toPath().toAbsolutePath().normalize()
        var count = 0
        try {
            openStream(archive).use { stream ->
                TarArchiveInputStream(stream).use { tar ->
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        val name = normalize(entry.name)
                        val path = if (name.isEmpty()) null else safeResolve(destPath, name)
                        if (path != null) {
                            if (entry.isDirectory) {
                                Files.createDirectories(path)
                            } else if (entry.isSymbolicLink) {
                                path.parent?.let { Files.createDirectories(it) }
                                Files.deleteIfExists(path)
                                try {
                                    Files.createSymbolicLink(path, File(entry.linkName).toPath())
                                } catch (t: Throwable) {
                                    copyLinkFallback(destPath, entry, path)
                                }
                            } else if (entry.isLink) {
                                path.parent?.let { Files.createDirectories(it) }
                                val target = safeResolve(destPath, normalize(entry.linkName))
                                Files.deleteIfExists(path)
                                if (target != null && Files.exists(target)) {
                                    try {
                                        Files.createLink(path, target)
                                    } catch (t: Throwable) {
                                        Files.copy(target, path)
                                    }
                                }
                            } else {
                                path.parent?.let { Files.createDirectories(it) }
                                Files.deleteIfExists(path)
                                tar.copyTo(Files.newOutputStream(path))
                                applyMode(path, entry.mode)
                            }
                        }
                        count++
                        if (count % 200 == 0) onEntry(count, name)
                        entry = tar.nextEntry
                    }
                }
            }
            return true
        } catch (t: Throwable) {
            return false
        }
    }

    private fun safeResolve(root: Path, name: String): Path? {
        return try {
            val resolved = root.resolve(name).normalize()
            if (resolved.startsWith(root)) resolved else null
        } catch (t: Throwable) {
            null
        }
    }

    private fun copyLinkFallback(root: Path, entry: TarArchiveEntry, path: Path) {
        // Broken links are harmless inside proot; write a best-effort relative link.
        try {
            val target = safeResolve(root, normalize(entry.linkName))
            if (target != null && Files.exists(target)) {
                Files.copy(target, path, LinkOption.NOFOLLOW_LINKS)
            } else {
                Files.createFile(path)
            }
        } catch (_: Throwable) {
        }
    }

    private fun applyMode(path: Path, mode: Int) {
        try {
            val file = path.toFile()
            file.setReadable(true, false)
            file.setWritable(true, false)
            if (mode and 0b001_000_000 != 0 || mode and 0b000_001_000 != 0 || mode and 0b000_000_001 != 0) {
                file.setExecutable(true, false)
            }
        } catch (_: Throwable) {
        }
    }

    private fun normalize(raw: String): String {
        var name = raw.replace('\\', '/')
        while (name.startsWith("./")) name = name.substring(2)
        name = name.trimStart('/')
        if (name == "." || name.isEmpty()) return ""
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return ""
        return parts.joinToString("/")
    }
}
