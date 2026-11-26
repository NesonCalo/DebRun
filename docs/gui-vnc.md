# 在容器里装可视化界面（X11 + VNC）

DebRun 的容器只有命令行。要让 GUI 程序（微信 Linux 版、Electron 类应用）显示出来，需要一层虚拟屏幕 + VNC：

- `Xvfb`：虚拟 X server，不输出到屏幕，画面全在内存里
- `x11vnc`：把 Xvfb 的画面转成 VNC 协议
- `websockify` + `noVNC`：把 VNC 转成浏览器能看的网页（Android 上直接开 Chrome 就行，不用装 VNC 客户端）

## 一条命令

在 DebRun 终端里粘贴执行：

```sh
apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends xvfb x11vnc novnc websockify && mkdir -p /tmp/.X11-unix && { Xvfb :0 -screen 0 1280x720x24 -extension MIT-SHM -nolisten tcp >/tmp/xvfb.log 2>&1 & } ; sleep 3; x11vnc -display :0 -noshm -nopw -forever -shared -rfbport 5900 -localhost -noxdamage >/tmp/x11vnc.log 2>&1 & sleep 4; websockify --web=/usr/share/novnc 6080 127.0.0.1:5900 >/tmp/novnc.log 2>&1 & sleep 2; echo "GUI ready -> http://127.0.0.1:6080/vnc.html"
```

执行完在手机浏览器打开 `http://127.0.0.1:6080/vnc.html` 就能看到桌面。

## 两个必须带上的参数（踩过的坑）

1. **Xvfb 要 `-extension MIT-SHM`**，**x11vnc 要 `-noshm`**。
   PRoot 容器里 `shmget()` 返回 `Function not implemented`（Android SELinux 不允许 SysV 共享内存），
   而 x11vnc 默认依赖 XShm 抓屏。不关掉的话端口虽然在监听，但客户端连上去拿不到画面，
   日志里只有一句 `shmget(scanline) failed`。
2. **不要漏 `-noshm` 时 x11vnc 会直接拒绝启动**，提示 `Restart with -noshm to override this.`

## 在这个桌面上跑 GUI 程序

```sh
export DISPLAY=:0
wechat &            # 或任何 X11 程序
```

微信 Linux 版还要中文字体：

```sh
apt-get install -y fonts-noto-cjk
```

## 只想要原生 VNC（不装 noVNC，少 130 个包）

```sh
apt-get install -y --no-install-recommends xvfb x11vnc
```

然后从手机上的 VNC 客户端（AVNC / bVNC 等）连 `127.0.0.1:5900`。
这条路径装得更快，但需要在 Android 侧装一个 VNC 客户端 App。

## 从电脑上连（调试用）

容器里的 `localhost` 就是设备的 localhost，所以 `adb forward` 直接把端口透到电脑：

```powershell
adb forward tcp:6080 tcp:6080
```

然后电脑浏览器开 `http://127.0.0.1:6080/vnc.html`。若要用原生 VNC 客户端直连 5900，
启动 x11vnc 时去掉 `-localhost` 即可（默认只监听回环，去掉等于把画面暴露到局域网，谨慎）。

## 已知限制

- 纯 CPU 渲染（Xvfb 软件光栅化），Electron 类应用会偏卡，鼠标滚动、动画尤其明显。
- 没有窗口管理器，窗口会叠在一起、无法拖动缩放。想好用可以再装 `openbox` 或 `xfce4`。
- 容器重启（App 重启 / 重新初始化）后 Xvfb、x11vnc、websockify 都要重新起一遍，目前没有做成常驻服务。
- 分辨率固定为启动时指定的 `1280x720`，热改要重启 Xvfb。

## 实测记录

在 Android 11 (API 30) x86_64 模拟器的 Ubuntu 24.04 容器里验证通过：

- 安装 148 个包（耗时约 20 分钟，模拟器上 proot 很慢；真机 arm64 会快很多）
- `Xvfb :0` 起来后 `x11vnc` 日志出现 `Read initial data from X display into framebuffer`、`Listening for VNC connections on TCP port 5900`
- RFB 握手返回 `RFB 003.008`，noVNC 页面 HTTP 200
- 设备上的 Chrome 打开 `http://127.0.0.1:6080/vnc.html`，能看到容器里 `xclock` 的实际画面
