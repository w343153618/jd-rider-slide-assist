# 1.3.1 更新文件发布验证

2026-10-31 的实际更新验收在 Android 模拟器上发现，旧实现试图使用 `Os.link` 保存已校验安装包时，Android SELinux 拒绝了该硬链接操作。修正后，应用在私有更新目录内使用同目录重命名；已有摘要路径先检查其文件摘要和 APK 身份，不能直接覆盖。

在独立 `fixtureCheck` 测试变体的应用私有目录执行了 Android instrumentation：

- 新摘要路径不存在时，临时文件成功重命名，测试 Provider 读出的字节与发布前一致。
- 若摘要路径已存在但内容摘要不匹配，发布被拒绝；原有字节不变，新临时文件未覆盖原文件。

该文件系统测试使用随机测试字节，单独验证保存及读取，不把它当成有效 APK；应用包名、版本和签名校验由其他更新器测试覆盖。已有正确摘要且签名正确的文件复用分支及完整线上下载安装需要另外验证。

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:assembleFixtureCheck :app:assembleFixtureCheckAndroidTest --offline
adb install -r app/build/outputs/apk/fixtureCheck/app-fixtureCheck.apk
adb install -r app/build/outputs/apk/androidTest/fixtureCheck/app-fixtureCheck-androidTest.apk
adb shell am instrument -w -r -e publish 1 \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
```

测试未操作生产 baseline 应用，未启用无障碍服务。测试结果不能代替真实手机或完整系统安装过程的验证。
# GitHub 下载与系统安装完整验收

2026-10-31，在同一隔离 Android 17 模拟器完成：

1. 安装私有测试基线1.2.99（versionCode 4），它包含待验收的更新器并使用正式发布的同一签名。该基线仅用于测试，未作为历史公开版本发布。
2. 从 App 点击检查更新，实际读取 GitHub 最新正式发布的 update.json，显示 v1.3.1；用户界面确认下载后，实际下载发布的 APK 和 SHA 文件并通过校验。
3. 打开系统安装来源授权页，开启本应用的安装授权，再返回 App；重新核验文件后再次确认安装，进入 Android 的“Update this app?”界面。
4. 在系统安装器确认 Update，应用成功更新并重新显示版本1.3.1。PackageManager/dumpsys 同时确认 versionCode 6、versionName 1.3.1。
5. 展开设置后，升级前的700毫秒选择仍为选中，六档参数完整。再次检查更新显示“当前已是最新版本”，远端 v1.3.1。

本次使用真实 GitHub 公开资产和 Android 系统安装器，不以 adb 覆盖安装代替最后的升级步骤。测试开始的基线安装使用 adb，测试结束的1.3.1来自应用内下载。它证明同一签名的升级流程，不代表早期 debug 包能够换签名覆盖升级，也不证明真实京东接单兼容。

发布 APK SHA-256：`cf4489bfab432276248331a94dd8f68b724ae01af801027dc6eae3862e072e9b`。GitHub API 资产摘要和本地产物一致。签名证书 SHA-256：`de508e5c22cb49ff0577afee5933d7197de1b4d9154fae9bd8768885af9999e7`。签名私钥和密码均未入库。

模拟器最终保留正式1.3.1供体验，生产应用的安装来源授权保持开启，测试夹具的安装授权仍为 default；无障碍服务关闭、adb为普通 shell。真实骑士 App 未安装，演示状态球只控制虚构订单。
