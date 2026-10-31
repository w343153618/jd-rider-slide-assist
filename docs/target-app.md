# 目标客户端来源

核对日期：2026-10-31。

用户确认客户端为“京东秒送骑士（原达达快送骑士）”。

- 京东秒送官网骑士页面：https://www.imdada.cn/transporter
- 小米官方应用商店条目：https://app.mi.com/details?id=com.dada.mobile.android
- 商店应用名称：京东秒送骑士；开发者：达疆网络科技（上海）有限公司。
- 商店公开包名：`com.dada.mobile.android`。
- 本次页面列示版本12.30.0、更新时间2026-08-03。该信息仅为来源核对时的商店快照，不能推断用户手机版本或全渠道最新版。

本项目只检查包名、安装版本与可启动性，没有进行安装包签名锁定，不能据此保证安装的是官方包。尚未取得真实订单页面的控件结构或触摸验收结果，当前以人工对齐浮层方式操作。

使用的 Android 官方接口：

- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)
- https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentForPackage(java.lang.String)
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent)
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_FILTER_KEY_EVENTS
- https://developer.android.com/reference/android/view/KeyEvent

音量快捷键通过服务的按键过滤能力观察事件，并返回 `false` 保留系统处理。该能力需要在服务元数据声明 `canRequestFilterKeyEvents` 并设置 `flagRequestFilterKeyEvents`；并非所有设备和同时启用的无障碍服务组合均能保证收到事件。短按组合键与系统的长按无障碍快捷方式需在用户手机上核对。
