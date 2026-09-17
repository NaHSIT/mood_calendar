# 学校平台适配协议（草案）

此协议只描述窗口 E 的未来适配边界。当前版本仅实现未接入和本地模拟网关，不配置真实 endpoint、认证信息或消息接收人。

## 版本与幂等

- 协议版本：`mood-calendar.school-alert.v1`。
- 每个告警投递使用稳定 `idempotencyKey`，建议由 `alertId + recipientScope + payloadRevision` 确定。
- 超时结果是未知状态，先按幂等键查询回执，再决定重试；不能直接创建新投递。
- 迟到回执只允许推进对应尝试，不能把已永久失败、已撤销或更新修订的记录回滚为成功。

## 最小请求示例

```json
{
  "protocolVersion": "mood-calendar.school-alert.v1",
  "idempotencyKey": "demo-alert-001:teacher-scope-01:r3",
  "payloadRevision": 3,
  "alert": {
    "eventId": "demo-alert-001",
    "studentCode": "DEMO-S042",
    "attentionLevel": "MODERATE",
    "reasonTags": ["ASSESSMENT_REVIEW_REQUIRED"],
    "occurredAtEpochMillis": 1790000000000,
    "ruleVersion": "demo-risk-v1"
  },
  "recipientScope": "teacher-scope-01",
  "simulation": true
}
```

禁止字段包括：学生真实姓名、证件/联系方式、量表逐题答案、详细分数、日记文本、照片 URI、原始心率/睡眠序列、健康设备标识、平台密钥。

## 结果语义

| 结果 | 含义 | 队列动作 |
|---|---|---|
| `NotConfigured` | 未配置真实平台 | 保持未接入，不显示发送成功 |
| `SimulatedAccepted(receiptId)` | 本地模拟网关接收 | 记模拟送达；教师仍未确认 |
| `TemporaryFailure(retryAfter)` | 超时、临时不可用或可重试错误 | 按策略退避并持久化下次时间 |
| `PermanentFailure(code)` | 协议拒绝或不可恢复配置错误 | 停止自动重试，形成处理待办 |
| `AuthorizationRejected` | 未来认证失败 | 停止发送并提示管理员配置，不降级绕过 |

真实实现未来应通过依赖注入获得 endpoint 和凭据，凭据由安全配置提供；仓库、日志和演示 payload 中均不得出现密钥。

## 回执与处置

平台接收回执、教师确认、教师联系记录和干预状态是不同事件：

- 回执查询按 `idempotencyKey`，返回平台事件 ID、接收时间、状态和协议版本。
- 教师确认必须经过应用身份与责任范围校验，不能由“已送达”自动推导。
- 处置状态提交使用独立幂等键，重复提交返回已存在结果。
- 所有时间均使用 UTC epoch milliseconds；展示时才转本地时区。

## 模拟网关约束

模拟实现应支持确定性的成功、临时失败后成功、永久失败、超时后迟到回执四种脚本，并允许注入时钟。界面和审计记录始终保留 `simulation=true`，不能在任何文案中称为真实学校平台投递。

