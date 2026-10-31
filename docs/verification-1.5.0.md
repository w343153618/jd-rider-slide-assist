# 1.5.0 自动复位与找字反应测试验证

本轮按项目强制Addy Osmani流程实现。规格见`spec-1.5.0.md`，使用记录见`skills-workflow.md`。所有输入与订单均为自有测试数据；没有登录或测试真实京东订单。

## 构建与主机验证

- JDK21、Android SDK35、Gradle9.7.1、AGP9.4.0；release版本1.5.0/code9。
- debug、release、fixtureCheck、fixtureCheckAndroidTest与独立fixture均构建成功。
- debug和release Lint均为0 errors / 43 warnings。没有隐藏或压低Lint规则；现有告警包括目标SDK/备份/图标、绘制分配、静态服务引用、国际化和RTL等。新增页面沿用中文内联文案，国际化仍有告警。
- 主机测试：VolumeKeyChord、SwipeTiming、SliderGeometry、InteractionRecorder、TripleTapDetector、ReactionModel、UpdatePolicy全部通过。
- 500组随机种子覆盖六单、唯一普通“田”、全部字段更换、可复现输入和长度/位置多样性；统计用已知数值核对平均/中位/P95/标准差，覆盖预呈现、错误、多指/外起手、失败重试、重复计分与上限/不可变快照。

## RED、修复与审查

- 旧实现的新设备用例在“manual slider must reset without a reset button”处失败；实现200ms复位后通过。反转“已无物理手指”的复位条件会使测试再次失败，恢复源码后重建并重新通过。
- 50ms规范化旧实现返回250ms，新增失败用例后实现新档位；独立Trace.snapshot接口先缺失失败，随后实现并证明与后续写入隔离，反应记录不进入独立手速榜。
- 互审纠正重试次数：第一笔有效起手后才有重试；首次失败为0，第二笔有效操作为1，重复练习和无效起点排除。
- 互审发现子滑条在多指序列中提前释放父拦截，剩余手指可能触发刷新。设备失败用例复现后，改为子控件认领的整次触摸直到新DOWN都不具备刷新资格，重跑通过。
- 控件、模型、主页面、返回入口、报告/导出与测试接收端分别接受作者以外的五轴审查；必改项已修正。没有增加运行时依赖、真实订单选择、刷新循环或重试。

## Android模拟器设备检查

环境为自有Android17/API37模拟器，1080×1920、ranchu，测试构建绑定独立接收App。数据不是朋友手指的手速成绩。

- `-e v15sliders 1`：200ms之前不复位、之后就绪，全部抬手前不复位；连续操作、次数/路径/独立阶段时间保留；取消、未完成、多指、移除视图、显式启用和原始点上限通过。
- `-e v15main 1`：11档显示，50/60/70/80/90ms逐档保存，真实辅助偏好不变；手速页滚到底后返回按钮仍可见并直接返回模拟页。
- `-e v15reaction 1`：进入前其他页面已有全局记录仍不隐式采集；字符本身的布局可见性；错单/目标/重复练习；刷新后全字段变化且恰有一个田；完整本地JSON/CSV序列化、NaN和缺失时刻为null；旋转保留与固定返回通过。
- 既有默认模拟回归：三模式、手柄外和移入不触发、不同订单连续操作、关闭/重开、自动复位再练、700ms实际动画及重开保留通过。
- `-e v14 1`既有手动记录/轨迹回归：按下到首次98%时间、与辅助结果隔离、JSON/CSV、缺失阶段图、播放/拖动及旋转进度通过。

## 跨应用严格路径检查和不确定性

本轮生产`AssistService`和`SliderGeometry`相对1.4.0未改。保留起终点/行程/Y误差小于3px、时长差不超过10ms、每次恰有一个新完成滑动的原有断言，没有直接放宽容差。

首次默认接收端运行在150ms出现终点920.5967px，而预期909px，约12px偏差。原始失败日志保留；当时尚未记录重采样标志，因此不能声称原因已唯一定位。加入只观察的API35 `PointerCoords.isResampled`日志后，默认模式整套重跑通过；日志确实出现`resampled=true`的MOVE小数坐标，而本次UP为原始终点。

Android文档说明系统可能对触摸做插值/外推，并提供重采样标志，因此“Android交付样本”不能等同触控芯片的未处理数据。本版报告补充这一限定。[PointerCoords官方说明](https://developer.android.com/reference/android/view/MotionEvent.PointerCoords#isResampled())

自有接收端另外提供默认关闭的`unbuffered`测试参数。首次A/B运行第二个用例未收到新完成事件；保留失败记录，没有算通过。随后在测试手指按下前等待两个显示帧并确认仍开启，让用例从已等待显示的控件起手；严格整套无缓冲检查通过，且逐次确认`unbuffered=true`和终点`resampled=false`。两帧是有界的测试准备条件，不修改真实辅助逻辑，也不能反推前次失败必然来自窗口注册。

最终默认与无缓冲接收端各有完整通过记录。无缓冲模式只控制自有靶场，不能控制京东客户端如何交付输入，更不保证在所有设备降低延迟。[View官方说明](https://developer.android.com/reference/android/view/View#requestUnbufferedDispatch(android.view.MotionEvent))

## 发布与现场边界

- 正式APK沿用原正式签名，证书SHA256 `de508e5c22cb49ff0577afee5933d7197de1b4d9154fae9bd8768885af9999e7`。
- APK SHA256 `6e90ac6bd03241b11b3e41d482d1598d801e7b2d21bf6a80813e521324923f28`，732310字节。
- [v1.5.0正式Release](https://github.com/w343153618/jd-rider-slide-assist/releases/tag/v1.5.0)固定提交`595a4dc6feffec995ebd3381e51ba3bb8bc330c2`，APK/SHA256/update.json三项资产已上传。重新下载并核对APK、摘要文件、GitHub资产digest和本地产物一致；旧标签与Release保留。
- 数据只在进程内存，50轮/200尝试和原始点容量均有上限。离开/旋转中断当前计时，已有结果保留；在尚未呈现的一帧内离开再返回，可能需点开始测试重新显示练习订单，不会产生计分。
- 本轮没有真实京东页面、实体手机、硬件光学时延、真实按压力/人体电流或平台许可验收；系统手势完成也不等于实际接单。

## 实际导出与正式升级

在fixture的实际页面显式开始测试，用adb合成一次手动滑动，随后通过Android系统文件保存器分别保存JSON/CSV。等待后台写入完成后读回：JSON 9775字节，CSV 499字节，均可解析；含6张虚构订单、一次目标成功、10个输入样本，阶段时刻与170ms到达值一致。该数值来自测试输入，不是人的成绩。初次在异步保存完成前过早拉取曾得到空文件，完成后重读与解析通过；没有把第一次空读算作导出失败或通过。

只有这两份本轮创建的测试导出文件在核对后删除，其他下载内容未动。布局渲染截图和实际adb屏幕截图分别标识；实际截图显示了练习订单、普通样式田、固定返回栏和报告。截图和设备日志仅保存在本地忽略目录，不发布为用户数据或真人成绩。

正式1.4.0通过应用内更新检查发现v1.5.0，实际从GitHub下载并通过摘要/包名/版本/同签名校验，点击安装更新并经Android系统Update确认完成覆盖安装。首页与PackageManager均确认1.5.0/code9。没有用adb安装替代这次正式升级流程。

最后恢复无障碍启用服务为null、accessibility_enabled为0，adb仍为普通shell。独立fixture Lint另外完成，0 errors / 5 warnings。
