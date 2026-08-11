# 本地开发与 D0–D1 运行说明

## MySQL

本地使用 MySQL 8.4：

```shell
docker compose up -d mysql
```

默认开发库为 `invoice_management`，测试库为 `invoice_management_test`。端口冲突时可使用：

```shell
MYSQL_PORT=3308 docker compose up -d mysql
```

应用通过 `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 连接外部数据库，并在启动时执行 Flyway 迁移。已经在共享环境执行的迁移文件禁止修改，只能追加新版本。

## 首个平台管理员

首次部署可临时设置：

```text
APP_BOOTSTRAP_PLATFORM_ADMIN_CAS_ID
APP_BOOTSTRAP_PLATFORM_ADMIN_NAME
```

应用会幂等确认该账号为启用的平台管理员。确认创建成功后可移除变量；变量为空时不会创建任何人员或测试数据。

## 测试

普通测试不需要数据库：

```shell
./mvnw test
```

运行真实 MySQL 集成测试：

```shell
MYSQL_URL='jdbc:mysql://127.0.0.1:3308/invoice_management_test?serverTimezone=UTC' \
MYSQL_USERNAME=root MYSQL_PASSWORD=invoice_dev ./mvnw test
```

数据库集成测试仅在 `MYSQL_URL` 包含 `_test` 时启用，并会再次校验实际数据库名称以 `_test` 结尾。

## API 约定

- 当前用户：`GET /auth/me`
- 当前用户社团：`GET /api/organizations`
- 社团详情：`GET /api/organizations/{id}`
- 成员列表和新增：`GET|POST /api/organizations/{id}/members`
- 成员状态和任期：`PATCH /api/organizations/{id}/members/{casId}`
- 角色及项目范围：`PUT /api/organizations/{id}/members/{casId}/roles`
- 平台社团管理：`POST /api/platform/organizations`、`PATCH /api/platform/organizations/{id}`

除社团列表和平台管理入口外，租户 API 必须携带 `X-Organization-Id`。写接口使用 Cookie CSRF Token，并通过 `X-XSRF-TOKEN` 请求头回传。
