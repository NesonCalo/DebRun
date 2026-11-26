# 第三方组件与许可 / Third-Party Components and Licenses

DebRun 自身以 GPL-3.0-or-later 发布。以下组件随 APK 分发或作为运行时依赖被下载，
各自的许可条款独立适用。本文件同时满足 GPL-3.0 §6（对应源码）与多数宽松许可的署名要求。

## 随 APK 打包的二进制 / 前端资产

### PRoot 5.1.107.102（Termux 构建，四个 ABI）

- 路径：`app/src/main/assets/proot/<abi>/proot`、`libexec/proot/loader`、`loader32`
- 上游：https://github.com/termux/proot（Termux 维护的 PRoot 分支，基于 https://github.com/proot-me/PRoot ）
- 许可：GPL-2.0-or-later
- 对应源码：https://github.com/termux/proot/tree/master（构建配方见 https://github.com/termux/termux-packages/tree/master/packages/proot ）
- 说明：DebRun 不修改其源码，仅原样打包并在运行时通过环境变量（`PROOT_LOADER`、`PROOT_TMP_DIR` 等）重定向其默认路径。

### libtalloc 2.5.0

- 路径：`app/src/main/assets/proot/<abi>/lib/libtalloc.so.2`
- 上游：https://talloc.samba.org/
- 许可：LGPL-3.0-or-later

### libandroid-shmem 0.7

- 路径：`app/src/main/assets/proot/<abi>/lib/libandroid-shmem.so`
- 上游：https://github.com/termux/libandroid-shmem
- 许可：MIT

### xterm.js 5.5.0 + @xterm/addon-fit 0.10.0

- 路径：`app/src/main/assets/terminal/`
- 上游：https://github.com/xtermjs/xterm.js
- 许可：MIT（许可原文随文件放在 `app/src/main/assets/terminal/LICENSE`）

## Gradle 依赖（编译期引入、运行时打包进 dex）

| 组件 | 版本 | 许可 |
| --- | --- | --- |
| Kotlin Standard Library | 2.0.10 | Apache-2.0 |
| AndroidX Core KTX | 1.13.1 | Apache-2.0 |
| AndroidX Activity Compose | 1.9.0 | Apache-2.0 |
| AndroidX Lifecycle (runtime / viewmodel-compose) | 2.8.4 | Apache-2.0 |
| Jetpack Compose（BOM 2024.06.00：runtime、foundation、material3、ui、ui-tooling） | — | Apache-2.0 |
| Apache Commons Compress | 1.26.2 | Apache-2.0 |
| XZ for Java | 1.9 | 0BSD |
| SLF4J API（Commons Compress 可选依赖） | 1.7.36 | MIT |

## 运行时下载、不随 APK 分发

| 资源 | 许可 | 说明 |
| --- | --- | --- |
| Ubuntu base 24.04 rootfs（cdimage.ubuntu.com） | 组件各自许可（以 GPL/LGPL/BSD 为主） | 首次初始化时下载，存放于 App 私有目录 |
| Debian 12 bookworm rootfs（LXC images 镜像站） | 同上（Debian 社会契约 / DFSG） | 可选发行版 |
| apt 仓库中的 `.deb` 软件包 | 各软件包自身许可 | 由用户在容器内自行安装 |

DebRun 不对上述运行时下载内容主张任何权利，也不对其做二次分发；用户在容器内安装的软件，
其许可与合规责任由用户自行承担。

## 商标

Debian® 是 Software in the Public Interest, Inc. 的注册商标；Ubuntu® 是 Canonical Ltd. 的注册商标；
Android 是 Google LLC 的商标。DebRun 与上述主体无隶属关系，名称仅用于描述兼容性。
