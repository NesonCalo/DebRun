# DebRun

在 Android 上直接安装并运行 `.deb` 软件包的开源容器运行时。

DebRun = **PRoot** + **Linux 根文件系统**（Debian 12 / Ubuntu 24.04）+ **dpkg/apt** + **内置终端**。不需要 root，不需要解锁 Bootloader，不写入 Android 系统分区。

> Run Debian-style `.deb` packages on Android without root, by unpacking them into a private Linux rootfs driven by PRoot.

---

## 为什么需要它

Android 的 libc 是 bionic，桌面 Linux 发行版用的是 glibc。`.deb` 里的 ELF 程序直接放到 Android 上是跑不起来的——不是缺权限，是 ABI 不兼容。所以任何"在安卓上运行 deb"的方案，本质上都必须先有一份完整 Linux 用户态：

1. **PRoot** 通过 `ptrace` 模拟 `chroot`/`mount`，让普通 App 拥有一个独立的 Linux 文件树（这是不需要 root 的关键）。
2. 下载一份 **根文件系统**（Debian/Ubuntu），解压到 App 私有目录。
3. `.deb` 交给容器里的 **dpkg** 安装，缺依赖由 **apt** 补齐。
4. 内置终端（xterm.js + WebView）驱动容器内的 `bash`。

DebRun 把这四步做成一个图形界面：点一下初始化，选文件，装完就能在终端里跑。

## 功能

- 一键初始化：自动选择国内镜像（清华 TUNA / 阿里云），失败自动回退官方源
- 四种 ABI 全支持：`arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`
- `.deb` 元数据预览：包名、版本、架构、安装后体积、依赖（App 内自行解析 `ar` + `control.tar`）
- 安装流程：`dpkg -i` → 失败自动 `apt-get -f install` 补依赖 → 重试 → 状态校验
- 实时安装日志，可中断
- 真 PTY 终端（有 `script` 时自动启用），配色、readline、Ctrl 键、方向键都可用
- 已安装包列表 + 一键把命令丢进终端执行
- 38 种语言（英文 / 简体中文完整翻译，其余 36 种覆盖主界面核心文案）
- 完全离线可用（除首次下载根文件系统和 apt 安装依赖）

## 安装

从 [Releases](../../releases) 下载 APK 直接安装。首次打开点"初始化环境"，等待下载（约 30 MB）。

最低 Android 6.0（API 24）。**targetSdk 固定为 28**，这是刻意的：Android 10 起，targetSdk ≥ 29 的应用不允许执行私有目录里的二进制文件，而 proot 与整个 Linux 用户态都依赖这一能力。

## 用法

1. **首页**：初始化环境。下载 → 解压 → 配置镜像 → 安装基础工具（`bsdextrautils`、`procps`）。
2. **安装**：选择 `.deb` → 预览元数据 → 安装。默认开启"用 apt 自动补齐依赖"。
3. **终端**：交互式 `bash`，`apt`/`dpkg`/`nano` 之类都能用。
4. **应用**：列出容器内已安装的软件包，点一下可以填命令并跳转到终端执行。

命令行等价操作（在 DebRun 终端里）：

```sh
apt-get update
apt-get install -y curl git
dpkg -i /tmp/foo.deb        # 或 apt install ./foo.deb
```

## 已知限制

- **图形界面程序（X11）不显示**：Firefox、GIMP 这类需要 X server。路线图里会接 `Termux:X11` 或内置 VNC。
- 容器内没有内核模块、`systemd`/`init`、真实设备节点；`/dev`、`/proc`、`/sys` 是 PRoot 绑定挂载的宿主视图。
- 64 位设备上跑 32 位二进制（arm64 上跑 armhf）不支持。
- 部分 `.deb` 会因为依赖版本（例如 glibc 版本、`libssl3`/`libssl3t64` 差异）装不上；Debian 12 源更贴近原生，Ubuntu 24.04 的 glibc 更新、兼容性更宽。
- PRoot 是 `ptrace` 模拟，IO 密集任务明显慢于真机原生，属于预期行为。

## 构建

```bash
# 需要 JDK 17 与 Android SDK (platform 34, build-tools 34.0.0)
./gradlew :app:assembleRelease
```

`local.properties` 里写 `sdk.dir=...`。发布签名可选：在仓库根目录放 `keystore.properties`（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`），或用环境变量 `DEBRUN_KEYSTORE` 等。CI 见 `.github/workflows/build.yml`。

## 目录结构

```
app/src/main/java/com/nesoncalo/debrun/
├── core/
│   ├── Env.kt            # 沙盒路径、ABI 判定
│   ├── Proot.kt          # proot 运行时安装 + 命令行拼装 + 进程环境
│   ├── RootfsManager.kt  # 发行版定义、镜像下载、配置
│   ├── TarExtractor.kt   # rootfs tar(.gz/.xz) 解压，保留符号链接与执行位
│   ├── DebParser.kt      # ar + control.tar 解析，用于元数据预览
│   ├── Installer.kt      # dpkg/apt 安装编排
│   ├── TerminalSession.kt# 交互式 shell 会话
│   ├── AppsRepository.kt # dpkg-query 列表
│   └── Prefs.kt          # 设置项
└── ui/                   # Compose 界面（首页 / 安装 / 终端 / 应用 / 设置）
app/src/main/assets/
├── proot/<abi>/          # Termux 构建的 proot 二进制 + loader + 依赖库
└── terminal/             # xterm.js 终端前端
```

## 致谢与许可

DebRun 以 **GPL-3.0-or-later** 发布，见 [LICENSE](LICENSE)。

关键第三方组件：

| 组件 | 许可 | 说明 |
| --- | --- | --- |
| PRoot 5.1.107.102（Termux 构建） | GPL-2.0-or-later | 打包在 `assets/proot/`，对应源码见 https://github.com/termux/proot |
| libtalloc 2.5.0、libandroid-shmem 0.7 | LGPL-3.0-or-later / MIT | proot 运行时依赖 |
| xterm.js 5.5.0、@xterm/addon-fit 0.10.0 | MIT | 终端前端 |
| Apache Commons Compress 1.26.2 | Apache-2.0 | tar/ar 解析 |
| XZ for Java 1.9 | 0BSD | xz 解压 |
| AndroidX / Jetpack Compose / Kotlin | Apache-2.0 | 界面与语言 |
| Ubuntu base 24.04 / Debian 12 镜像 | 各自自由软件许可 | 运行时从公共镜像下载，不随 APK 分发 |

完整清单见 [THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)。

## 免责声明

只安装你信任的 `.deb`。容器里的程序以 App 自身权限运行，可以访问 App 的网络与已授权存储；DebRun 不在软件包之间做额外隔离。本软件按"现状"提供，不附带担保。
