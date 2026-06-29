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
- service：活动、AI 策划和后台统计业务
- model/dto：领域模型与输入输出模型
- config/common：跨域、统一响应结构

当前版本使用内存数据便于演示；后续可在 service 下接 MyBatis/JPA，并保持 controller API 不变。
