# 窗口 G 交付：应用集成与闭环验收

## 当前状态

所有已形成提交的 B–F 分支头均已合入 `integration/app`。`AppCareServices` 作为单一应用组装根创建 Room 仓储、Keystore 加密、C 演示健康提供者、D 风险评估器、E 教师工作台/模拟学校网关和 F 随访适配器。页面导航和演示闭环已经接通。

## 已完成

- 核对 `integration/app` 分支、G 独占目录和基线构建环境。
- 为 `minSdk 24` 启用 core-library desugaring，避免 `java.time` 在 API 24/25 上不可用。
- 移除 `AnalysisScreen` 对日记关键词疾病倾向的展示，保留心情分布/记录浏览；量表未接入时明确显示“待评估”。
- 编辑已有心情记录时保留原 `createdAt`，并添加纯函数单元测试。
- 图片选择后尝试持久化 URI 读权限，去重并补充编辑页无障碍描述。
- Auto Backup 与 device transfer 均排除数据库和 shared preferences，避免敏感摘要、会话信息或无法随 Android Keystore 恢复的密文被导出。
- G 使用独立 `.gradle-g-integration/` 缓存，避免其他并行 worktree 执行 `gradle --stop` 中止验证。
- 重新合入 B–F 最新分支头，包含 E 的教师工作台与模拟投递实现。
- 新增底部“照护”入口，接入量表、健康授权、学生 AA 随访和教师预警工作台导航。
- 量表提交已接通：持久化→D 风险评估→评估/预警入库→建立教师责任关系和干预案例→E 模拟投递回执入库。
- 教师工作台可确认预警并启动干预，`RoomInterventionRepository.startWithAa` 保证干预和 AA 入库的事务一致性；学生随访页通过 F 的适配器读取和操作任务/退出申请。
- 健康授权不再是页面临时状态：授权修订写入 Room，按修订号拉取并加密保存演示样本，学生原始数据页可读取展示；撤权会停止后续同步。
- AA 入组后首次打开随访页会幂等生成打卡、量表复测和教师联系任务；量表复测跳转量表页，只有量表保存成功才完成任务，教师联系任务不能由学生自行完成。
- PHQ-9/GAD-7 页面已提供完整可作答题目和四档频率选项；仍明确标注为筛查而非诊断，PHQ-9 安全题非零时立即展示求助提示。
- 风险评估按评估时刻向前读取完整 14 天量表窗口，并将当前授权修订下的合格生理辅助信号写入评估输入，不再固定传空列表。
- 原始健康数据删除已接通学生权限限定的 Room 删除接口。
- AA 退出申请会校验 7 天稳定观察、至少一次量表复测和未解决安全关注；教师工作台提供责任范围内的批准/驳回入口，批准操作会事务内取消未来任务。

## 修改文件

- `.gitignore`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/mdd_calender/ui/MoodViewModel.kt`
- `app/src/main/java/com/example/mdd_calender/ui/screens/AnalysisScreen.kt`
- `app/src/main/java/com/example/mdd_calender/ui/screens/EditorScreen.kt`
- `app/src/main/java/com/example/mdd_calender/integration/app/AppCareServices.kt`
- `app/src/main/java/com/example/mdd_calender/integration/app/CareScreens.kt`
- `app/src/main/java/com/example/mdd_calender/feature/followup/FollowUpRepositoryUiAdapter.kt`
- `app/src/main/java/com/example/mdd_calender/feature/assessment/QuestionnaireCatalog.kt`
- `app/src/main/java/com/example/mdd_calender/feature/health/HealthScreens.kt`
- `app/src/main/java/com/example/mdd_calender/data/care/CareDao.kt`
- `app/src/main/java/com/example/mdd_calender/data/care/PrivateRepositories.kt`
- `app/src/main/java/com/example/mdd_calender/data/care/WorkRepositories.kt`
- `app/src/main/java/com/example/mdd_calender/domain/port/HealthPorts.kt`
- `app/src/main/java/com/example/mdd_calender/ui/navigation/AppNavigation.kt`
- `app/src/main/java/com/example/mdd_calender/ui/screens/MainScreen.kt`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/test/java/com/example/mdd_calender/ui/MoodViewModelIntegrationTest.kt`
- `docs/tasks/handoffs/G.md`

## 公开签名/行为变更

- `StudentHealthRepository` 新增当前学生按样本类型删除原始健康数据的受限接口。
- `MoodViewModel.saveMoodWithContent(...)` 签名不变，修正编辑时的创建时间语义。
- `AnalysisScreen(...)` 签名不变，旧关键词“诊断”路径不再从 UI 触发。

## 验证记录

- 首次 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace`：Kotlin 源码编译通过；测试 worker 启动失败，报 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`，不计为测试通过。
- 无 daemon/单 worker 重试：被其他并行 worktree 的 Gradle stop 命令中止，不计为通过。
- 合入最新 A–F 提交和 `AppCareServices` 后在独立 Gradle 用户目录执行 `gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest :app:assembleDebug`：通过，`BUILD SUCCESSFUL` (1分)。
- 重新合入 B–F、完成页面和事件链后执行同一命令：通过，`BUILD SUCCESSFUL` (1分 11秒)。
- 补齐健康持久化、AA 任务生成和量表复测回跳后再次执行 `:app:testDebugUnitTest :app:assembleDebug`：通过，`BUILD SUCCESSFUL` (51秒)。
- 生成 APK：`app/build/outputs/apk/debug/app-debug.apk`。
- 修复 Compose 非可观察 Locale 读取后，`:app:lintDebug`：通过，`BUILD SUCCESSFUL` (2分05秒)；报告为 0 error、59 warning，剩余项主要是依赖可升级、旧资源/图标和 KTX 风格提示。
- 尚无设备/模拟器，未验证 UI 导航、旋转、重启、数据库迁移和权限隔离。

## 备份策略取舍

A 的在建方案将 care 表和旧心情/纪念日表放在同一 `mood_database`。Android 备份规则不能按 Room 表排除，因此当前整库不备份；代价是旧心情和纪念日也不能跨设备恢复。生产化时应分库，再为非敏感数据制定可恢复策略。

## 尚存边界

- AA 初始任务在学生首次进入随访页时幂等物化，不是后台调度；生产版应由可靠任务调度器在入组事务后触发。
- 尚无设备/模拟器，仍需手工走查导航、进程重启恢复、数据库迁移和角色权限隔离。

## 外部依赖/生产边界

学生年龄适配、中文量表版权与专业审核、学校响应/退出策略、真实身份认证、硬件、推送和学校平台均未接入。本地角色演示不是生产鉴权，模拟送达不是真实通知。
