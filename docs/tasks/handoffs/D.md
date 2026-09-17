# D 窗口交付：评估规则与预警事件

## 完成项

- 新增 `DemonstrationRiskEvaluator`：按 14 天有效期筛选每份量表最新完成结果，分别计分后取较高关注等级，不相加。
- PHQ-9 第 9 题非零仅产生 `SAFETY_REVIEW_REQUIRED` 和及时人工复核优先级，不直接判定紧急危险。
- 生理信号仅在质量合格、未过期、时间窗口已结束且来自当前学生时作为辅助标签；不改变量表等级。
- 新增 `AlertEventFactory`：中度/高度或独立安全关注生成教师最小化事件；事件与评估 ID 使用稳定哈希，重复输入幂等。
- 无有效量表返回 `INSUFFICIENT_DATA`，不生成新预警；量表缺失/过期原因保留在评估标签中。

## 修改文件

- `app/src/main/java/com/example/mdd_calender/feature/risk/RiskEvaluatorImpl.kt`
- `app/src/test/java/com/example/mdd_calender/feature/risk/RiskEvaluatorTest.kt`

## 公开接口/页面签名

实现 A 已冻结的 `com.example.mdd_calender.domain.port.RiskEvaluator`；新增 `AlertEventFactory.fromEvaluation(EvaluationResult, studentCode, occurredAtEpochMillis)`。无页面、导航或数据库改动。

## 验证命令和结果

- `./gradlew :app:compileDebugKotlin`：通过（仅已有项目弃用警告）。
- `./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest --tests com.example.mdd_calender.feature.risk.RiskEvaluatorTest`：测试执行器失败，环境报 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`；测试源码已编译。

## 未完成项

测试执行器环境需修复后重跑；事件持久化由 A 的 `AlertRepository`/G 集成，不在 D 内实现。

## 集成步骤

合并本分支后由 G 装配 `DemonstrationRiskEvaluator`，并将其结果交给 `AlertEventFactory` 与 `AlertRepository.saveInternal`；教师 DTO 仅使用白名单摘要。

## 依赖请求

无新增依赖。请在 CI 或本机修复 Gradle worker classpath 后重跑 D 单测。
