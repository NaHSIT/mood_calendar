# 窗口 E 交付记录

## 当前结论

窗口 E 的 Kotlin 实现尚未开始，当前不可声明“可集成”。2026-09-17 执行 `git fetch origin --prune` 后，`origin/develop` 与 `origin/feat/core-privacy` 均仍为初始提交 `5aebfd0`；A 的公共契约没有冻结并合入 `develop`。按照协同开发总则，E 不能自行定义公共模型或仓储接口。

## 已完成

- 核对工作目录和分支：`feat/teacher-platform`，工作树干净。
- 完成教师工作台的页面边界、双重鉴权、状态流、撤权竞态和验收场景设计。
- 完成学校平台 v1 草案、最小化模拟 payload、幂等/回执/错误语义及未来认证边界。
- 明确模拟送达与教师确认分离，未接入网关不能报告真实发送成功。

## 修改文件

- `feature/teacher/TeacherWorkbenchDesign.md`
- `integration/school/SchoolPlatformProtocol.md`
- `docs/tasks/handoffs/E.md`

## 公开接口/页面签名

暂无冻结 Kotlin 签名。设计建议的三个 Route 入口仅用于与 G 协调，最终参数类型以 A 合入后的契约为准，不构成公共 API。

## 验证

- `git fetch origin --prune`：成功。
- `git rev-list --left-right --count HEAD...origin/develop`：`0 0`。
- `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`：未通过。源码 Kotlin 编译完成，仅有基线弃用警告；随后 `:app:mergeExtDexDebug` 因共享 Gradle transforms 缓存被另一 Java 进程（PID 15012）占用而超时。
- `gradlew.bat :app:testDebugUnitTest --no-parallel`：未通过。测试源码编译完成，启动测试执行器时出现 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`。这是本机 Gradle 测试执行环境错误，不能据此声明测试通过，也没有出现窗口 E 代码编译失败（当前仅新增文档）。
- 缓存占用进程退出后再次运行组合任务：未通过；本项目任务执行期间收到外部 Gradle `stop` 命令，守护进程被停止。共享环境存在并行 Gradle 操作，未清理全局缓存或停止其他工作树进程。

## 未完成项

- 教师消息箱、详情和干预 Compose 页面及专属 ViewModel。
- 未接入/模拟 `SchoolPlatformGateway` 实现。
- 持久投递队列、重试、回执恢复、升级待办和完整单元测试。
- 与 A 的鉴权仓储、干预/AA 事务能力接线，以及与 G 的导航和依赖装配。

## 依赖请求与后续步骤

1. A 将 `ActorContext`、`SessionProvider`、教师白名单摘要查询、告警投递记录、干预命令、审计结果和明确失败类型的完整签名合入 `develop`，并发布契约冻结通知。
2. A 明确发送前按当前授权修订重新生成摘要的读取接口，以及启动干预与 AA 可靠入库的事务服务签名。
3. 契约到位后，本分支合并 `origin/develop`，按本设计实现，不复制或重定义 A 的领域类型。
4. G 最终负责导航、依赖装配、Android 调度能力及端到端闭环验证。

## 生产边界

当前协议、时限和处置步骤均为本地演示设计，不代表学校已提供接口、全天候接警、专业审核或真实身份认证。不得使用真实学生数据或真实教师接收人测试。
