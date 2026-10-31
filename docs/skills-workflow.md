# 开发流程记录

## 项目强制规则

2026-10-31用户要求：本项目只要修改代码就必须使用Addy技能。已写入根目录`AGENTS.md`并提交；适用于主代理、委派代理、测试、脚本与构建改动。按改动选择并读取相关技能，记录验证与独立审查，不因改动小而跳过。规则不意味着每次预加载所有技能。

## 1.5.0 实际应用

- using-agent-skills / spec-driven-development：按用户已确认的手动测试方式界定能力与测量语义，规格见`docs/spec-1.5.0.md`。
- incremental-implementation：先建立纯数据与统计模型，再改控件复位，最后集成页面/导出，按功能提交。
- test-driven-development：旧控件自动复位失败、50ms被归一为250ms失败、独立轨迹快照缺失失败、下拉刷新多指边界失败后再实现修复；统计保护与复位条件做隔离变异验证。
- frontend-ui-engineering / source-driven-development：原生固定返回栏、独立模拟档位、普通样式目标字、可滚动报告；核对Android事件uptime、postDelayed、帧回调、返回栈与系统文档保存API。
- code-review-and-quality：控件/模型/主页面/报告互审；修正重试次数定义与多指下拉拦截边界。运行结论、未覆盖内容及实际Lint结果见`docs/verification-1.5.0.md`。
- git-workflow-and-versioning / shipping-and-launch：同签名新版本、不可变历史Release、更新清单与产物核对；不把夹具测试当真实京东验收。

## 初始流程

本轮使用 addyosmani/agent-skills 0.6.15，按任务需要加载：

- using-agent-skills：选择流程与验证边界。
- incremental-implementation：目标绑定、UI、跨应用验证分步交付。
- source-driven-development：核对目标包名和 Android API。
- test-driven-development：保留触摸行为用例，补跨应用真实接收端。
- code-review-and-quality：作者之外的审查，覆盖错误派发与测试误报。
- security-and-hardening：固定目标、最小包可见性、测试变体隔离、公开前敏感数据排除。
- git-workflow-and-versioning：固定构建版本、公开仓库提交与远端回读。

真实订单与平台许可未被模拟器测试覆盖，必须单独记录。工程规则见 AGENTS.md；最终测试与审查事实见 verification.md。
