# Git 分支与窗口启动

远程仓库：https://github.com/NaHSIT/mood_calendar

## 分支与目录

主目录 `E:/AndroidStudio_prj/mood_calendar` 保留在 main。下表目录为独立 Git worktree，打开窗口时必须选择对应目录；不要在主目录反复 checkout 不同模块分支。`.worktrees` 已忽略，不会上传。

| 窗口 | 远程/本地分支 | 工作目录（相对于主目录） | 任务书 |
|---|---|---|---|
| A | feat/core-privacy | .worktrees/A-core-privacy | 01-公共契约与隐私存储.md |
| B | feat/assessment | .worktrees/B-assessment | 02-学生量表与测评中心.md |
| C | feat/health-framework | .worktrees/C-health-framework | 03-健康授权与接入框架.md |
| D | feat/risk-alerts | .worktrees/D-risk-alerts | 04-评估规则与预警事件.md |
| E | feat/teacher-platform | .worktrees/E-teacher-platform | 05-教师工作台与平台接口.md |
| F | feat/aa-followup | .worktrees/F-aa-followup | 06-AA随访与学生任务.md |
| G | integration/app | .worktrees/G-integration | 07-应用集成与闭环验收.md |

`develop` 是 PR 汇总目标，无独占工作窗口；`main` 只接收完成验收并确认后的 develop 成果。准备阶段所有分支从同一基线创建，不包含新功能实现。

## 复制到新窗口的启动指令

将下面的 X 和任务文件名替换为该窗口信息：

> 当前窗口负责模块 X。请先确认当前 Git 分支和工作目录符合 docs/tasks/09-Git分支与窗口启动.md，再阅读 docs/tasks/00-协同开发总则.md、docs/tasks/08-公共接口与业务约定.md 及对应模块任务书。仅修改任务书分配文件。先检查依赖：A 的公共契约尚未冻结并进入 develop 时，B–F 只做模块内设计/测试场景准备，不自行定义公共模型。依赖满足后执行任务，完成必要测试，将交付写入 docs/tasks/handoffs/X.md，提交并推送本模块分支，创建目标为 develop 的 PR。不要自行合并 PR、修改其他模块、强制推送或接入真实硬件/真实教师消息。跨模块改动记录在交付文档并交对应负责人处理。

## 同步节奏

1. A 优先交付公共契约和空实现，以首个 PR 合入 develop；G 负责核对签名与可编译性。A 随后继续存储工作。
2. B–F 在自己的干净工作目录获取并合并 develop；不得只依赖未合并的 A 分支。无冲突并通过必要检查后开始并行实现。
3. 每个模块以小步可验证提交推送对应分支。跨窗口变更通过 commit/PR 同步，不复制其他 worktree 文件。
4. 模块 PR 进入 develop 后，其他窗口再次同步。G 每次集成前合并最新 develop，统一接线；不要未经审核把所有分支直接混合。
5. 功能全部进入 develop 后由 G 做闭环验收，再交用户确认是否合入 main。

在干净模块目录执行：

```powershell
git status --short
git fetch origin
git merge origin/develop
# 完成必要验证后
git push
```

若有未提交修改，先自行提交该模块的完整小步成果，再同步；不得用 reset --hard、clean 或覆盖复制来消除冲突。冲突由相关文件所有者协调解决；不强推共享历史。

## PR 要求

目标分支 develop；描述问题与最终行为、依赖 PR、测试证据、接口变更和未完成项。A 首次契约 PR 合并前，其余模块仅允许不依赖公共实现的准备工作。角色/鉴权、数据库迁移与敏感数据修改须附对应验证证据。

这些是协作约定，不代表 GitHub 已配置强制分支保护或 CI。仓库管理者可后续启用保护规则；当前没有自动同步/自动合并后台服务。

## 本机文件

SDK 的 local.properties、IDE 和构建缓存不入库。此机器上的 worktree 可复用本机 SDK 配置，但每个目录保留独立构建输出。新机器按自身 SDK 路径重新生成 local.properties。私人参考图片“聊天记录.jpg”仅保留本机，不上传。
