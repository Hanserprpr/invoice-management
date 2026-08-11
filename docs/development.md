# 本地开发与 D0–D2 项目模块运行说明

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
- 项目列表和创建：`GET|POST /api/projects`
- 项目详情和编辑：`GET|PATCH /api/projects/{projectId}`
- 开放收集：`POST /api/projects/{projectId}/open`
- 停止收集：`POST /api/projects/{projectId}/stop-collection`
- 开始整理：`POST /api/projects/{projectId}/start-organizing`
- 归档项目：`POST /api/projects/{projectId}/archive`
- 表单列表和创建：`GET|POST /api/projects/{projectId}/forms`
- 从已发布表单复制草稿：`POST /api/projects/{projectId}/forms/copy`
- 表单详情和草稿编辑：`GET|PATCH /api/forms/{formId}`
- 发布新版本：`POST /api/forms/{formId}/publish`
- 暂停、恢复和结束：`POST /api/forms/{formId}/pause|resume|end`
- 表单版本历史：`GET /api/forms/{formId}/versions`、`GET /api/forms/{formId}/versions/{versionNo}`
- 当前成员可填写表单：`GET /api/application-forms`、`GET /api/application-forms/{formId}`
- 创建或复用申请草稿：`POST /api/application-forms/{formId}/applications`
- 本人申请列表和详情：`GET /api/applications`、`GET /api/applications/{applicationId}`
- 自动保存申请答案：`PATCH /api/applications/{applicationId}`
- 正式提交申请：`POST /api/applications/{applicationId}/submit`
- 申请答案修订历史：`GET /api/applications/{applicationId}/revisions`

项目创建和编辑支持负责人及 `VIEW/SUBMIT/REVIEW/MANAGE` 范围的完整替换。项目状态不能通过通用编辑接口修改，只能使用上述语义化状态接口；所有编辑和状态接口均要求提交当前 `version`。

表单草稿结构保存在 `application_form.draft_schema_json`，发布时复制到不可变的 `form_version`。字段条件只允许结构化操作符，不接受脚本或任意表达式；历史版本没有修改或删除接口。

申请草稿始终绑定创建时的表单版本。自动保存会进行字段类型、条件显示、数值范围和人员归属校验并追加修订记录；正式提交额外检查必填条件、项目/表单状态、时间窗口、提交范围和每人次数。附件与发票字段目前只校验 JSON 形状，真实文件状态和发票归属将在文件及发票模块中校验。

除社团列表和平台管理入口外，租户 API 必须携带 `X-Organization-Id`。写接口使用 Cookie CSRF Token，并通过 `X-XSRF-TOKEN` 请求头回传。
