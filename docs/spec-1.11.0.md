# 1.11.0 逐单静态颜色标记

日期2026-11-01。用户指定南邮红、南航绿、南理工蓝，并补充同单/同批多匹配表达；随后明确全屏变色影响阅读，改为只在对应订单上标记。范围仅本App虚构订单关键词练习。

## 交互契约

- 移除全屏变色、呼吸、闪烁覆盖层，订单卡片保持原白底。仅在已开启标记的命中卡片左侧显示5dp静态窄色条，不遮挡地址/文字/滑条；没有任何循环动画或系统动画偏好观察。
- 固定配色：南邮#dc2626红、南航#15803d绿、南理工#2563eb蓝。文字使用可读的同色深色#7f1d1d/#14532d/#1e3a8a，并写学校及颜色名，不仅依赖颜色。
- 单学校一段色条；同一订单多个学校时，将该单自己的窄色条按南邮/南航/南理工顺序分为相应色段，标签同时列全“南邮（红） · 南理工（蓝）”。原始四字段内的实际关键词同色着色，不混成另一颜色。
- 顶部固定摘要按学校分别列出全部命中订单序号，例如“南邮（红）：第1、3单”“南航（绿）：第2单”“南理工（蓝）：第3单”。同校同单多次出现只计该单一次；手动操作后卡片仍保留订单编号，始终能对应顶部摘要。
- 默认关闭，开启后处理当前批全部已加载订单，含屏幕外。关闭/离开/后台清除卡片色条及卡片原文/标签的颜色span；前台恢复按本页开关重新展示。固定颜色说明可以保持着色，不能作为订单匹配数据。
- 生成器每批6～8张全虚构订单、三个学校是否出现各独立35%；出现时可放在1～2个不同订单，不同学校可落在同一单。保留与前批同位四字段变更、种子可复现、不可变数据及零匹配场景。
- 保留纯手动滑条、200ms回位、下拉换批、固定返回/开关、重建保留实际当前批。标记不产生任何自动抢单、滚动或真实客户端读取。

## 实现与验证顺序

1. 纯Java Palette：三色/深色文字/颜色名、规范顺序去重及不可变列表；缺实现RED后GREEN。生成器增加同单多词/同校多单的有界种子测试，旧实现应失败后再改。
2. 原生RED在旧整屏实现上验证全屏层仍存在，明确失败；实际每单颜色/不遮挡/关闭透明及操作后保留编号均有断言。
3. 删除RedBreathingView，以无动画、无触摸拦截的KeywordOrderMarkerView作为每卡局部子视图；Activity汇总原始订单所属编号、展示标签/文字着色，不再放置全屏层。
4. 原生检查每单单红/绿/蓝、2/3色顺序、同单多标签、同校多单编号、原文跨度、白底不染色、无匹配/关闭透明、手动/刷新/离屏摘要/生命周期完整回归。作者之外Addy五轴审查后发布1.11.0/code15，同签名应用内升级验收。

原生Java/SDK35/min24，沿用现有目录、简单final类和缓存Paint，无新权限/运行时依赖。文字颜色使用公开[ForegroundColorSpan](https://developer.android.com/reference/android/text/style/ForegroundColorSpan)，窄条使用[Canvas.drawRect](https://developer.android.com/reference/android/graphics/Canvas)。

```sh
tests/run-unit-tests.sh
tests/run-update-policy-tests.sh
./gradlew :app:assembleDebug :app:assembleRelease :app:assembleFixtureCheck :app:assembleFixtureCheckAndroidTest :app:lintDebug :app:lintRelease --offline
adb shell am instrument -w -e v111color 1 com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
adb shell am instrument -w -e v110keyword 1 com.example.slideassist.testfixture.test/com.example.slideassist.GestureInstrumentation
```

仅使用本App虚构数据及自有测试构建，媒体/日志/签名材料不入Git；真实骑士客户端、实体手机和50/100ms识别延迟仍未验收。遵循AGENTS的Addy TDD、增量、UI、独立审查与版本流程；1.10旧规格/标签/发布资产作为历史保留，不重写成当前静态行为。
