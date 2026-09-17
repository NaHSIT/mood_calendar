# 窗口 B 交付：学生量表与测评中心

## 当前状态

部分完成，暂不可集成。2026-09-17 已执行 `git fetch origin`；`origin/develop` 与 `origin/feat/core-privacy` 仍停在基线提交 `5aebfd0`，A 的公共契约尚未冻结。依据协作总则，B 不自行创建 AssessmentRecord、AssessmentRepository 或其替代接口。

## 已完成

- 纯 Kotlin PHQ-9 / GAD-7 计分、完整性校验和症状分段。
- PHQ-9 第 9 题非零只输出人工安全复核标志，不自动诊断或判定紧急危险。
- 不可变草稿模型：作答、清除、进度、恢复与损坏草稿拒绝。
- 量表版本、来源、两周回顾窗口和审核状态的模块内描述。
- 全部分段临界值、漏题、非法值、最大分、草稿恢复及安全标志的单元测试。

## 修改文件

- `feature/assessment/AssessmentScoring.kt`
- `feature/assessment/AssessmentDraft.kt`
- `feature/assessment/QuestionnaireCatalog.kt`
- 对应 `AssessmentScoringTest.kt`、`AssessmentDraftTest.kt`

## 模块内签名

- `AssessmentScorer.score(type, answers): ScoreResult`
- `AssessmentScorer.bandFor(type, total): SymptomBand`
- `AssessmentDraft.empty(type)` / `restore(type, answers)` / `answer(index, value)` / `clear(index)`
- `ScoreResult.Complete.toAssessmentRecord(...)` maps a validated completed score to A's frozen `AssessmentRecord`.

`AssessmentType` 和 `SymptomBand` 现在直接别名到 A 冻结的 `domain.model` 类型；B 不再复制一套公共枚举。其余类型仍为 B 内部实现，其他模块不应依赖。

## 验证

- `GRADLE_USER_HOME=<ASCII 可写目录> .\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest`：通过（2026-09-17，26 个任务，2 个实际执行）。
- 默认用户缓存路径下的同一命令曾因 Gradle 测试 worker 找不到 `worker.org.gradle.process.internal.worker.GradleWorkerMain` 失败；源码编译通过。该环境问题通过纯 ASCII 缓存目录复测排除，未修改项目配置。
- 构建日志仍有项目原有的 Android SDK XML 版本警告；不影响本次单元测试结果。

## 未完成及原因

- 保存完整记录、本人历史隔离、重复提交幂等、提交失败恢复：等待 A 的 `AssessmentRepository`、失败类型、会话和持久化签名。
- 测评入口、逐题页、结果页、历史趋势及专属 ViewModel：应以冻结仓储签名实现，避免临时接口造成返工。
- `followUpTaskId` 与成功保存后的 `assessmentId` 回调：等待 A/F 公共模型，最终由 G 接线。
- 正式中文题文：当前仅有明确占位符。中文版本来源、授权、年龄适用性和专业审核未确认，不能标成正式标准版。
- 导航注册、Gradle、数据库和旧 `MoodViewModel`：均不属于 B 的修改范围。

## 依赖请求与后续集成

1. A 将冻结契约合入 `develop`，公布 `AssessmentRecord`、草稿/完成态表达、`AssessmentRepository`、授权失败类型及可信学生身份来源。
2. B 合并最新 `origin/develop` 后实现仓储适配、专属 ViewModel、页面及仓储替身测试。
3. G 仅在 B 完成第二阶段后注册页面，并将保存成功事件接入 D；不要把草稿或失败提交传入风险评估。
