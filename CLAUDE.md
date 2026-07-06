# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

- 住宅小区物业管理系统 — A residential community property management system built as a JavaEE + Database course project. Target: **中等档 (90分)**.
- 需求文档是`T10.md`
  - 先完整阅读`T10.md`的所有内容
    - 确认技术栈：我们要做中等档（微服务 + Nacos + OpenFeign + Spring Security + JWT）
    - 后面我们基于这个文档来写代码，每次开发前你先确认是否符合需求

## 项目概述

- **后端**: Spring Boot 3.2.12, Spring Cloud 2023.0.3, Spring Cloud Alibaba 2023.0.1.0, Java 17
- **前端**: Vue 3 (Composition API), Element Plus, Vite
- **数据库**: MySQL 8.0, MyBatis-Plus 3.5.10.1, Druid 1.2.23
- **微服务**: Nacos 2.5.2 (service registry), OpenFeign (planned, not yet wired)
- **安全**: Spring Security 6.x + JWT (jjwt 0.12.6, HS256, BCrypt password encryption)

## 命令

```bash
mvn clean compile                          # 构建所有模块
cd frontend && npm run dev                 # 前端开发服务器（端口 5173）
cd frontend && npm run build               # 前端生产环境构建
mysql -u root -p < init.sql                # 初始化数据库（删除已存在的表）
cd nacos/bin && startup.cmd -m standalone  # 启动 Nacos（控制台：localhost:8848，账号密码均为 nacos）```
```
## 架构
```
community-parent/
├── common/                        # 共享的 Result<T> 响应类
├── user-service/    (端口 8081)   # 认证、楼栋、住户、维修人员、维修工单
├── property-service/ (端口 8082)  # 物业费、停车位/停车费、报表
├── frontend/        (端口 5173)   # Vue3 + Element Plus 单页应用
├── init.sql                       # 完整数据库架构 + 触发器 + 存储过程 + 测试数据
└── 数据库设计文档.md                # 数据库设计文档
```


## 关键设计决策

### 用户身份：`ref_id` 模式
`user` 表有一个单一的`ref_id`字段，根据`role`进行解释：:
- resident（业主）→`ref_id`= household_id（真实姓名/电话来自`household`表）
- maintenance（维修员）→ `ref_id` = worker_no（真实姓名/电话来自 `maintenance_staff` 表） 
- admin（管理员）→ `ref_id` = NULL（显示名使用 username）

### 术语
- 楼号 = building number, 层号 = floor number, 户号 = unit number
- 房号 = 层号 + 户号 (e.g., 1302 = floor 13 + unit 02)
- 住号 = 楼号-层号户号（e.g., 28-1301)
- 车位编号 format: `^[A-Z]\d{3}$` (e.g., A001, B002)
- 11位电话号码：`^1[3-9]\d{9}$` (如：18989901512)

### 缴费规则
- 费用记录按月生成（不是预生成整年）
- 缴费时必须包含已逾期的未缴月份
- 缴费期限：下拉选择 1/6/12 个月；后端计算截止月份
- 默认每月缴费截止日：10号（可通过 `PUT /property-fee/deadline` 配置）
- `parking_fee.household_id` 在生成时冻结——不受租户更换影响

### 住户生命周期
- 入住：创建新的 `household` 记录 + 自动生成业主 `user` 账号
- 搬离：设置 `household.status=0` + 禁用关联的 `user` 账号
- 重新入住：总是创建新的 `household` 记录 + 新的 `user` 账号

### 数据库
- 运行 `init.sql` 可重建数据库；所有测试用户的密码均为 `123456`
- BCrypt 哈希值需要在初始化后通过 `PasswordGen.main()` 重新生成（位于 user-service 的测试工具类中）
- 测试账号：`admin`, `zhangsan`, `lisi`, `wangwu`, `weixiu01`, `weixiu02`
- `household` 表对房间没有 UNIQUE 约束——应用程序只检查活跃（status=1）住户

### 注意

- 写代码时附带注释
- 数据库有所修改时相应的修改`数据库设计文档.md`内容