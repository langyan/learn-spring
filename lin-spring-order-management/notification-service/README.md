# notification-service（:8084 / notifications_db）

通知旁观者：订阅全部三个主题（om-orders / om-inventory / om-payments），按 `eventId` 幂等登记（`sent_notifications` 主键 + `ON CONFLICT DO NOTHING`）后发模拟通知（日志输出 EMAIL 模板文案）。Kafka 至少一次投递下，客户不会收到两条相同通知。

完整场景见 `../api.http` 与 `../README.md`。
