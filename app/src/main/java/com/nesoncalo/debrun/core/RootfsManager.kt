package com.nesoncalo.debrun.core

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

data class Distro(
    val id: String,
    val nameRes: Int,
    val descRes: Int,
    val approxBytes: Long
)

object RootfsManager {

    const val DISTRO_UBUNTU = "ubuntu24"
    const val DISTRO_DEBIAN = "debian12"

    fun distros(): List<Distro> = listOf(
        Distro(DISTRO_UBUNTU, com.nesoncalo.debrun.R.string.distro_ubuntu, com.nesoncalo.debrun.R.string.distro_ubuntu_desc, 30L * 1024 * 1024),
        Distro(DISTRO_DEBIAN, com.nesoncalo.debrun.R.string.distro_debian, com.nesoncalo.debrun.R.string.distro_debian_desc, 95L * 1024 * 1024)
    )

    fun label(distroId: String): String = when (distroId) {
        DISTRO_DEBIAN -> "Debian 12"
        else -> "Ubuntu 24.04"
    }

    sealed class Event {
        data class Log(val text: String) : Event()
        data class Progress(val percent: Int, val text: String) : Event()
        data class Phase(val title: String, val indeterminate: Boolean = false) : Event()
        data class Failure(val message: String) : Event()
        object Done : Event()
    }

    private val UBUNTU_MIRRORS = listOf(
        "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage",
        "https://mirrors.aliyun.com/ubuntu-cdimage",
        "https://cdimages.ubuntu.com"
    )

    private val DEBIAN_LXC_MIRRORS = listOf(
        "https://mirrors.tuna.tsinghua.edu.cn/lxc-images",
        "https://mirrors.nju.edu.cn/lxc-images",
        "https://images.linuxcontainers.org"
    )

    private fun ubuntuArch(): String? = when (Env.primaryAbi()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a", "armeabi" -> "armhf"
        "x86_64" -> "amd64"
        else -> null
    }

    private fun debianArch(): String? = when (Env.primaryAbi()) {
        "arm64-v8a" -> "arm64"
        "armeabi-v7a", "armeabi" -> "armhf"
        "x86_64" -> "amd64"
        "x86" -> "i386"
        else -> null
    }

    /** Ordered list of candidate tarball URLs for the requested distribution. */
    private fun candidateUrls(distroId: String, log: (String) -> Unit): List<String> {
        return if (distroId == DISTRO_DEBIAN) {
            val arch = debianArch() ?: return emptyList()
            val urls = mutableListOf<String>()
            for (base in DEBIAN_LXC_MIRRORS) {
                val listing = tryFetch("$base/images/debian/bookworm/$arch/default/")
                if (listing == null) {
                    log("listing unavailable: $base")
                    continue
                }
                val dates = Regex("href=\"(\\d{8}_\\d{2}(?:%3A|:)\\d{2})/\"")
                    .findAll(listing)
                    .map { it.groupValues[1] }
                    .toList()
                    .sortedDescending()
                if (dates.isEmpty()) {
                    log("no image found: $base")
                    continue
                }
                val newest = dates.first()
                urls += "$base/images/debian/bookworm/$arch/default/$newest/rootfs.tar.xz"
            }
            urls
        } else {
            val arch = ubuntuArch() ?: return emptyList()
            UBUNTU_MIRRORS.map { base ->
                "$base/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.5-base-$arch.tar.gz"
            }
        }
    }

    fun downloadArchive(
        distroId: String,
        target: File,
        cancel: AtomicBoolean,
        emit: (Event) -> Unit
    ): File? {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        val urls = candidateUrls(distroId, { emit(Event.Log(it)) })
        if (urls.isEmpty()) {
            emit(Event.Failure("no download source for this architecture"))
            return null
        }
        for (url in urls) {
            if (cancel.get()) return null
            emit(Event.Log("GET $url"))
            emit(Event.Phase("download", false))
            try {
                download(url, target, cancel, emit)
                emit(Event.Log("saved ${Env.humanSize(target.length())}"))
                return target
            } catch (t: Throwable) {
                emit(Event.Log("failed: ${t.message}"))
                target.delete()
            }
        }
        emit(Event.Failure("download failed"))
        return null
    }

    private fun download(url: String, target: File, cancel: AtomicBoolean, emit: (Event) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DebRun/1.0 (Android)")
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalIOException("HTTP ${conn.responseCode}")
        }
        val total = conn.contentLengthLong
        var copied = 0L
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var lastReport = 0L
                while (true) {
                    if (cancel.get()) throw IllegalIOException("cancelled")
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (copied - lastReport > 512 * 1024 || read < buffer.size) {
                        lastReport = copied
                        val pct = if (total > 0) ((copied * 100) / total).toInt() else -1
                        emit(
                            Event.Progress(
                                pct,
                                "${Env.humanSize(copied)} / ${if (total > 0) Env.humanSize(total) else "?"}"
                            )
                        )
                    }
                }
            }
        }
        conn.disconnect()
    }

    private class IllegalIOException(message: String) : IOException(message)

    private fun tryFetch(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DebRun/1.0")
        }
        conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (t: Throwable) {
        null
    }

    fun extractArchive(
        archive: File,
        target: File,
        cancel: AtomicBoolean,
        emit: (Event) -> Unit
    ): Boolean {
        emit(Event.Phase("extract", true))
        target.mkdirs()
        val ok = TarExtractor.extract(archive, target) { count, name ->
            if (count % 1000 == 0) emit(Event.Log("$count entries: $name"))
        }
        if (ok) emit(Event.Log("extracted to ${target.absolutePath}"))
        return ok
    }

    fun configure(distroId: String, dns: String, useChinaMirror: Boolean, emit: (Event) -> Unit) {
        emit(Event.Phase("configure", true))
        val root = Env.rootfsDir
        listOf("tmp", "root", "var/tmp", "proc", "dev", "sys", "etc/apt/apt.conf.d").forEach {
            File(root, it).mkdirs()
        }
        File(root, "tmp").setWritable(true, false)
        File(root, "tmp").setExecutable(true, false)

        val nameservers = buildList {
            if (dns.isNotBlank()) add(dns.trim())
            add("223.5.5.5")
            add("119.29.29.29")
            add("8.8.8.8")
        }
        writeFile(File(root, "etc/resolv.conf"), nameservers.joinToString("\n") { "nameserver $it" } + "\n")
        writeFile(
            File(root, "etc/hosts"),
            "127.0.0.1\tlocalhost\n127.0.1.1\tdebrun\n::1\tlocalhost ip6-localhost ip6-loopback\n"
        )
        writeFile(File(root, "etc/apt/apt.conf.d/99debrun"), """
            APT::Sandbox::User "root";
            Acquire::Check-Valid-Until "false";
            Acquire::Retries "3";
            Dpkg::Options {
               "--force-confdef";
               "--force-confold";
            };
        """.trimIndent() + "\n")
        writeFile(File(root, "etc/profile.d/99debrun.sh"), """
            export LANG=C.UTF-8
            export LC_ALL=C.UTF-8
            export TERM=xterm-256color
        """.trimIndent() + "\n")
        writeFile(File(root, "root/.bashrc"), bashrc())

        val host = if (useChinaMirror) "mirrors.tuna.tsinghua.edu.cn" else "deb.debian.org"
        when (distroId) {
            DISTRO_DEBIAN -> {
                val deb = if (useChinaMirror) "http://mirrors.tuna.tsinghua.edu.cn/debian" else "http://deb.debian.org/debian"
                val sec = if (useChinaMirror) "http://mirrors.tuna.tsinghua.edu.cn/debian-security" else "http://security.debian.org/debian-security"
                writeFile(File(root, "etc/apt/sources.list"), """
                    deb $deb bookworm main contrib non-free non-free-firmware
                    deb $deb bookworm-updates main contrib non-free non-free-firmware
                    deb $sec bookworm-security main contrib non-free non-free-firmware
                """.trimIndent() + "\n")
            }
            else -> {
                val isArm = Env.primaryAbi().startsWith("arm") || Env.primaryAbi() == "x86"
                val base = if (useChinaMirror) {
                    if (isArm) "http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports"
                    else "http://mirrors.tuna.tsinghua.edu.cn/ubuntu"
                } else {
                    if (isArm) "http://ports.ubuntu.com/ubuntu-ports"
                    else "http://archive.ubuntu.com/ubuntu"
                }
                File(root, "etc/apt/sources.list").writeText("")
                writeFile(File(root, "etc/apt/sources.list.d/ubuntu.sources"), """
                    Types: deb
                    URIs: $base
                    Suites: noble noble-updates noble-security noble-backports
                    Components: main restricted universe multiverse
                    Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
                """.trimIndent() + "\n")
            }
        }
        emit(Event.Log("mirror host: $host"))
        writeFile(File(root, ".debrun-ready"), "${System.currentTimeMillis()}\n")
        emit(Event.Phase("done", true))
    }

    private fun bashrc(): String = """
        export PS1='\[\033[1;32m\]root@debrun\[\033[0m\]:\[\033[1;34m\]\w\[\033[0m\]# '
        export LANG=C.UTF-8
        export LC_ALL=C.UTF-8
        export TERM=xterm-256color
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        alias ll='ls -alF'
        alias la='ls -A'
        alias deb-install='dpkg -i'
        cd /root 2>/dev/null
        clear 2>/dev/null
    """.trimIndent() + "\n"

    private fun writeFile(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun wipeRootfs() {
        Env.rootfsDir.deleteRecursively()
        Env.l2sDir.deleteRecursively()
    }
}
