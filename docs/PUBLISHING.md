# 发布到 GitHub

## 首次公开源码

将本仓库根目录作为 Git 根目录，保留隐藏的 .github 和 .gitignore。GitHub Desktop 可从「Add Local Repository」选择该目录，创建首次提交后使用「Publish repository」选择账户、仓库名和公开范围。也可以从命令行提交到自己的空仓库。使用 Git 客户端可保留 gradlew 的可执行权限。

## 发布 Android 安装包

1. 以 JDK 17 和 Android SDK 35 执行：
   ```sh
   ./gradlew clean testDebugUnitTest lintDebug assembleRelease
   ```
2. Android Studio 中打开 **Build → Generate Signed App Bundle / APK → APK**，使用维护者长期保管的 release keystore，选择 release 构建。命令行构建生成的 app-release-unsigned.apk 需要先签名。把密钥和密码保存在仓库之外，备份签名密钥。
3. 用 Android SDK Build Tools 的 apksigner 校验最终签名 APK。在测试设备上检查首次安装或同签名升级。已安装调试签名版本的设备通常需要先卸载才能换正式签名，卸载会清除手机本地配置，请先保存连接信息。后续升级沿用首次正式签名。
   ```sh
   apksigner verify --verbose --print-certs AriaFlow-1.9.5.apk
   shasum -a 256 AriaFlow-1.9.5.apk > SHA256SUMS.txt
   ```
4. 在 GitHub Releases 创建 v1.9.5 标签并指向验证过的源码提交。使用本目录的发布说明，附上签名后的 APK 和对应的 SHA256SUMS.txt。发布源码本身无需 Android 签名密钥。

仓库的 GitHub Actions 在推送、拉取请求或手动触发时运行构建、单元测试和 Lint。工作流仅读取仓库内容，正式签名和上传由维护者完成。
