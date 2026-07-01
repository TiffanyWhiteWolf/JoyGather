# 趣聚 Quju

以兴趣为纽带的线下活动社交平台。项目使用 Vue 3 + TypeScript + Vite 构建用户端与运营后台，Spring Boot 提供 REST API。

## 已实现的需求闭环

- 邮箱登录、个人/商家注册、本地激活链接、管理员登录和 Token 会话
- 首页最新/推荐/附近信息流、关键词搜索、多选活动类型、高级筛选和地图模式
- 活动模板、活动克隆、地图选点、高德地点搜索、AI 策划、四步创建、MySQL 草稿保存和审核分流
- 报名安全确认、名额校验、满员候补、取消报名与候补递补
- 扫码签到、活动总结/评价接口、好友关注、兴趣小队、群聊、个人中心
- 后台审核、用户封禁/解封、商家任务、活动下架/恢复、小队停用/恢复
- Spring Boot + MySQL 活动查询、创建、报名、取消、候补递补、后台治理和第三方降级日志接口

当前版本使用本机 MySQL 的项目库 `quju_dev` 持久化数据；Flyway 负责建表和种子数据迁移。前端不再直接读取 `mock/data`。

## 环境要求

- Node.js 18+
- npm 9+
- JDK 8+
- Maven 3.6+
- MySQL 8.x

## 启动项目

首次初始化 MySQL：

```bash
mysql -u root -p < backend/db/init-mysql.sql
```

打开两个终端，先启动后端：

```powershell
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn -Dmaven.repo.local=../.m2/repository spring-boot:run
```

再启动前端：

```powershell
cd frontend
npm --cache ../.npm ci
npm --cache ../.npm run dev
```

真实第三方集成通过环境变量或未提交的 `backend/src/main/resources/application-local.yml` 配置：

- 高德：`QUJU_AMAP_WEB_SERVICE_KEY`
- SMTP：`QUJU_SMTP_HOST`、`QUJU_SMTP_PORT`、`QUJU_SMTP_USERNAME`、`QUJU_SMTP_PASSWORD`、`QUJU_MAIL_FROM`
- OpenAI-compatible：`QUJU_AI_BASE_URL`、`QUJU_AI_API_KEY`、`QUJU_AI_MODEL`、`QUJU_AI_RESPONSE_FORMAT`
- S3-compatible：`QUJU_S3_ENDPOINT`、`QUJU_S3_REGION`、`QUJU_S3_BUCKET`、`QUJU_S3_ACCESS_KEY`、`QUJU_S3_SECRET_KEY`、`QUJU_S3_PUBLIC_BASE_URL`

未配置第三方时，本地开发仍可运行：邮件写入 outbox，地图/AI/S3 走明确降级并记录 `third_party_events`。

内容安全审核采用服务端 OpenAI-compatible Chat Completions 接口和严格 JSON Schema。未配置、超时、异常或结果无法解析时，活动只会进入人工审核，不会自动发布。macOS / zsh 可在启动后端前配置：

```bash
export QUJU_AI_BASE_URL="https://api.openai.com/v1"
export QUJU_AI_API_KEY="你的服务端 API Key"
export QUJU_AI_MODEL="gpt-4o-mini"
export QUJU_AI_RESPONSE_FORMAT="auto"
mvn spring-boot:run
```

也可以复制 `backend/src/main/resources/application-local.example.yml` 为被 Git 忽略的 `application-local.yml`，填写 `quju.ai` 后使用本地配置启动：

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

API Key 只能配置在后端环境变量或未提交的本地配置中，不要写入前端源码或提交到 Git。

访问地址：

- 用户端：http://localhost:5173
- 登录注册：http://localhost:5173/auth
- 运营后台：http://localhost:5173/admin
- 后端 API：http://localhost:8080/api

默认账号：

- 普通用户：`demo@quju.cn` / `12345678`
- 管理员：`admin@quju.cn` / `Admin123456`

## 构建和测试

```powershell
cd frontend
npm --cache ../.npm ci
npm --cache ../.npm run build

cd ../backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
mvn -Dmaven.repo.local=../.m2/repository test
```

## 主要 API

- `GET /api/activities`：活动列表，可传 `keyword`、`category/categories`、`city`、`fee`、`time`、`distance`、`bounds`、`sort`、`page/size`
- `GET /api/activities/{id}`：活动详情
- `POST /api/activities`：创建活动，用户从 `Authorization: Bearer <token>` 获取；人数超过 50 或 AI 风险进入人工审核
- `GET /api/activities/registrations/me`：当前用户报名/候补状态
- `POST /api/activities/{id}/registrations`：当前用户报名或加入候补
- `DELETE /api/activities/{id}/registrations/me`：当前用户取消并自动递补候补首位
- `POST /api/checkins/scan`：扫码签到
- `PUT /api/users/me`：更新个人资料
- `POST /api/files/upload`：上传头像、资质、图片或群文件
- `GET /api/conversations`、`POST /api/conversations/{id}/messages`、`/api/ws`：消息和 WebSocket
- `POST /api/ai/plans`：生成活动策划方案
- `GET /api/admin/dashboard`：运营数据概览，管理员 Token 必填
