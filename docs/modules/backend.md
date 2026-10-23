# 后端模块（M0 骨架）

> 目标：先把「能启动、能登录、能迁移」跑通，再接 AI 能力。
> 版本：JDK 21、Spring Boot 3.5.16、MyBatis-Plus 3.5.17、Flyway、MySQL 8。
> Spring AI 1.1.8 在 M1 引入，M0 不建模型调用。

## 0. 速览（只读这节）

- 目标：M0 只做「能启动、能登录、能迁移」，AI 调用留到 M1
- 当前：B01–B02 完成（骨架 + 建库 + Flyway `V1`），下一步 B03
- 关键文件：`pom.xml`、`application-local.yml`、`V1__init.sql`、`SecurityConfig`
- 验收：`/actuator/health` = UP；登录拿 token；`/me` 返回用户名
- 数据库：`127.0.0.1:3306/ai_app`，账号密码见 `application-local.yml`

## 1. 包结构

```
backend/src/main/java/com/tutuchacha/ai/
├─ AiApplication.java
├─ common/        # Result、错误码、全局异常、TraceId
├─ config/        # Security、MyBatis、OpenAPI、CORS
├─ auth/          # 注册、登录、JWT 过滤器
├─ user/          # 用户实体与 Mapper
└─ meta/          # 健康检查、版本信息（后续放模型配置）
```

## 2. 分步实现（每步一个 commit）

### M0-B01 工程初始化
- 文件：`backend/pom.xml`、`AiApplication.java`、`application.yml`、`application-local.yml`
- 依赖：web、validation、security、actuator、mybatis-plus、flyway、mysql、springdoc、jjwt
- 配置：端口 8080、`/api` 前缀、local profile、日志级别
- 验收：`mvn spring-boot:run` 启动，访问 `/actuator/health` 返回 `UP`
- 注意：`application-local.yml` 已被 gitignore，数据库密码只放这里或环境变量

### M0-B02 数据库与迁移
- 文件：`resources/db/migration/V1__init.sql`
- 建表：`sys_user(id, username, password_hash, nickname, role, created_at, updated_at)`
- 唯一索引：`uk_user_username`
- 验收：启动后 Flyway 自动建表，`flyway_schema_history` 有记录
- 失败排查：库不存在 / 账号无权限 / 字符集不是 utf8mb4

### M0-B03 统一响应与异常
- 文件：`common/Result.java`、`ErrorCode.java`、`GlobalExceptionHandler.java`、`TraceIdFilter.java`
- 约定：`{ code, message, data, traceId }`；参数校验错误返回 400
- 验收：访问不存在接口返回统一 JSON，不再返回 Whitelabel 页面

### M0-B04 注册与登录
- 文件：`auth/AuthController`、`AuthService`、`JwtService`、`JwtAuthFilter`、`SecurityConfig`
- 接口：`POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`
- 密码：BCrypt；JWT 有效期 2h；`/api/auth/**` 放行，其余需登录
- 验收：注册 → 登录拿 token → 带 token 访问 `/me` 返回用户名；错误密码返回 401

### M0-B05 文档与可观测
- 文件：`config/OpenApiConfig.java`、`application.yml`
- 内容：Swagger `/swagger-ui.html`；Actuator 暴露 `health,info,metrics,prometheus`
- 验收：Swagger 能看到 auth 接口；`/actuator/prometheus` 有 JVM 指标

### M0-B06 前端联调
- 前端改动：`stores/auth.ts` 改为调 `/api/auth/login`；`LoginView` 调 store；HTTP 封装 `api/http.ts`
- 保留：`VITE_USE_MOCK=true` 时仍走 Mock，方便离线开发
- 验收：前端登录写真实 token，刷新保持；未登录访问 `/chat` 仍被守卫拦截

## 3. 建库命令（已执行，留作换机参考）

```sql
CREATE DATABASE ai_app DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'ai_app'@'localhost' IDENTIFIED BY '<见 application-local.yml>';
GRANT ALL PRIVILEGES ON ai_app.* TO 'ai_app'@'localhost';
FLUSH PRIVILEGES;
```

表结构与排障见 `docs/modules/database.md`。

## 4. 本地启动

```powershell
# 管理员
net start mysql

cd D:\AI_learn\backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

IDEA：打开 `backend/pom.xml`，SDK 选 `D:\JDK21`，Run `AiApplication`，Active profile `local`。

## 5. 最小测试

- `AuthControllerTest`：注册、登录、错误密码、无 token 访问受保护接口
- `ResultTest`：成功/失败结构一致
- 手工：Swagger 调一遍三个接口

## 6. 完成后状态

- M0 结束：前端能真实登录，后端能建表、发 token、出指标
- 进入 M1：会话表、消息表、`AiGateway`、SSE 接口
- 每一步完成后更新 `STATUS.md` 与 `progress/YYYY-MM.md`
