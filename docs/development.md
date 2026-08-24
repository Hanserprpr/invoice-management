# 本地开发与 D0–D5 运行说明

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

## 接口文档（Swagger UI）

本地启动后访问 `http://localhost:8080/api/swagger-ui.html`，OpenAPI 描述位于 `/api/v3/api-docs`（YAML 为 `/api/v3/api-docs.yaml`）。

部署在带路径前缀的反向代理后（如 `https://i.sdu.edu.cn/invoice`），用 `API_DOCS_EXTERNAL_PREFIX=/invoice` 让页面按外部路径去取文档，必要时再用 `API_DOCS_OAUTH2_REDIRECT_URL` 指定完整回调地址；本地直连时两者留空即可。代理需下发 `X-Forwarded-Prefix`，`/api/swagger-ui.html` 的跳转才会落在前缀内。

Swagger UI 上每个接口的摘要和详细说明直接来自控制器方法的 Javadoc：首句作为 summary，其余段落作为 description。这依赖 `therapi-runtime-javadoc` 注解处理器在编译期把注释写进 `*__Javadoc.json`，所以改完注释要重新 `compile` 才会在文档里生效。

文档路径由 `API_DOCS_ENABLED` 控制：开发默认 `true` 且允许匿名访问，生产 profile 默认 `false`，关闭后这些路径不再放行。接口本身仍需登录，先在浏览器完成 `GET /api/oauth2/authorization/sdu` 登录再回到 Swagger UI 调试；写操作的 CSRF 头由 Swagger UI 自动从 `XSRF-TOKEN` Cookie 读取并附带。需要租户上下文的接口记得填写 `X-Organization-Id` 请求头。

## API 约定

- 当前用户：`GET /api/auth/me`
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
- R2 直传初始化、完成确认与安全下载：`POST /api/files/uploads`、`POST /api/files/{fileId}/upload-complete`、`GET /api/files/{fileId}/download-url`
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
- 发票台账、筛选、排序与金额合计：`GET /api/ledger/invoices`
- 单票聚合时间线：`GET /api/ledger/invoices/{invoiceId}/timeline`
- 本人保存筛选：`GET|POST /api/ledger/saved-filters`、`PATCH|DELETE /api/ledger/saved-filters/{filterId}`
- 发票识别任务、任务历史与建议：`POST /api/invoices/{invoiceId}/recognition`、`GET /api/invoices/{invoiceId}/recognition/jobs|suggestions`
- 人工确认识别建议：`PUT /api/invoices/{invoiceId}/recognition/suggestions`
- 运行和查看预检：`POST|GET /api/invoices/{invoiceId}/prechecks`
- 处理预检命中：`POST /api/invoices/{invoiceId}/prechecks/{resultId}/resolve`
- 规则集列表和创建：`GET|POST /api/rule-sets`
- 编辑规则集、发布不可变版本：`PATCH /api/rule-sets/{id}`、`POST /api/rule-sets/{id}/versions`
- 单票纸票详情：`GET /api/invoices/{invoiceId}/paper`
- 项目纸票列表与连续扫码：`GET /api/projects/{projectId}/paper-items`、`POST /api/projects/{projectId}/paper/scans`
- 社员声明/撤销、社团收取：`POST /api/invoices/{invoiceId}/paper/declare|revoke-declaration|receive`
- 纸票退回、异常、更正、移交和归档：`POST /api/invoices/{invoiceId}/paper/state`
- 导出批次列表、创建和详情：`GET|POST /api/export-batches`、`GET /api/export-batches/{batchId}`
- 异步生成、建立新修订、取消、完成和归档：`POST /api/export-batches/{batchId}/generate|revisions|cancel|complete|archive`
- 导出产物安全下载：`GET /api/export-batches/{batchId}/artifacts/{artifactId}/download-url`
- 平台外状态历史和追加：`GET|POST /api/export-batches/{batchId}/external-events`
- 更正平台外事件：`POST /api/export-batches/{batchId}/external-events/{eventId}/corrections`
- 通知列表、已读和忽略：`GET /api/notifications`、`POST /api/notifications/{id}/read|dismiss`
- 换届交接列表和执行：`GET|POST /api/handovers`
- 审计日志只读查询：`GET /api/audit-logs`

项目创建和编辑支持负责人及 `VIEW/SUBMIT/REVIEW/MANAGE` 范围的完整替换。项目状态不能通过通用编辑接口修改，只能使用上述语义化状态接口；所有编辑和状态接口均要求提交当前 `version`。

表单草稿结构保存在 `application_form.draft_schema_json`，发布时复制到不可变的 `form_version`。字段条件只允许结构化操作符，不接受脚本或任意表达式；历史版本没有修改或删除接口。

申请草稿始终绑定创建时的表单版本。自动保存会进行字段类型、条件显示、数值范围和人员归属校验并追加修订记录；正式提交额外检查必填条件、项目/表单状态、时间窗口、提交范围、每人次数、附件就绪状态以及发票归属。表单中的发票 ID 集必须与申请下的未作废发票完全一致，提交成功后发票与申请在同一事务中转为 `SUBMITTED`。

文件二进制通过 Cloudflare R2 私有桶直传，应用不代理大文件流量。`POST /api/files/uploads` 返回短时预签名 `PUT` 地址及必须原样携带的请求头；该地址只写临时对象键。上传后调用完成确认，服务端通过 R2 `HEAD` 重新校验对象大小、类型和 SHA-256 元数据，再以服务端凭据复制到正式对象键、删除临时对象并转入 `SCANNING`，避免仍在有效期内的 PUT 地址覆盖待检测文件。只有平台检测回写为 `READY` 后文件才能被业务引用和下载。下载地址签发前会重新检查租户、文件状态以及上传者或关联发票的数据权限，返回的短时 `GET` 地址不能替代业务授权。

R2 默认关闭，未配置密钥时应用和测试仍可启动，真实上传/下载返回 `OBJECT_STORAGE_NOT_CONFIGURED`。后期在部署环境设置：

- `R2_ENABLED=true`
- `R2_ACCOUNT_ID`
- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`
- `R2_BUCKET`
- 可选 `R2_UPLOAD_URL_TTL`（默认 `10m`）和 `R2_DOWNLOAD_URL_TTL`（默认 `5m`）
- 可选 `R2_REQUEST_TIMEOUT`（默认 `30s`）和 `R2_ATTEMPT_TIMEOUT`（默认 `10s`）

这些值不得写入配置文件、日志、数据库或 Flyway。R2 桶保持私有；浏览器直传前还需在 R2 配置仅允许前端正式域名、`PUT/GET/HEAD` 和必要请求头的 CORS 规则。

上传固化后会创建 `FILE_SECURITY_SCAN` 异步任务。工作线程从 R2 下载正式对象到受控临时文件，重新计算正文 SHA-256、核对真实大小，并通过文件签名识别 PDF、JPEG、PNG；OFD 还必须是包含根 `OFD.xml` 的 ZIP 包。正文与登记信息不一致时转为 `REJECTED`。任务领取使用数据库锁，失败按退避时间最多重试三次，工作线程在访问租户表之前会显式恢复任务中的 `organization_id`；执行节点异常退出留下的 `RUNNING` 任务超过 15 分钟会被重新调度或终止，避免永久卡死。

安全检测通过后，可调用 `POST /api/invoices/{invoiceId}/recognition` 创建识别任务。同一张发票已有等待中或执行中的任务时会返回原任务，避免重复提交。识别服务通过 `OCR_ENDPOINT` 适配外部 OCR/二维码提供方，请求正文为文件字节，响应字段为 `rawText`、`qrRaw` 和 `fields`；每个建议字段包含 `value` 与 `confidence`。服务端只接受发票字段白名单，识别原文保存在内部任务结果中，结构化建议保存在 `recognition_suggestion`，人工通过 `PUT /api/invoices/{invoiceId}/recognition/suggestions` 逐条接受、更正或拒绝。未配置 OCR 时任务成功进入 `MANUAL_ENTRY`，不会阻断人工录入。

识别相关环境变量：`OCR_ENABLED`、`OCR_ENDPOINT`、`OCR_API_KEY`、`OCR_TIMEOUT`、`OCR_WORKER_ENABLED`、`OCR_POLL_INTERVAL`。生产环境应让 OCR 服务端点只接受受信网络调用，并定期轮换密钥。

规则集由社团管理员维护，版本一经发布不提供修改接口。项目通过 `ruleSetVersionId` 固定引用已经生效的版本；后续发布不会改变历史项目。当前规则支持票面金额上限、申请金额上限、允许的发票类型、必填销售方税号和必填购买方税号，并为每条规则指定 `BLOCK/WARNING/INFO`。开始审核时自动执行规则与查重，完全相同的数电票号、代码加号码或原文件摘要产生阻断，销售方税号、日期和金额相同产生警告。结果只追加保存；审核只读取当前规则版本和每类最新结果。跨社团命中不会在 API 中返回另一社团的发票 ID 或内容。

项目创建或编辑时可启用 `paperRequired`。启用后，发票内部通过会幂等创建 `PENDING_DELIVERY` 纸票记录；社员可声明已交及在社团确认前撤销，具备项目审核权限的成员可手工或扫码确认收取，项目管理员可记录退回、异常、更正、外部移交和归档。每次变化同时追加 `paper_event` 和审计日志，写入使用独立的 `paper_item.version` 乐观锁。扫码原文不会落库，只保存 SHA-256 与解析后的最小票据标识；未启用纸票的项目会拒绝全部纸票操作。

病毒扫描采用可替换的 ClamAV `INSTREAM` 适配器。默认未启用时，任务结果写入 `MANUAL_REVIEW`，文件保持 `SCANNING`，必须由平台人工检测回写，绝不自动标记安全。启用时配置：

- `CLAMAV_ENABLED=true`
- `CLAMAV_HOST`（默认 `127.0.0.1`）
- `CLAMAV_PORT`（默认 `3310`）
- 可选 `CLAMAV_CONNECT_TIMEOUT`（默认 `3s`）和 `CLAMAV_READ_TIMEOUT`（默认 `2m`）
- 可选 `FILE_SCAN_WORKER_ENABLED` 和 `FILE_SCAN_POLL_INTERVAL`；前者默认跟随 `R2_ENABLED`，后者默认 `5s`

ClamAV TCP 协议本身不提供认证或加密，只能部署在同机或受控私网，禁止向公网开放。检测临时文件在每次任务结束后都会删除。另有孤立文件清理任务定期处理已过期且未被任何业务表引用的 `PENDING/REJECTED/FAILED` 文件；删除时会锁定数据库记录并再次检查引用，避免清理与业务引用并发时删除有效对象。可通过 `FILE_CLEANUP_ENABLED` 和 `FILE_CLEANUP_INTERVAL` 控制，默认随 R2 启用并每小时运行。

导出生成使用 `EXPORT_GENERATION` 异步任务，从 R2 读取选中发票的原票和有效附件，生成台账 XLSX、清单 PDF、附件 ZIP 与 `manifest.json`，每个产物都记录 SHA-256 并由服务端直接上传私有 R2。可通过 `EXPORT_WORKER_ENABLED` 和 `EXPORT_POLL_INTERVAL` 控制工作线程，前者默认跟随 `R2_ENABLED`。同一发票由 `invoice_export_reservation` 防止进入两个活动批次；取消释放占用，完成和归档保留追溯，新修订会归档旧版本并原子转移占用。

通知首先写入站内收件箱，再创建 `NOTIFICATION_DELIVERY` 异步任务。默认投递适配器返回 `IN_APP_ONLY`；如需接企业微信、短信或邮件网关，可配置 `NOTIFICATION_DELIVERY_ENABLED=true`、`NOTIFICATION_DELIVERY_ENDPOINT`、`NOTIFICATION_DELIVERY_API_KEY`、`NOTIFICATION_DELIVERY_TIMEOUT`，并通过 `NOTIFICATION_WORKER_ENABLED` 与 `NOTIFICATION_POLL_INTERVAL` 启动重试工作线程。通知正文和日志不得包含文件内容、令牌或跨租户数据。

审核入口同时支持社团级 `REVIEWER/CLUB_ADMIN` 与项目级 `REVIEW/MANAGE` 授权。单票结论必须先执行“开始审核”；批量通过会为已提交发票追加隐式 `START_REVIEW` 记录。退回必须使用已发布原因字典并指定可修改字段；申请人只能修改这些发票字段，表单原始答案保持不变。申请状态由所属发票的已提交、审核中、退回、通过、拒绝和作废状态统一派生。

台账列表与合计查询共用相同的租户、权限和业务筛选条件，合计不受当前页大小影响。普通社员只能查看本人发票；社团审核或审计角色可查看授权范围，项目负责人和具有 `VIEW/REVIEW/MANAGE` 范围的成员只能查看相应项目。时间线访问使用同一权限判定，不会因已知发票 ID 而绕过数据范围。

除社团列表和平台管理入口外，租户 API 必须携带 `X-Organization-Id`。写接口使用 Cookie CSRF Token，并通过 `X-XSRF-TOKEN` 请求头回传。

## D5 生产准备

生产启动使用 `prod` Profile，共享 Session、Redis 限流、健康探针、Prometheus 指标、请求关联 ID 和启动配置校验会同时生效。备份恢复、密钥轮换、真实脱敏样本回放和上线验收见 [production-runbook.md](production-runbook.md)。
