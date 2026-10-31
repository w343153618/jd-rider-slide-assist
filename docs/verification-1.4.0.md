# 1.4.0 手速、轨迹和触摸起点验证

2026-10-31，在隔离 Android 17 / API37 模拟器、原生 Java / SDK35 / JDK21 上执行。测试目标始终为单独的 fixture 包，不登录骑士账号或接触真实订单。

## 触发与坐标

- 共享几何主机测试覆盖白色圆角手柄、橙色无效位置、不同密度、非中心抓取、完整行程、固定Y与边界。
- 模拟页完整手势回归通过；橙色区点击不触发，外起手移入不触发；连续练习、重置、手动关闭/重开及六档时长仍通过。
- 通过模拟器真实触摸设备验证三种模式的橙色起手移入白块均不派发；白色块内起手后，独立接收 App 的实际 DOWN X/Y 与首指初次按下坐标误差小于3px，终点平移完整行程、Y不变，非中心抓取偏移保留。
- 跨应用100/150/200/250/450/700ms、按下快抬/按住、抬起和双击通过；每次靶场滑动计数恰增1。opt-in时真实辅助记录有指头采样、dispatch与completed阶段，且不进入手速成绩。

## 状态球

TripleTapDetector 主机测试包括单击、双击、三击、第四击、350ms边界、超时、移动/取消重置及异常时序。临时副本把阈值改2，测试按预期失败。设备测试实际点按1次和2次均未开启，第3次开启；三击关闭，不产生滑动，离开目标与熄屏隐藏。

## 测量与报告

- 采集默认关闭；未点击开始前，手动完整拖动不产生记录。开始后橙色起手记无效，白色手柄手动拖到98%行程再抬手记成功。
- 实际注入的 Android 触摸事件有按下/移动/抬起原始点、target事件时刻。最快成绩严格按manual成功的target−down计算，辅助和无效试次排除。
- 原生Canvas逐项绘制有效手动、缺失目标的无效记录、只有首指点但有后续阶段的辅助记录，均无缺失阶段崩溃。辅助回放跨度包括最后阶段，不虚构系统手势采样。
- 实际播放推进游标、暂停/拖进度、Activity.recreate后保留会话和游标通过。
- JSON能解析全部类型，非有限压力值写null；CSV保留密度/关键阶段等点表上下文。另通过系统文档选择器真正保存JSON和CSV，等待后台写完后读回：1次模拟器注入的测试轨迹、6个原始点及阶段一致。随后仅清理本次创建的两个文件，保留其他下载内容。
- 可视化截图与10秒屏幕录制来自模拟器注入的曲线测试数据，用于展示功能，绝非朋友的人手实测成绩或真实压感/电流测量。

## 容量与统计

InteractionRecorderTest覆盖显式开关、stop/clear后旧引用不可写、200次/1024点/20000总点/16阶段上限、同时间MOVE/UP保留、去重/乱序计数、平均/中位/nearest-rank P95、非有限值、无有效时间差及并发快照。辅助类型隔离的临时条件反转会使测试失败。所有主机测试和UpdatePolicyTest通过。

记录只在进程内存；上限截断会计数，不能声称无限或完整采集。事件接收滞后并非触摸硬件/服务器延迟，采样间隔也不等于硬件扫描频率。pressure等设备原始字段不换算真实力；人体电流未提供。

## 构建与命令

最终debug/release构建和Android Lint均通过，分别为0 errors、35 warnings；签名证书与前版一致。警告主要包括目标SDK、旧平台回调、绘图分配、点击语义及文本/布局提示，未以压低检查标准消除警告。

```sh
tests/run-unit-tests.sh
tests/run-update-policy-tests.sh
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
./gradlew :app:assembleFixtureCheck :app:assembleFixtureCheckAndroidTest :fixture:assembleDebug
adb shell am instrument -w -r -e v14 1 \
  com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
```

默认instrumentation运行模拟回归；`-e service hardware`与`-e bubble hardware`沿用隔离模拟器内核输入验收，按早期验证记录启用fixture服务后运行。不得在实体设备或未知输入设备运行内核测试。结束恢复无障碍null/0，adb保持普通shell。

真实京东页面、朋友手机的实际控件尺寸/厂商按键分发/真实接单结果仍未验收。实际客户端仍需人工对齐完整橙条和白色手柄触发框。

## 正式安装包升级

模拟器中使用正式1.3.2的应用内更新入口，实际下载GitHub v1.4.0的APK、校验文件摘要/包名/版本/签名，经过Android系统安装器确认后升级。界面及PackageManager均确认1.4.0/code8。签名证书与旧版一致，APK SHA-256为 `ab48a8b80f8cdea885f9d790506eb95fce5e513b953766f03cceb3f7d18bf949`，与GitHub资产摘要一致。

正式包升级后已确认新增手速入口和版本信息；状态球单次/双次不切换、第三次切换的运行结论来自前述fixture设备测试，未把补充现场脚本未定位到状态球的结果算作正式包通过。模拟器保留1.4.0供体验。最终无障碍null/0、adb普通shell；仅删除本轮创建的临时输入脚本、录制文件和两份导出样本，其他下载内容未动。
