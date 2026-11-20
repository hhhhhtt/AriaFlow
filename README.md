# Aria Flow

Aria Flow 是 Android 上的 aria2 远程下载管理器。用手机向 NAS 添加链接或种子、查看进度、暂停或恢复任务，下载文件保存在服务器上。

**版本 1.9.5 · Android 8.0 及以上 · Kotlin / Jetpack Compose / Material 3**

## 功能

- 按活跃、等待和已停止分组，支持搜索、排序、单项与批量暂停和恢复。左滑或长按可移除任务，移除前会再次确认。
- 添加 HTTP、HTTPS、磁力链接，或通过系统文件选择器导入最大 4 MiB 的种子文件。
- 最近 60 秒上传和下载速度曲线，前台定时更新。任务排序中的创建时间，对外部添加的任务采用首次发现时间。
- 保存并切换多个下载器，读取和修改当前服务器的全局限速及同时下载数。
- 跟随系统、浅色和深色外观，六款可选桌面图标。

## 连接下载器

1. 在 NAS 上启动支持 JSON-RPC 的 aria2 服务，确认手机可访问对应端口。
2. 打开「设置 → 下载器」，填写名称，点击「填入示例」并修改主机地址。标准地址形如 `http://192.168.1.100:6800/jsonrpc`。
3. 在密钥输入框填写服务器配置的 RPC secret 原值。点击「测试连接」检查连通性，点击「保存」保存配置并切换服务器。
4. 在「添加」页提交任务。下载偏好中的 `0` 表示不限速，`500K` 表示 500 KiB/s，`10M` 表示 10 MiB/s。重启后要保留设置，需同时配置服务器端。

应用使用 Android Keystore 保护本机保存的 RPC secret。局域网 HTTP 会以明文传输 RPC 请求和令牌；远程连接请使用可信 HTTPS 或 VPN。反馈问题时请遮挡真实地址、令牌、任务链接与文件名。

## 从源码运行

在 Android Studio 打开仓库根目录，选择 JDK 17，安装 Android SDK Platform 35 与 Build Tools 35.0.0，运行 `app`。也可在已配置 Android SDK 的终端执行：

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Windows 使用 `gradlew.bat`。首次构建需要下载 Gradle 和 Maven 依赖。调试安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 许可与发布

原创代码采用 MIT，署名 Aria Flow contributors。桌面图标和第三方资源的范围另见许可说明。[MIT 许可证](LICENSE) · [素材与第三方声明](THIRD_PARTY_NOTICES.md)

维护者可查阅[发布指南](docs/PUBLISHING.md)、[1.9.5 发布说明](docs/RELEASE-1.9.5.md)与[界面规范](AGENTS.md)。
