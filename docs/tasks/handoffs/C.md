# C：健康授权与接入框架交付

## 完成项

- 未接入 provider：明确返回 `NotConnected`，不伪造空数据或真实接入状态。
- 演示 provider：只生成确定性的虚构心率/睡眠数据，所有样本强制标记 `DEMO`。
- 授权同步协调器：未授权不拉取；心率/睡眠分别控制；重复授权幂等；撤回取消任务；迟到回调按授权修订号丢弃；重新授权不能复活旧任务。
- 最终写入要求网关在同一原子操作中再次核对授权修订号，覆盖“回调复核后、保存前”撤回的竞态。
- 演示规则提取器：输出粗粒度模式、质量、时效、规则版本、授权修订号和模拟标记，不输出原始序列，不修改量表风险等级。
- 健康管理页：用途、可见范围、接入状态、分项授权、撤回、删除原始数据入口及不可收回历史摘要说明。
- 本人原始数据页：演示数据醒目标注并显示概览/列表；未接入显示真实空状态；越权明确显示拒绝。
- 单元测试覆盖未授权/部分授权、撤回竞态、迟到回调、重新授权、重启后关闭、重复授权、数据域隔离、低质量/过期/无数据和越权拒绝。

## 修改文件

- `app/src/main/java/com/example/mdd_calender/feature/health/HealthFeatureModels.kt`
- `app/src/main/java/com/example/mdd_calender/feature/health/HealthFeaturePorts.kt`
- `app/src/main/java/com/example/mdd_calender/feature/health/HealthProviders.kt`
- `app/src/main/java/com/example/mdd_calender/feature/health/RuleBasedPhysiologySignalExtractor.kt`
- `app/src/main/java/com/example/mdd_calender/feature/health/HealthSyncCoordinator.kt`
- `app/src/main/java/com/example/mdd_calender/feature/health/HealthScreens.kt`
- `app/src/test/java/com/example/mdd_calender/feature/health/HealthProvidersTest.kt`
- `app/src/test/java/com/example/mdd_calender/feature/health/RuleBasedPhysiologySignalExtractorTest.kt`
- `app/src/test/java/com/example/mdd_calender/feature/health/HealthSyncCoordinatorTest.kt`

## 页面/适配签名

- `HealthManagementScreen(state, onBack, onConsentChange, onDeleteRawData, onOpenRawData)`
- `RawHealthDataScreen(view, onBack)`
- `DisconnectedHealthProvider : HealthProviderClient`
- `DemoHealthProvider(scenario, nowEpochMillis) : HealthProviderClient`
- `RuleBasedPhysiologySignalExtractor(rules) : PhysiologySignalExtractor`
- `HealthSyncCoordinator.sync / setConsent / deleteRawData / rawDataFor`
- 集成边界：`HealthConsentGateway`、`StudentHealthGateway`、`HealthConsentChangeSink`

## 模拟场景表

| 场景 | 心率 | 睡眠 | 预期 |
|---|---|---|---|
| `BALANCED` | 正常范围虚构样本 | 充足睡眠虚构样本 | `NO_PATTERN` |
| `ELEVATED_HEART_RATE` | 演示阈值以上 | 充足睡眠 | 粗粒度心率辅助信号 |
| `SHORT_SLEEP` | 正常范围 | 演示阈值以下 | 粗粒度睡眠辅助信号 |
| `LOW_QUALITY` | 质量 0.25 | 质量 0.25 | `LOW_QUALITY`，不产信号 |
| `NO_DATA` | 无样本 | 无样本 | `NO_DATA` |
| `STALE_DATA` | 十天前样本 | 十天前样本 | `STALE_DATA` |

阈值只存在于 `DemoSignalRules`，用于原型演示，不声称临床有效。

## 验证命令和结果

命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin
```

- `:app:compileDebugKotlin`：通过。
- `:app:compileDebugUnitTestKotlin`：通过。
- `:app:testDebugUnitTest`：未能执行测试方法。本机 Gradle 9.3.1 在启动测试进程时报告 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`；单 worker、无 daemon 复验仍相同。不得记为测试通过。
- 编译时另有既存 Android SDK XML 版本提示和旧代码弃用警告，本模块未新增对应警告。

## 未完成项

- A 的冻结公共契约已合入当前分支；`DomainHealthDataProviderAdapter` 和 `RuleBasedPhysiologySignalExtractor` 已对接 `domain.port.HealthDataProvider` / `PhysiologySignalExtractor`。Room 授权/原始样本事务适配仍由 G/A 注入，C 专属网关保留为可测试的同步编排边界。
- 未做 Room/加密持久化、应用导航接线、启动恢复或真实设备接入；这些分别属于 A/G 或明确排除范围。
- 测试已编译但受本机 Gradle test worker `GradleWorkerMain` 类加载故障影响未实际运行。

## 集成步骤

1. 由 G/A 为 `HealthConsentGateway`、`StudentHealthGateway` 写薄适配器；`persistIfConsentCurrent` 必须在事务中核对授权修订号后再保存原始样本和信号。
2. `HealthConsentChangeSink` 接到 D/G：撤回后清除待发健康辅助内容并重新计算摘要，但不得删除量表本身触发的预警。
3. G 将两个页面接入现有导航并提供可信学生身份；教师导航不得暴露 `RawHealthDataScreen`。
4. 使用 A 的加密演示存储验证进程重启后授权仍关闭，再运行全量测试。

## 依赖请求

- 请求 A 冻结并发布 `HealthConsent`、`RawHealthSample`、`PhysiologySignal`、`ConsentRepository`、`StudentHealthRepository`、`HealthDataProvider` 完整签名及授权变更事件签名。
- 原子保存能力需接受 `studentId + metric + expectedConsentRevision`，修订不匹配必须不写入。
- 不需要新增 Gradle 依赖、系统权限、健康 SDK 或后台采集权限。
