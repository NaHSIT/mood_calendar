# 窗口 G 交付：应用集成与闭环验收

## 当前状态

所有已形成提交的分支均已合入 `integration/app`：A 核心契约、B 量表、C 健康框架、E 教师平台契约、F AA 随访均在分支历史中；D 分支没有独立于 `develop` 的风险实现提交。全量单元测试和 APK 构建已通过，但测评提交→风险评估→投递→教师干预的真正 UI/事件接线仍需 D/E 的实现交付。

## 已完成

- 核对 `integration/app` 分支、G 独占目录和基线构建环境。
- 为 `minSdk 24` 启用 core-library desugaring，避免 `java.time` 在 API 24/25 上不可用。
- 移除 `AnalysisScreen` 对日记关键词疾病倾向的展示，保留心情分布/记录浏览；量表未接入时明确显示“待评估”。
- 编辑已有心情记录时保留原 `createdAt`，并添加纯函数单元测试。
- 图片选择后尝试持久化 URI 读权限，去重并补充编辑页无障碍描述。
- Auto Backup 与 device transfer 均排除数据库和 shared preferences，避免敏感摘要、会话信息或无法随 Android Keystore 恢复的密文被导出。
- G 使用独立 `.gradle-g-integration/` 缓存，避免其他并行 worktree 执行 `gradle --stop` 中止验证。

## 修改文件

- `.gitignore`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/mdd_calender/ui/MoodViewModel.kt`
- `app/src/main/java/com/example/mdd_calender/ui/screens/AnalysisScreen.kt`
- `app/src/main/java/com/example/mdd_calender/ui/screens/EditorScreen.kt`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/test/java/com/example/mdd_calender/ui/MoodViewModelIntegrationTest.kt`
- `docs/tasks/handoffs/G.md`

## 公开签名/行为变更

- 未新增跨模块公共契约。
- `MoodViewModel.saveMoodWithContent(...)` 签名不变，修正编辑时的创建时间语义。
- `AnalysisScreen(...)` 签名不变，旧关键词“诊断”路径不再从 UI 触发。

## 验证记录

- 首次 `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --stacktrace`：Kotlin 源码编译通过；测试 worker 启动失败，报 `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`，不计为测试通过。
- 无 daemon/单 worker 重试：被其他并行 worktree 的 Gradle stop 命令中止，不计为通过。
- 合入 A–F 后在独立 Gradle 用户目录执行 `gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest :app:assembleDebug`：通过，`BUILD SUCCESSFUL` (2分 21秒)。只有既有弃用警告和 Google Location D8 companion-object 警告。
- 生成 APK：`app/build/outputs/apk/debug/app-debug.apk`。
- `:app:lintDebug`：未完成。源码编译阶段通过，但 `generateDebugAndroidTestLintModel` 需下载隔离缓存中缺失的 `androidx.test.ext:junit:1.3.0`，沙箱网络请求被拒绝。
- 尚无设备/模拟器，未验证 UI 导航、旋转、重启、数据库迁移和权限隔离。

## 备份策略取舍

A 的在建方案将 care 表和旧心情/纪念日表放在同一 `mood_database`。Android 备份规则不能按 Room 表排除，因此当前整库不备份；代价是旧心情和纪念日也不能跨设备恢复。生产化时应分库，再为非敏感数据制定可恢复策略。

## 未完成项与集成顺序

1. A 将冻结契约和无损 v3→v4 迁移合入 `develop`，并交付密钥丢失的可解释错误语义。
2. B–F 基于冻结契约完成实现、测试和 handoff，通过提交/PR 进入 `develop`。
3. G 只从稳定 `develop` 检查点合入，装配服务/ViewModel/导航与恢复调度，再执行 07 的 7 个端到端场景。
4. 完整构建通过后才记录 APK 路径；当前不声明全项目完成。

## 外部依赖/生产边界

学生年龄适配、中文量表版权与专业审核、学校响应/退出策略、真实身份认证、硬件、推送和学校平台均未接入。本地角色演示不是生产鉴权，模拟送达不是真实通知。
