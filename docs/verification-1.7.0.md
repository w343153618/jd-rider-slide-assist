# 1.7.0 浮窗大小验证

本轮遵守项目AGENTS要求，使用Addy的技能路由、增量实现、UI工程、先失败再通过的行为测试、独立五轴审查、Git版本和发布流程。范围见[本版规格](spec-1.7.0.md)。所有设备操作使用Android模拟器、虚构订单和自有接收App。

## 先失败的基线

新增原生回归按独立字面量检查64dp默认尺寸、菜单32～120dp、大小保存、两侧吸附、缩放时计时连续及可见度独立。在未实现尺寸功能的旧Main/Status源码上构建运行，实际失败：`Main status control must measure 64 by 64 dp, received 28.0 by 28.0`。日志保存在本地忽略目录，没有将失败算作通过。正式1.6.0也通过当次新UI层级核实28dp及无大小滑杆。

## 实现与证据

默认64dp，32～120dp整数范围。黑色圆角方窗与自绘绿色听筒、计时按实际尺寸缩放，大小与可见度分别保存。模拟页保持同一个View并调整布局；真实服务保持同一窗口View，正在拖动时延后更新尺寸。调整大小保持停靠侧并限制垂直位置，系统返回手势排除区域同步为控件自身矩形。[Android布局参数](https://developer.android.com/reference/android/widget/FrameLayout.LayoutParams)、[手势导航区域说明](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav)。

## 构建、设备与审查

- JDK21、SDK35、Gradle9.7.1、AGP9.4.0，版本1.7.0/code11。debug/release/fixtureCheck/AndroidTest构建成功；debug和release Lint均0 errors / 49 warnings，未屏蔽检查。告警主要延续中文内联文本、旧API和绘制检查；本轮增加尺寸文案及有意按物理左右定位的Gravity提示。
- 主机VolumeKeyChord、SwipeTiming、SliderGeometry、InteractionRecorder、TripleTapDetector、ReactionModel、BubblePlacement、BubbleTimer、UpdatePolicy通过。尺寸策略先因缺少新API编译失败，再实现32～120dp归一化与两侧尺寸定位后通过。
- 原生Main回归通过：64dp默认、32/64/120dp即时预览及持久化、10/55/100%可见度独立、缩放保持计时和停靠侧、边界内布局和手势排除矩形、最小/最大尺寸下三击及拖动。测试保存并恢复大小、可见度和六个音频流。
- 原生真实WindowManager服务窗口回归通过：32/64/120dp测量值、同一View缩放、ON计时连续、两侧停靠、按实际高度避让、拖动时延后应用新尺寸、松手立即应用；单/双不切换、三击、取消、多指、调整设置均未新派发订单手势。
- 连续模拟订单、三种触发、外起手不触发、同单自动复位后重试、独立11档模拟时长、三单刷新/计分/报告/旋转及固定返回的既有设备回归通过。
- 作者以外的Addy五轴审查覆盖Main布局、共享绘制/计时、服务窗口与新增测试，无Critical/Required遗留项。真实滑动路径、固定目标、锁屏及一次派发保护不变。

实际全局触摸从右侧距屏幕8px的位置起手拖到左侧，再从左侧8px起手拖回右侧，Main始终前台，两侧吸附正常；最终新UI层级确认64dp控件停靠右边。计时运行时UIAutomator仍可能等待idle超时，已使用即时截图与停表后的新层级核对，没有读取旧XML当作证据。

跨应用浮标整套检查第一次失败：系统回调completed，但接收端无新增DOWN/MOVE/UP。保留完整失败日志；其中InputDispatcher记录一个已移除overlay通道，但该记录也可能对应原物理抬手，不能据此断定根因。先暂停发布，独立审查输入链路；重新建立接收App和服务连接后，严格整套service hardware通过：立即/抬起/双击、100～700ms、原起点与行程误差小于3px、时长误差不超过10ms，接收端均收到事件。随后相同bubble hardware用例也通过：三击开关不产生滑动、一次触发后OFF、离开目标与熄屏隐藏。此前异常与历史1.5.0现象相似，本轮未将不确定性写成已修复，没有增加重试或随机操作来掩盖它。

## 正式产物

- APK SHA256：`72d6158ef219e60551a604d5f7d52254c69dd8d349182ae300389218c11be471`，738070字节。
- 签名校验通过；证书SHA256继续为`de508e5c22cb49ff0577afee5933d7197de1b4d9154fae9bd8768885af9999e7`，与1.6.0正式版一致。

发布与实际升级结果完成后补充回执。旧版标签与资产保留；对应微信版本截图未提供，接近用户描述的观感不等于逐像素一致。真实京东页面、实体手机和真实订单结果未验收。
