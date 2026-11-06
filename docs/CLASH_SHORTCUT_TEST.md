# Clash Meta 快捷按钮测试版

这是个人 Fork 的实验功能，尚未在电视上验证，不是上游官方版本。

## 使用

1. 电视上已有 Clash Meta for Android：支持 `com.github.metacubex.clash.meta` 和 Alpha 的 `com.github.metacubex.clash.alpha`。两者都安装时优先稳定版。
2. 在 Clash 内选好配置，手动连接一次，完成系统 VPN 授权。
3. 安装并从应用列表打开 **Aerial Shortcuts Test**。
4. 控制中心 → 个性化 → **Clash Meta 快捷按钮 → 开**。
5. 返回控制中心，在 **快捷操作 → Clash Meta → 切换** 上按确认键。
6. 去 Clash 客户端确认实际运行状态，再测试一次关闭。

按钮发送官方 `TOGGLE_CLASH` Intent，由 Clash 决定开启还是关闭。发送成功不代表连接成功；配置和首次 VPN 授权仍由客户端完成。系统 VPN 标识可能属于其他应用，不能作为 Clash 独立状态显示。

测试版包名 `app.orchard.tv.shortcuts`，与官方版共存，设置独立。采用 guard 变体，不声明 HOME，也不需要将其设为默认桌面。先作为普通应用测试即可。若官方 Aerial 正在运行，主页键会回到官方版，再从应用列表打开测试版即可。

测试构建从 Manifest 中移除了桌面守护、通知监听、同步账户、开机接管和系统屏保组件，以及修改系统设置和悬浮窗的权限；不会与官方版注册相同的同步账户。这次验证只需要普通应用权限。不要将测试版设为开机启动或系统桌面。测试包不提供应用内升级，后续版本从 Actions 下载覆盖安装。

## 安装

可用 U 盘安装 APK。使用 ADB 且电视为 Android 9 及以上时，按项目规范同时安装 APK 内的 dex metadata：

先从两份 `.dm` 中选出与系统匹配的那份，复制或重命名为 **`aerial-shortcuts-test.dm`**，与 APK 主文件名保持一致：Android 12 及以上选 `api31.dm`，Android 9–11 选 `api28.dm`。

```text
# 先连接自己的电视，示例地址须替换
adb connect <电视IP>:5555

# Android 9 及以上（先按上文选好并重命名 .dm）
adb install-multiple -r aerial-shortcuts-test.apk aerial-shortcuts-test.dm

# Android 5–8
adb install -r aerial-shortcuts-test.apk

adb shell am start -n app.orchard.tv.shortcuts/app.orchard.tv.MainActivity
```

只有无线调试的电视需要按原项目说明先配对。签名密钥固定保存于 Fork 的 Actions Secret，后续构建可覆盖此测试版；不能覆盖官方包。

TCL 若返回 `INSTALL_FAILED_VERIFICATION_FAILURE`，后台安装未成功；可改用电视已有的多屏互动安装入口，由用户确认。不要直接运行原项目完整安装脚本来装这个测试包，那会配置官方桌面及守护服务。

## 验收清单

- 开关默认关闭；开启后重启测试 App，设置保留。
- 正确配置并授权时，按钮能开启、关闭 Clash；系统网络名称仍正确。
- 未配置或未授权时不把“请求已发送”当作连接成功，在 Clash 内完成后再试。
- 客户端不存在、被停用或启动被拒绝时有提示，Aerial 不崩溃。
- 遥控器可以从音量、亮度移动到快捷操作及后续菜单，底部内容可以滚动到。
- 儿童／长辈模式不显示快捷操作；有家长 PIN 时操作遵守现有解锁规则。
- 原官方 Aerial 及其设置正常。

测试完成后卸载 `app.orchard.tv.shortcuts` 即可移除测试版，官方版保留。

## 开发与构建

GitHub Actions 在 `feat/clash-meta-shortcut` 分支推送时自动运行：两种变体的 JVM 测试，然后构建带 R8 的 guard release 测试包，输出 APK、两份 `.dm`、校验和和测试报告。不依赖本地 Android Studio 或 SDK。

首次设置 Fork 需自行生成 PKCS12 测试签名密钥，以 `aerial-test` 为别名、`aerial-test-only` 为测试密码，将文件 Base64 保存到 `SHORTCUT_TEST_KEYSTORE` Secret。该密钥只用于此独立测试包，不能用于生产包，不能提交到仓库。CI 的测试密码不是密钥本身。

本分支只实现内置 Clash 操作，没有通用 Intent 编辑器，不下载或安装任何第三方应用，不改系统桌面设置。Lite 未改动。待实机验收后，再整理是否向上游提交 PR。
