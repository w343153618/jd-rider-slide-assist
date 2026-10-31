# 音量键和滑动时长验证（2026-10-31）

验证在隔离的 Android 17 userdebug 模拟器和本项目的 `fixtureCheck` 构建上进行；目标是独立测试应用 `com.example.slideassist.fixture`，不连接骑士账号或真实订单。生产构建仍只针对骑士应用。本记录不代表实体手机或真实骑士页面已通过测试。

## 自动化结果

- 音量键：真实 Android 按键过滤路径通过。单个音量键和两键按下间隔超过 200ms 均未开启；同一设备两键先后按下开启辅助，快捷键本身没有发送滑动；持续按住时的重复按键未再次切换，两键全部抬起后的反向组合键关闭辅助；其他应用位于前台时组合键未开启辅助。
- 时长：独立靶场逐次收到完整 DOWN/MOVE/UP，实际 MotionEvent 时间差分别为默认 250ms、450ms、700ms；每次完整右滑计数恰增 1，回调为 completed。正常触发、抬起触发及双击模式的跨应用回归也通过。
- 界面与练习：选择 700ms 后，模拟滑条在 350ms 时尚未完成，随后完成；重开 Activity 后仍选中 700ms。既有模拟手势和多订单独立性测试通过。
- RED：功能未实现时，独立音量键测试因不存在 `VolumeKeyChord` 按预期失败。

自动注入到 UiAutomation 的音量键虽然到达 Android 音量系统，却未进入被测服务的 `onKeyEvent`，不能用于声称按键过滤通过。最终测试由 `app/src/androidTest/cpp/volume_uinput.c` 在模拟器中创建临时 uinput 硬件键盘设备，经 InputReader 和系统无障碍按键过滤链路送达；进程结束时设备被销毁。它仅用于测试，不进入生产 APK。开始测试前记录并在结束后恢复六个音量流，逐项核验恢复值一致。

## 重现环境与命令

本次使用 JDK 21、Android SDK 35、NDK 29.0.14206865、macOS 的 Android 模拟器（ranchu，arm64，userdebug），模拟器须支持 `su 0`、`/dev/uinput` 和对应的 `/dev/input/event1`。测试代码对内核设备操作限制在 ranchu 模拟器，并使用独立 fixture 构建；不要在实体手机或未知设备运行这组内核事件测试。

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:assembleFixtureCheck :app:assembleFixtureCheckAndroidTest :fixture:assembleDebug --offline

# 将 NDK 路径按本机实际情况调整；下例是本次 macOS 环境。
NDK="$HOME/Library/Android/sdk/ndk/29.0.14206865"
"$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android24-clang" \
  -O2 -Wall -Wextra -o /tmp/slide-volume-uinput \
  app/src/androidTest/cpp/volume_uinput.c
adb push /tmp/slide-volume-uinput /data/local/tmp/slide-volume-uinput
adb shell chmod 755 /data/local/tmp/slide-volume-uinput
adb install -r fixture/build/outputs/apk/debug/fixture-debug.apk
adb install -r app/build/outputs/apk/fixtureCheck/app-fixtureCheck.apk
adb install -r app/build/outputs/apk/androidTest/fixtureCheck/app-fixtureCheck-androidTest.apk

# 模拟订单和 700ms 界面选择：
adb shell am instrument -w -r \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation

# 在独立终端开启测试服务（见下文）后，分别运行：
adb shell am instrument -w -r -e volume 1 \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
adb shell am instrument -w -r -e service hardware \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
```

instrumentation 启动可能重启被测进程；本次是在 instrumentation 启动后、等待服务期间，从另一终端设置：

```sh
adb shell settings put secure enabled_accessibility_services \
  com.example.slideassist.testfixture/com.example.slideassist.AssistService
adb shell settings put secure accessibility_enabled 1
```

执行完毕应恢复测试前的无障碍设置，不应假设其他设备的初始值。本次原值为 `null` / `0`，已恢复并核验；adb 保持普通 shell。临时 uinput 设备与 `/data/local/tmp/slide-volume-uinput` 已清除。

系统和厂商可能处理音量键组合、键盘重复事件及浮层的方式不同。本次没有在朋友的手机、真实骑士应用、锁屏场景或其他厂商系统中验证音量键入口；真实使用时须观察页面状态和实际订单结果。

## 状态球和模拟订单并发回归

新增状态球后的隔离 fixture 测试通过：目标应用前台且未开启辅助时显示关闭态；点按可以开启和关闭，且不会触发滑动；一次辅助滑动结束后恢复关闭态；切离目标或关闭屏幕时隐藏。模拟练习中，完成触发即关闭状态球；700ms 的第一条动画仍在进行时轻点第二条，第二条不会启动辅助滑动。模拟器休眠测试结束后已重新唤醒。

使用前述 fixture 环境，在 instrumentation 已启动并从另一终端开启测试无障碍服务后运行：

```sh
adb shell am instrument -w -r -e bubble hardware \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
```

## 更新器离线安全及生命周期测试

更新器测试未访问真实账号或真实订单。测试通过版本号和大小比较、可信 HTTPS 主机和发布文件 URL、校验和文件格式、只读 Provider 及不合法 URI 拒绝；当前版本、不同包名、相同包名但以另一把临时密钥签署的更高版本 APK 均被拒绝。测试用错误签名 APK 由下面脚本在临时目录生成，不进入生产包或仓库；测试后可删除模拟器临时文件。

```sh
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
  app/src/androidTest/tools/prepare-wrong-signer.sh
adb shell am instrument -w -r -e update 1 \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
adb shell rm /data/local/tmp/slide-wrong-signer.apk
```

另外将测试应用的“允许安装未知应用”权限分别预置为拒绝和允许，再各自启动 instrumentation：拒绝时重建后显示未授权；允许时重建后重新核验伪造的待安装文件并拒绝，两种情况均未打开安装器。Android 会在安装来源权限变更时杀死应用，因此测试必须分进程进行。测试后该测试包的 appops 已恢复为 `default`。这不等于真实新版本下载、签名一致的安装和升级过程已通过；完整升级需另行验收。

```sh
adb shell appops set com.example.slideassist.testfixture REQUEST_INSTALL_PACKAGES ignore
adb shell am instrument -w -r -e pending denied \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
adb shell appops set com.example.slideassist.testfixture REQUEST_INSTALL_PACKAGES allow
adb shell am instrument -w -r -e pending allowed \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
adb shell appops set com.example.slideassist.testfixture REQUEST_INSTALL_PACKAGES default
```
