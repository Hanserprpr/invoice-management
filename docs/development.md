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
- 文件元数据登记与本人查看：`POST /api/files`、`GET /api/files/{fileId}`
- 平台文件检测结果回写：`POST /api/platform/organizations/{organizationId}/files/{fileId}/inspection`
- 申请发票列表与新增：`GET|POST /api/applications/{applicationId}/invoices`
- 发票详情、编辑与删除草稿：`GET|PATCH|DELETE /api/invoices/{invoiceId}`
- 替换发票原文件：`POST /api/invoices/{invoiceId}/replace-file`
- 新增与作废发票附件：`POST /api/invoices/{invoiceId}/attachments`、`POST /api/invoices/{invoiceId}/attachments/{attachmentId}/void`
- 作废已提交发票：`POST /api/invoices/{invoiceId}/void`
- 审核队列与单票整理详情：`GET /api/reviews/invoices`、`GET /api/reviews/invoices/{invoiceId}`
- 开始审核：`POST /api/reviews/invoices/{invoiceId}/start`
- 内部通过、退回和拒绝：`POST /api/reviews/invoices/{invoiceId}/approve|return|reject`
- 批量通过：`POST /api/reviews/invoices/batch/approve`
- 单票审核历史：`GET /api/invoices/{invoiceId}/reviews`

项目创建和编辑支持负责人及 `VIEW/SUBMIT/REVIEW/MANAGE` 范围的完整替换。项目状态不能通过通用编辑接口修改，只能使用上述语义化状态接口；所有编辑和状态接口均要求提交当前 `version`。

表单草稿结构保存在 `application_form.draft_schema_json`，发布时复制到不可变的 `form_version`。字段条件只允许结构化操作符，不接受脚本或任意表达式；历史版本没有修改或删除接口。

申请草稿始终绑定创建时的表单版本。自动保存会进行字段类型、条件显示、数值范围和人员归属校验并追加修订记录；正式提交额外检查必填条件、项目/表单状态、时间窗口、提交范围、每人次数、附件就绪状态以及发票归属。表单中的发票 ID 集必须与申请下的未作废发票完全一致，提交成功后发票与申请在同一事务中转为 `SUBMITTED`。

当前文件接口负责上传后元数据登记与安全状态流转，不接收文件二进制内容。新文件默认为 `PENDING`，只有平台检测回写为 `READY` 后才能被发票或附件引用；真实对象存储上传凭证、内容嗅探、病毒扫描及 OCR 适配器将在后续模块接入。

审核入口同时支持社团级 `REVIEWER/CLUB_ADMIN` 与项目级 `REVIEW/MANAGE` 授权。单票结论必须先执行“开始审核”；批量通过会为已提交发票追加隐式 `START_REVIEW` 记录。退回必须使用已发布原因字典并指定可修改字段；申请人只能修改这些发票字段，表单原始答案保持不变。申请状态由所属发票的已提交、审核中、退回、通过、拒绝和作废状态统一派生。

除社团列表和平台管理入口外，租户 API 必须携带 `X-Organization-Id`。写接口使用 Cookie CSRF Token，并通过 `X-XSRF-TOKEN` 请求头回传。
