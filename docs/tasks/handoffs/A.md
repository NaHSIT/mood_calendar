# 窗口 A 交付：公共契约、身份权限与隐私存储

## 完成项

- 冻结 `domain/model`、`domain/port`、`domain/policy` 公共模型、失败结果、可注入 UTC 时钟及内部评估/事务通道。
- 提供固定注入的演示会话、未配置即拒绝的生产身份认证占位；没有 UI 角色切换后门。
- 新增量表、授权、原始健康数据、派生信号、评估、告警、投递、干预、AA、随访任务和审计的 Room 存储。
- 学生仓储按可信会话限制本人及数据域；教师仓储只返回责任范围内白名单摘要/工作信息；系统通道独立校验 SYSTEM 角色。
- 敏感答案、原始健康值、处置备注、退出审核和回执使用 Android Keystore AES-GCM，随机 nonce、AAD 和 `v1` 版本化密文；密钥缺失/篡改返回可解释失败，不清空数据库。
- Room 从已核实的 v3 增量迁移到 v4；保留 `mood_records`、`anniversary_records`。未知 v1/v2 不做破坏性回退。
- 干预启动与 AA 入库在同一 Room 事务中执行；活动 AA 通过 `(studentId,dataDomain,activeSlot)` 唯一索引及幂等键避免重复。

## 公开接口与主要文件

- 模型：`app/src/main/java/com/example/mdd_calender/domain/model/`
- 接口：`app/src/main/java/com/example/mdd_calender/domain/port/`
- 权限：`domain/policy/AccessPolicy.kt`
- 会话/加密：`security/SessionProviders.kt`、`security/AuthenticatedCipher.kt`
- Room 实体/DAO：`data/care/CareEntities.kt`、`CareDao.kt`
- 实现：`data/care/PrivateRepositories.kt`、`WorkRepositories.kt`
- 数据库与迁移：`data/MoodDatabase.kt`（版本 4、`MIGRATION_3_4`）

## 验证

- `gradlew.bat testDebugUnitTest --stacktrace`：主源码及 `compileDebugKotlin`、`compileDebugUnitTestKotlin` 已完成；测试执行器随后因本机 Gradle worker `worker.org.gradle.process.internal.worker.GradleWorkerMain` 类加载/管道异常失败，不是断言失败。
- 修正仪器测试的 `RoomDatabase` 关闭方式后，尚未能重新运行完整 Android 测试编译：受限环境无法再次通过 Gradle wrapper 网络审批；直接调用本机 Gradle 发行版又缺少 `foojay-resolver-convention` 插件缓存。
- 测试源码：`src/test/.../AccessPolicyTest.kt`、`src/androidTest/.../CareSecurityInstrumentedTest.kt`，覆盖越权、教师私有字段边界、GCM 篡改/AAD、v3 数据保留和 AA 重试幂等。

## 未完成项与边界

- 未接入真实身份、健康平台、学校平台或真实通知；`IdentityAuthenticator`/`HealthDataProvider`/`SchoolPlatformGateway` 仅为适配接口。
- v1/v2 schema 在当前 Git 历史中不存在，无法安全编写迁移；遇到这些版本应报告迁移缺失，不能 destructive fallback。
- Android Keystore 加密原型不等于服务端身份安全、备份密钥恢复方案或校园合规认证。

## 集成步骤

1. 将本分支合入 `develop`，其他窗口仅依赖冻结的 `domain` 接口，不复制公共类型。
2. G 在应用装配处注入 `SessionProvider`、`AndroidKeystoreCipher` 和各 Room 仓储；不要把演示会话用于生产。
3. 运行 `testDebugUnitTest` 与连接设备后的 `connectedDebugAndroidTest`；确认 v3 fixture 迁移和 Keystore 仪器测试。
