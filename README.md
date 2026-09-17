# 心情日历 Mood Calendar

Android 原生心情记录原型，使用 Kotlin、Jetpack Compose、Room。当前包括日历、图文记录、心情统计、天气、纪念日和个性化设置。

校园量表、健康数据授权、教师预警和 AA 随访为待开发功能，不能将任务书视为已实现能力。当前关键词心理分析也不是临床诊断功能。

## 开发入口

- [协同开发总则](docs/tasks/00-协同开发总则.md)
- [模块分支与窗口启动](docs/tasks/09-Git分支与窗口启动.md)
- [公共接口约定](docs/tasks/08-公共接口与业务约定.md)
- [原始产品需求](Mood_Calendar_PRD.md)

用 Android Studio 打开工作目录，配置本机 Android SDK；`local.properties` 不提交。Gradle 版本及工具链以项目配置为准。本次仓库准备不代表已经通过构建或真机验证。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## 分支原则

`main` 保存经确认的基线，`develop` 汇总模块，模块通过 PR 合入 develop。先合并公共契约，再并行实现功能，最后集成验收。各窗口使用独立 worktree，禁止提交真实学生数据、密钥或本机配置。
