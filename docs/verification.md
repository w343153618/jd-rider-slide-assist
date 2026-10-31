# 验证记录（2026-10-31）

这是开发验证记录，不代表京东秒送骑士或任何实体手机已经通过兼容性验收。所有订单为虚构内容，跨应用接收端是独立测试靶场，不连接账号、订单或网络。

## 构建

在 macOS、JDK 21、Android SDK platform 35 / build-tools 35.0.0、Gradle wrapper 9.7.1 环境执行：

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:assembleDebug :app:assembleFixtureCheck \
  :app:assembleFixtureCheckAndroidTest :fixture:assembleDebug :app:lintDebug --offline
```

结果：构建和 lint 均通过；lint 报告 0 errors、16 warnings。普通 debug 包版本为 1.1.0（versionCode 2），最低 Android 7.0（API 24），目标包编译固定为 `com.dada.mobile.android`。独立验收变体的应用 ID、名称和目标包均与普通包不同；其目标只指向 `com.example.slideassist.fixture`，不安装、覆盖或连接骑士应用。

## 模拟订单

在 Android 17 模拟器运行原生 instrumentation，通过系统触摸事件测试：默认按下、区域外轻点或拖入、抬起模式按住及抬起、首指先抬且第二指未抬、区域外抬起、手势模式的单击/双击/双指/移动/三指以及订单间相互独立。全部通过。安装验收变体及测试包后运行：

```sh
adb shell am instrument -w -r \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
```

条件变异验证：临时反转模拟滑条的即时触发判断，测试按预期失败，指出“手指仍按住时默认模式应完成”；随后恢复原始代码、重新构建并再次通过测试。

## 跨应用滑动

测试只在隔离的 Android 17 模拟器上执行：安装 `fixture` 的 debug 包、`fixtureCheck` 变体及其 androidTest 包；启动测试后在模拟器开启验收变体的无障碍服务。模拟器的 `virtio_input_multi_touch_1` 为 DIRECT 触摸设备，测试对它发送内核触摸事件，绕开 UiAutomation 与无障碍注入共享虚拟设备 ID 的冲突。该测试依赖这个模拟器的 `/dev/input/event1`、0–32767 坐标范围及可用的 `su`，不能在实体设备或未知设备上直接照搬。

```sh
adb install -r fixture/build/outputs/apk/debug/fixture-debug.apk
adb install -r app/build/outputs/apk/fixtureCheck/app-fixtureCheck.apk
adb install -r app/build/outputs/apk/androidTest/fixtureCheck/app-fixtureCheck-androidTest.apk
# 仅在隔离的可 root 模拟器：
adb root
adb wait-for-device
adb shell am instrument -w -r -e service hardware \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
```

instrumentation 运行期间，从另一个终端执行以下命令开启测试服务；若服务因 instrumentation 启动时的进程重启未绑定，先删除该设置再重试：

```sh
adb shell settings put secure enabled_accessibility_services \
  com.example.slideassist.testfixture/com.example.slideassist.AssistService
adb shell settings put secure accessibility_enabled 1
```

执行后必须恢复测试前的无障碍设置及 adb 权限。本次模拟器原值分别为 `null`、`0`，结束时删除该项、写回 `0` 并执行 `adb unroot`；不要把这两个值假设为其他设备的原值。

本次执行结果：抬起模式、按下后持续按住、按下后快速抬起和双击模式均收到 completed 回调；每例测试靶场的完整滑动计数恰好增加 1，实际收到 DOWN、7 次 MOVE、UP，右移约 798 像素，持续 250 毫秒。按住场景中，靶场完成滑动发生在原触摸源抬起前。

这些结果证明模拟器内的输入接收和服务回调，不能证明京东页面是否接受该手势，也不能保证不同手机、屏幕、系统版本或骑士应用更新后的效果。跨应用双指模式及实体设备尚未验证；真实订单结果以骑士应用显示为准。
