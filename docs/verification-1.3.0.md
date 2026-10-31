# 1.3.0 快速时长和更新元数据验证

验证于 2026-10-31 在 Android 17 userdebug 模拟器的独立 `fixtureCheck` 变体完成；目标为项目自有测试靶场，没有使用真实骑士账号或订单。未改动模拟器中的生产包。

- 六个时长选项均显示，250 毫秒仍为默认值；100、150、200 毫秒选项可分别选择和保存。模拟订单现有的三种触发方式、状态球、防止第二单误触及 700 毫秒设置的重开保留测试通过。
- 在跨应用靶场，通过模拟器 DIRECT 触摸设备触发并由靶场记录，六档 DOWN 到 UP 的实际事件时间分别是 100、150、200、250、450、700 毫秒；每一档均完成一次向右滑动，辅助服务回调 completed。新增三档分别收到 3、4、6 次 MOVE，横向位移均约 798 像素。
- 使用 Android 的 `JSONObject` 验证固定 `update.json` 解析：合法数据成功；草稿、预发布、缺失或非布尔发布状态、重复/缺失资源和不可信资源 URL 均拒绝；过长更新说明按显示上限截断。版本、URL、摘要格式、Provider 只读、同版本、不同应用包和不同签名证书拒绝测试均通过。

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:assembleFixtureCheck :app:assembleFixtureCheckAndroidTest :fixture:assembleDebug --offline

adb install -r fixture/build/outputs/apk/debug/fixture-debug.apk
adb install -r app/build/outputs/apk/fixtureCheck/app-fixtureCheck.apk
adb install -r app/build/outputs/apk/androidTest/fixtureCheck/app-fixtureCheck-androidTest.apk
adb shell am instrument -w -r \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
adb shell am instrument -w -r -e service hardware \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
```

跨应用测试须在 instrumentation 等待时，从另一终端启用 `com.example.slideassist.testfixture/com.example.slideassist.AssistService`。硬件触摸部分依赖本次模拟器的 `/dev/input/event1`、0–32767 坐标范围及 `su 0`，仅供隔离模拟器使用。操作前记录无障碍服务的原值，完成后恢复；本次恢复为 `null` / `0`，adb 保持普通 shell。

更新元数据和 APK 身份拒绝测试先按 [音量键验证记录](verification-volume-keys.md) 生成一次性错误签名的测试 APK，然后运行：

```sh
adb shell am instrument -w -r -e update 1 \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
adb shell rm /data/local/tmp/slide-wrong-signer.apk
```

这些结果仅证明本次模拟器和独立靶场的行为。100、150、200 毫秒是否能被真实骑士页面接受、不同手机是否能稳定完成滑动，以及完整线上更新安装流程，仍需分别验证。
