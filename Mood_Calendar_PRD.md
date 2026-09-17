# 🌙 心情日历 Mood Calendar（PRD v2.0）

## 文档信息

| 项目 | 内容 |
| :--- | :--- |
| 产品名称 | 心情日历 Mood Calendar |
| 版本 | v2.0 |
| 开发平台 | Android Studio（Kotlin + Jetpack Compose） |
| 架构模式 | MVVM + Repository + Room + DataStore |
| 数据策略 | Offline-First |
| UI体系 | Material 3 + Emotional Minimal Design |
| 当前状态 | 可进入开发阶段 |

---

# 1. 产品概述

## 1.1 产品定位

心情日历是一款以“低认知负担”为核心理念的情绪记录工具。

用户无需输入长文本，仅通过选择情绪表情，即可完成每日情绪记录，并通过日历与统计图谱形成长期情绪轨迹。

---

## 1.2 设计目标

- 单次记录操作 ≤ 3 秒
- 情绪表达无需文字门槛
- 通过时间维度实现情绪可视化
- 提供轻量但长期有效的自我认知工具

---

## 1.3 核心原则

- 极简输入（One Tap Logging）
- 情绪优先于文本
- 数据本地优先
- UI 不干扰内容表达

---

# 2. 系统架构（Android Studio 全栈）

## 2.1 总体架构

UI Layer (Jetpack Compose)
↓
ViewModel (StateFlow)
↓
UseCase (Business Logic)
↓
Repository (Single Source of Truth)
↓
Room Database (Local Storage)
↓
DataStore (Settings)
↓
Optional Cloud Sync (Firebase / API)

---

## 2.2 技术栈

- UI：Jetpack Compose + Material 3  
- 架构：MVVM  
- 状态管理：ViewModel + StateFlow  
- 本地数据库：Room (SQLite)  
- 设置存储：DataStore  
- 异步：Kotlin Coroutines  
- 动效：Compose Animation  
- 云同步（可选）：Firebase / REST API  

---

## 3. 核心功能

## 3.1 首页（日历）

- 月份切换
- 今日高亮
- Emoji 替代日期
- 点击进入记录

---

## 3.2 心情记录（Bottom Sheet）

- 3x3 Emoji 选择
- 点击即保存
- 自动关闭
- 可选 50字备注

---

## 3.3 统计页

- 情绪分布图
- Timeline 时间轴
- 点击可编辑

---

## 3.4 设置页

- 提醒时间设置
- 数据导出 JSON
- 云同步开关

---

# 4. 数据结构

```kotlin
@Entity(tableName = "mood_records")
data class MoodRecord(
    @PrimaryKey val date: String,
    val moodType: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long
)
```

---

# 5. 非功能需求

- 启动 < 800ms  
- 60FPS UI  
- Room 本地优先  
- 无网络依赖可运行  

---

# 6. UI原则

- Soft Minimalism  
- Emotional Pastel  
- Floating Card UI  
- Quiet Interaction  

---

