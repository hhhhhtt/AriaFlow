# 素材与第三方声明

仓库根目录的 MIT 许可证适用于项目原创代码。以下资源另行说明。

## 桌面图标

海洋蓝、薄荷、森林、沙色、珊瑚和石墨图标由维护者使用 Claude 生成。维护者确认创作时未参考第三方品牌标志。六张桌面图标及其衍生单色图层随项目提供；根目录 MIT 许可范围不包含这些品牌图形。此说明记录素材来源；独立使用品牌图形请联系维护者。

## 随源码分发的第三方文件

- 暂停和播放图标源自 Google Material Symbols Rounded，使用 Filled 版本的路径，已转换为 Android VectorDrawable。采用 Apache License 2.0。[上游图标](https://github.com/google/material-design-icons) · [随 APK 分发的许可证](app/src/main/assets/licenses/material_symbols_license.txt)
- Gradle Wrapper 脚本及 JAR 来自 Gradle 8.11.1。保留上游许可证和声明。[Gradle LICENSE](licenses/Gradle-LICENSE.txt) · [Gradle NOTICE](licenses/Gradle-NOTICE.txt)

## 构建依赖

AndroidX、Compose、Material 3、Kotlin、kotlinx.coroutines、OkHttp、Haze 以及测试工具按各自上游许可分发。直接依赖与版本见构建脚本，Gradle 可列出完整传递依赖。aria2 在用户的服务器上独立运行，应用通过 JSON-RPC 通信。[应用构建脚本](app/build.gradle.kts)
