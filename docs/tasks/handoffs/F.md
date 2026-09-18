# 窗口 F：AA 随访与学生任务交付

## 当前状态

公共契约尚未冻结。F 的实现暂以 `feature/followup` 内的端口隔离 A/B/E，待 A
冻结后由 G/F 做适配，不将本地模型直接当作最终公共契约。

## 已完成

- 核对窗口、分支和目录归属：`feat/aa-followup` / `.worktrees/F-aa-followup`。
- 核对 00、06、08 的业务边界和验收项。
- 完成状态机、幂等、权限与恢复场景实现。
- 完成学生任务/教师白名单视图、演示策略、可注入时钟与恢复调度。
- 测评回调必须由 `AssessmentEvidenceVerifier` 验证“已成功持久化”，不接受伪造 assessmentId。
- 退出申请只允许学生本人发起；稳定观察起点由仓储可信数据提供，责任教师独立审核。

## 设计与状态机

### AA 档案

- `干预中事件 -> 跟踪中`：以干预事件稳定 ID 幂等创建；同一学生已有活动档案时复用并关联新干预/预警。
- `跟踪中 -> 待退出审核`：只允许学生本人申请，保留申请依据和时间。
- `待退出审核 -> 跟踪中`：责任教师拒绝，记录审核人、依据和时间。
- `待退出审核 -> 已退出`：责任教师批准前再次校验稳定观察条件和未处理安全关注；批准后取消未来未执行任务，保留历史。
- 新的正式干预可以让已退出学生重新入库，但不得复活旧档案或已取消任务。

### 随访任务

- 类型：简短心情打卡、完整量表复测、教师联系。
- 状态：待处理、到期、逾期、暂停、完成、取消。到期是时间视图，不应依赖一次性后台写入才能正确显示。
- 任务生成键至少包含 `enrollmentId + policyVersion + taskKind + sequence/dueAt`，应用重启和事件重放不得重复生成。
- 量表复测只能由受信的“测评已成功保存”回调完成，并核对学生、量表类型及任务引用；学生提交任意字符串形式的 `assessmentId` 不可完成任务。
- 未打卡只产生教师待联系工作项，不自动提高心理风险等级。

## 验收场景矩阵

1. 相同干预事件重复消费：只有一个活动档案和一组任务。
2. 事务在事件记录后/任务生成中失败：重试补齐缺失任务，不生成重复项。
3. 同一学生的新预警/干预：复用活动档案并建立关联。
4. 虚拟时钟越过到期点：待处理变为到期/逾期视图，并产生待联系；风险级别不变。
5. 同一任务重复完成：返回同一完成结果或明确冲突，不改写首次完成时间。
6. 复测任务使用伪造、他人、未成功保存或量表类型不符的 assessmentId：拒绝。
7. 稳定期不足或存在未处理安全关注：退出审核拒绝。
8. 批准退出：未来待处理任务取消，已完成/历史逾期任务保留。
9. 已退出后新干预：新建档案和新任务，不复活旧任务。
10. 学生只能读取/操作本人任务；教师只能读取/操作责任范围。
11. 责任教师转交：旧责任人立即失去操作权，新责任人获得操作权。
12. 应用重启恢复：任务集合与重启前一致。

## 对 A 的最小契约请求

现有草案不足以在不绕过仓储授权的前提下完成 F 验收，建议由 A 冻结时补齐：

- 受信内部端口：消费正式干预事件并在事务中创建/复用档案、关联干预、幂等生成任务。
- 受信测评完成端口：输入 `taskId + assessmentId`，由存储层核验测评存在、已完成、属于任务学生且量表类型匹配。
- 学生打卡与教师联系分别使用明确完成命令，避免通用 `complete(taskId, time)` 绕过类型规则。
- 教师详情查询返回白名单工作视图：责任教师、任务统计、待联系、AA 状态，不包含逐题答案、原始健康数据和日记。
- 退出审核命令能够原子校验稳定期及未处理安全关注，并在批准后取消未来任务。
- 责任人转交命令、任务暂停/恢复语义，以及按稳定键创建任务的幂等保证。
- 模型补齐活动档案与干预/预警的多重关联；任务状态补齐暂停语义，或明确暂停为独立字段。

## 修改文件

- `app/src/main/java/com/example/mdd_calender/feature/followup/FollowUpModels.kt`
- `app/src/main/java/com/example/mdd_calender/feature/followup/FollowUpPorts.kt`
- `app/src/main/java/com/example/mdd_calender/feature/followup/FollowUpSchedule.kt`
- `app/src/main/java/com/example/mdd_calender/feature/followup/FollowUpService.kt`
- `app/src/main/java/com/example/mdd_calender/feature/followup/FollowUpRepositoryUiAdapter.kt`
- `app/src/main/java/com/example/mdd_calender/feature/followup/FollowUpComponents.kt`
- `app/src/main/java/com/example/mdd_calender/feature/followup/FollowUpPolicy.kt`、`FollowUpScreens.kt`、`FollowUpUiModels.kt`（同窗口已有实现，保留）
- `app/src/test/java/com/example/mdd_calender/feature/followup/FollowUpServiceTest.kt`
- `app/src/test/java/com/example/mdd_calender/feature/followup/FollowUpPolicyTest.kt`

## 公开接口/页面签名

`FollowUpService.onInterventionStarted(signal)`：幂等消费干预正式启动事件并创建/复用 AA。
`recoverTaskSchedules()`：启动恢复，按稳定任务键补齐缺失任务。
`studentView(actor)` / `teacherList(actor)`：学生本人和责任教师白名单查询。
`completeSimpleTask(...)` / `completeAssessmentTask(receipt)`：按任务类型完成，复测要求可信保存回执。
`requestExit(student, enrollmentId, rationale)` / `reviewExit(teacher, enrollmentId, approve)`：申请与人工审核分离。
`transferResponsibility(...)`、`setPaused(...)`：责任归属与暂停/恢复。
`StudentFollowUpPanel`、`TeacherFollowUpCard` 以及已有 `StudentFollowUpScreen`、`TeacherFollowUpScreen` 为 UI 组件签名。
`FollowUpRepositoryUiAdapter` 将 A 的 `FollowUpRepository`、B 的 `AssessmentRepository` 映射到 F UI，复测只接受已完成且属于当前学生的测评。

## 验证命令和结果

- `git status --short --branch`：分支为 `feat/aa-followup`，开始工作前干净。
- `compileDebugKotlin`、`compileDebugUnitTestKotlin` 已通过。
- 已合入本地最新 `develop`（`e9deed5`，A 公共契约与数据库冻结）。
- 标准 `testDebugUnitTest` 的 Gradle worker 在本机报 `GradleWorkerMain` 类加载错误；使用同一 runtime classpath 的直接 JUnitCore 执行通过：`OK (14 tests)`。

## 未完成项

- 与 B 测评成功回调、E 干预启动事件、G 提醒调度和应用导航的接线。

## 集成步骤

1. A 提交公共契约并发布“契约已冻结”。
2. F 同步包含冻结契约的 `develop` 检查点。
3. F 将 `FollowUpStore` 映射到 A 的事务仓储，将 `InterventionStartedSignal` 映射到 E/A 干预事件，将 `AssessmentEvidenceVerifier` 映射到 B 的成功保存回调。
4. G 装配 E/B 事件通道、提醒调度与导航入口。

## 风险与边界

- AA 是业务跟踪层级，不是诊断；不得从量表分数直接自动入库。
- 演示周期和稳定条件尚未经过学校或专业人员审核，UI 必须明确标注。
- 本地角色切换不等于正式身份鉴别，不能作为生产授权依据。
