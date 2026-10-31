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
