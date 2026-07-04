# 票据审核系统 (Bill Review System)

基于 RuoYi-Vue 前后端分离框架开发的在线票据提交与审核系统。四角色协作：超管全局监控、管理员管人管类别、审核员审批票据、普通用户提交票据。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.8 |
| ORM | MyBatis-Plus 3.5.9 |
| 安全框架 | Spring Security + JWT |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis |
| 邮件 | JavaMailSender (163 SMTP) |
| AI 框架 | LangChain4j + DeepSeek (通义千问) |
| 前端框架 | Vue 3.5 + Vite 5 |
| UI 组件库 | Element Plus 2.13 |
| 图表 | ECharts 5 |
| 状态管理 | Pinia 3 |

## 项目结构

```
Bill-Review-System/
├── zsc_backend/                           # 后端 (Spring Boot 多模块)
│   ├── zsc-admin/                         # 应用入口 + Controller（含系统初始化）
│   ├── zsc-framework/                     # 框架核心 (Security / Redis / JWT / 强制下线)
│   ├── zsc-system/                        # 系统管理 (用户 / 角色 / 菜单)
│   ├── zsc-module/                        # 业务模块 (票据 / 审核 / 注册 / 类别 / 邮件)
│   ├── zsc-common/                        # 公共工具 + 实体
│   └── sql/init-system.sql                # 数据库初始化脚本（单文件）
├── zsc_frontend/                          # 前端 (Vue 3 SPA)
│   └── src/
│       ├── views/admin/super/             # 超管仪表盘
│       ├── views/biz/admin/               # 管理员页面 (概览/用户管理/注册审核)
│       ├── views/biz/bill/                # 票据管理 (我的票据/审核/概览)
│       ├── views/biz/bizCategory/         # 类别管理
│       ├── api/biz/                       # API 封装 (admin.js/bill.js)
│       ├── router/                        # 路由配置 (含 dynamicRoutes)
│       └── store/                         # 状态管理
├── deploy/                                # 部署配置 (Nginx / 构建脚本)
└── docs/                                  # 设计文档
```

## 系统角色

| 角色 | role_key | 登录入口 | 职责 |
|------|----------|----------|------|
| 超管 | `admin` | `/admin/super` | 全局数据监控、审核员工作量、管理管理员账号 |
| 管理员 | `admin_user` | `/admin/dashboard` | 用户/类别管理、注册审批、概览统计 |
| 审核员 | `reviewer` | `/bill/manage` | 审核票据（通过/退回+意见）、批量审核 |
| 普通用户 | `user` | `/bill/manage` | 票据 CRUD、提交、查看审核结果 |

## 票据流转

```
草稿(0) → 已提交(1) → 已通过(2)
                    → 已退回(3) → 可重新编辑提交
```

## 核心功能

| 功能 | 说明 |
|------|------|
| Web 初始化 | 无用户时登录页显示初始化表单，创建首个超管 |
| 邮件通知 | 账号创建/停用/删除时发送邮件（163 SMTP @Async） |
| 强制下线 | 停用/删除用户时清除 Redis token，下次请求 401 |
| 批量操作 | 票据批量审核、用户批量启停/删除 |
| 排序分页 | 金额/创建时间/审批时间排序，分页支持 |
| 类别保护 | "其他"不可删/改，删类别时票据自动迁移 |
| 积压预警 | 超 3 天未审票据标记，一键筛选 |
| 数据隔离 | 审核员只看自己审的，用户只看自己提交的 |
| 单点登录 | 同一账号只能一处在线，后登踢前登 |
| 操作日志 | `@Log` 注解自动记录增删改查，支持筛选/清空/导出 |
| 自动导出 | 可配置定时导出操作日志 Excel 并清空 |
| 软删除 | 用户软删除保留审计凭证，用户名不释放 |
| AI 助手 | 自然语言查询系统数据，Tool Calling 调用 Service |

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis
- Maven 3.6+
- Node.js 22+

### 后端启动

```bash
# 1. 创建数据库并导入
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS \`zsc-train\` DEFAULT CHARSET utf8mb4"
mysql -u root -p zsc-train < zsc_backend/sql/init-system.sql

# 2. 配置数据库
# 复制 zsc_backend/zsc-admin/src/main/resources/application-druid.example.yml
# 重命名为 application-druid.yml，填入你的数据库密码和邮件配置

# 3. 启动
cd zsc_backend
mvn spring-boot:run -pl zsc-admin
```

### 前端启动

```bash
cd zsc_frontend
npm install
npm run dev
```

访问: http://localhost → 系统初始化表单 → 创建超管 → 正常使用。

### 邮件配置

编辑 `application-druid.yml`：

```yaml
mail:
    host: smtp.163.com
    port: 465
    username: your-email@163.com
    password: 邮箱授权码
    from: your-email@163.com
```

## 相关资源

- [RuoYi-Vue 框架](https://gitee.com/y_project/RuoYi-Vue)
- [Element Plus 文档](https://element-plus.org/)
- [MyBatis-Plus 文档](https://baomidou.com/)
