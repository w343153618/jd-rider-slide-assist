# 开发流程记录

本轮使用 addyosmani/agent-skills 0.6.15，按任务需要加载：

- using-agent-skills：选择流程与验证边界。
- incremental-implementation：目标绑定、UI、跨应用验证分步交付。
- source-driven-development：核对目标包名和 Android API。
- test-driven-development：保留触摸行为用例，补跨应用真实接收端。
- code-review-and-quality：作者之外的审查，覆盖错误派发与测试误报。
- security-and-hardening：固定目标、最小包可见性、测试变体隔离、公开前敏感数据排除。
- git-workflow-and-versioning：固定构建版本、公开仓库提交与远端回读。

真实订单与平台许可未被模拟器测试覆盖，必须单独记录。工程规则见 AGENTS.md；最终测试与审查事实见 verification.md。
