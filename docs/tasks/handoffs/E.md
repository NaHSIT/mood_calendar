# 窗口 E 交付记录

## 当前结论

A 的公共契约已合入 `develop`（基线 `e9deed5`），窗口 E 已完成教师工作台核心实现，可进入集成。E 不修改 A 的公共模型、端口或数据库。

## 已完成

- 核对工作目录和分支：`feat/teacher-platform`，工作树干净。
- 完成教师工作台的页面边界、双重鉴权、状态流、撤权竞态和验收场景设计。
- 完成学校平台 v1 草案、最小化模拟 payload、幂等/回执/错误语义及未来认证边界。
- 明确模拟送达与教师确认分离，未接入网关不能报告真实发送成功。
- 实现教师消息箱、预警详情、确认、干预启动及模拟平台投递/回执。
- 干预启动调用 A 的 `InterventionRepository.startWithAa`，由公共事务能力保证干预与 AA 原子入库；重复幂等键返回已有结果。
- 消息箱状态由告警白名单摘要与责任教师干预状态派生，避免自行修改告警领域存储。
- 模拟网关支持成功、待重试、永久失败、幂等回执查询四种演示结果，始终标记 `simulated=true`。
- 修复教师端缺少 AA 退出审核入口：只列出责任范围内的待审核档案，展示申请理由，审核备注必填，并支持批准/驳回及最小审计。

## 修改文件

- `feature/teacher/TeacherWorkbenchDesign.md`
- `integration/school/SchoolPlatformProtocol.md`
- `docs/tasks/handoffs/E.md`
- `feature/teacher/TeacherWorkbenchService.kt`
- `feature/teacher/TeacherWorkbenchViewModel.kt`
- `feature/teacher/TeacherWorkbenchScreen.kt`
- `integration/school/SimulatedSchoolPlatformGateway.kt`
- `src/test/.../SimulatedSchoolPlatformGatewayTest.kt`

## 公开接口/页面签名

页面入口：

- `TeacherWorkbenchRoute(viewModel)`：消息箱与详情切换。
- `TeacherInboxScreen(...)`：待确认、处理中、已结束筛选、空态、刷新和责任范围结果。
- `TeacherAlertDetailScreen(...)`：白名单详情、确认、启动干预、模拟平台投递和返回。

业务服务：

- `TeacherWorkbenchService.loadInbox/loadDetails`：只使用 A 的教师白名单查询。
- `acknowledge(caseId)`：责任教师鉴权、幂等确认和审计。
- `startIntervention(caseId, policyVersion)`：必要时先确认，再调用 `startWithAa`。
- `simulateDelivery(eventId)`：白名单 DTO + 幂等键投递，不发送真实平台消息。
- `loadPendingExitReviews/reviewExit`：调用 A 的 `FollowUpRepository`，不由 UI 构造教师权限或直接修改 AA 状态。

## 验证

- `git fetch origin --prune`：成功。
- `git merge --no-edit origin/develop`：成功合入 A 契约，产生本地合并提交。
- `gradlew.bat :app:compileDebugKotlin`：通过。
- `gradlew.bat :app:assembleDebug --no-daemon --max-workers=1`：通过。
- `gradlew.bat :app:compileDebugUnitTestKotlin :app:assembleDebug --no-daemon --max-workers=1`：通过，新增退出审核测试源码编译成功，APK 重新生成。
- 定向运行 `TeacherExitReviewTest`：测试执行器仍因本机 `GradleWorkerMain` 缺失而无法启动，未声明测试通过。
- `gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1`：测试源码编译通过，但执行器启动失败：`ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`；未声明测试通过。

## 未完成项

- G 尚需把 `TeacherWorkbenchRoute` 接入导航和可信教师会话/仓储装配。
- 当前模拟投递记录保存在网关实例内存；正式持久投递队列仍需 A/G 提供应用启动恢复与 `saveDeliveryInternal` 系统通道装配。
- 测试执行器环境需修复后重新运行单元测试。
- A/F 仍需在业务层保证批准退出前重新校验稳定观察和未处理安全关注，并在批准后取消未来未执行任务；E 页面已明确提示该边界，不能替代后端约束。

## 依赖请求与后续步骤

1. G 注入 `SessionProvider`、`AlertRepository`、`InterventionRepository`、`AuditRepository` 和模拟/真实网关实现。
2. G 将 `TeacherWorkbenchRoute` 注册到教师入口，并提供 ViewModel 工厂；页面不接受 UI 自行构造的角色。
3. A/G 补齐持久投递队列：发送前按当前摘要修订重建 payload，授权撤回时丢弃失效辅助内容。
4. 修复 Gradle worker 后运行 E 单元测试和全量闭环验收。

## 生产边界

当前协议、时限和处置步骤均为本地演示设计，不代表学校已提供接口、全天候接警、专业审核或真实身份认证。不得使用真实学生数据或真实教师接收人测试。
