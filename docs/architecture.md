# 项目设计

## Design tokens

| 类别 | 规范 |
| --- | --- |
| 主色 | `#FF6B45` 暖橙；深色 `#172238`；辅助 `#22B8A7` |
| 中性色 | `#F7F5F1` 页面底色；`#FFFFFF` 卡片；`#687083` 次级文本 |
| 圆角 | 10 / 16 / 22 / 32 / pill |
| 阴影 | soft / card / floating 三档 |
| 字号 | 12 / 14 / 16 / 20 / 28 / 40px |
| 间距 | 4px 基准，常用 8 / 12 / 16 / 24 / 32 / 48px |

所有 token 位于 `frontend/src/lib/design-tokens.css`，业务组件只引用语义变量。

## 后端分层

- controller：HTTP 与参数校验
- service：账号、活动、地图范围查询、报名候补、AI 策划、好友关注、小队消息、文件上传和后台统计业务
- dto：输入输出模型
- config/common：跨域、统一响应结构

当前版本使用 MySQL 持久化，Flyway 迁移位于 `backend/src/main/resources/db/migration`，本地初始化脚本位于 `backend/db/init-mysql.sql`。前端通过 `/api` 代理访问 Spring Boot，不再直接读取 mock 数据。

## 认证与集成

- 用户态接口从 `Authorization: Bearer <token>` 解析当前用户，不接受前端传入任意 `userId` 伪造身份。
- `/auth/me` 未登录返回错误；前端个人业务状态从后端加载，新用户默认没有报名、小队、草稿或消息。
- 第三方能力包括高德地点搜索、SMTP 邮件、OpenAI-compatible AI、S3-compatible 文件上传；未配置时写入 `third_party_events` 或 `mail_outbox` 并走明确降级路径。
- WebSocket/STOMP 端点为 `/api/ws`，REST 消息接口保留用于本地开发和连接失败时的降级。
