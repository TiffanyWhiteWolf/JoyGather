# 趣聚 Quju

以兴趣为纽带的线下活动社交平台。项目使用 Vue 3 + TypeScript + Vite 构建用户端与运营后台，Spring Boot 提供 REST API。

## 已实现的需求闭环

- 邮箱登录、个人/商家注册、激活邮件结果页和商家营业凭证上传
- 首页信息流、关键词搜索、多选活动类型、高级筛选和地图模式
- 活动模板、活动克隆、AI 策划入口、四步创建、草稿本地保存和审核分流
- 报名安全确认、名额校验、满员候补、取消报名与候补递补
- 扫码与位置签到演示、兴趣小队、群聊、个人中心
- 后台审核、用户封禁/解封、商家任务、活动下架/恢复、小队停用/恢复
- Spring Boot 活动查询、创建、报名、取消和候补递补接口

当前版本使用内存与浏览器 LocalStorage 保存演示数据，重启后后端数据会恢复；生产环境可在 service 层接入数据库而不改变现有接口。

## 环境要求

- Node.js 18+
- npm 9+
- JDK 8+
- Maven 3.6+

## 启动项目

打开两个终端，先启动后端：

```powershell
cd "E:\Desktop\大三下\小学期\quju-platform\backend"
$env:JAVA_HOME="E:\MyJava\jdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

再启动前端：

```powershell
cd "E:\Desktop\大三下\小学期\quju-platform\frontend"
npm install
npm run dev
```

访问地址：

- 用户端：http://localhost:5173
- 登录注册：http://localhost:5173/auth
- 运营后台：http://localhost:5173/admin
- 后端 API：http://localhost:8080/api

## 构建和测试

```powershell
cd "E:\Desktop\大三下\小学期\quju-platform\frontend"
npm run build

cd "E:\Desktop\大三下\小学期\quju-platform\backend"
$env:JAVA_HOME="E:\MyJava\jdk"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
```

## 主要 API

- `GET /api/activities`：活动列表，可传 `keyword`、`category`
- `GET /api/activities/{id}`：活动详情
- `POST /api/activities`：创建活动；人数超过 50 自动进入人工审核
- `POST /api/activities/{id}/registrations?userId=...`：报名或加入候补
- `DELETE /api/activities/{id}/registrations/{userId}`：取消并自动递补候补首位
- `POST /api/ai/plans`：生成活动策划方案
- `GET /api/admin/dashboard`：运营数据概览
