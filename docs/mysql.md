# MySQL 数据库设计

> 对应《社团发票收集与管理辅助工具 PRD v3.0》的 P0 开发基线。
> 数据库：MySQL 8.0+；存储引擎：InnoDB；字符集：`utf8mb4`；时区：UTC。

## 1. 设计约定

- `user.cas_id` 即学工号，作为用户主键，创建后不可修改。
- 业务实体的 `id` 使用应用程序生成的 26 位 ULID，MySQL 中使用 `CHAR(26)` 存储。
- 角色、权限等小型固定字典使用 `BIGINT UNSIGNED AUTO_INCREMENT`。
- 金额统一使用 `DECIMAL(12,2)`，禁止使用浮点数。
- 时间统一使用 `DATETIME(3)` 存储 UTC，界面按 `Asia/Shanghai` 显示。
- 需要并发修改的业务表使用 `version` 实现乐观锁。
- 正式数据不硬删除，通过状态、`voided_at` 或追加事件保留历史。
- JSON 字段只存储动态结构、快照或摘要，需要关联、筛选和保证一致性的数据必须拆表。
- 不同社团的数据隔离由服务端鉴权和表间关联共同保证，不依赖主键的不可猜测性。

### 1.1 多租户隔离

本系统采用“单数据库、共享表、共享 Schema”模式，以 `organization_id` 作为租户标识：

- 除全局表外，所有租户业务表都直接保存 `organization_id CHAR(26) NOT NULL`，即使该字段可通过上级实体推导也不省略。
- 当前租户来自已登录用户选择的 `X-Organization-Id`，服务端必须先校验有效成员关系，再写入 `TenantContext`；不得直接信任客户端提交的租户 ID。
- MyBatis-Plus 使用 `TenantLineInnerInterceptor` 为查询、更新和删除自动追加 `organization_id = 当前租户`，并在插入时填入当前租户。
- 服务层仍需校验社团、项目、表单、申请、发票、文件及批次的完整资源归属链；租户拦截器不能替代业务鉴权。
- 自定义 SQL、批量操作、后台任务和数据导出必须显式携带租户上下文，并通过跨租户隔离测试。
- 租户字段由服务端赋值；请求体中的 `organization_id` 与当前租户不一致时直接拒绝。

不参与租户 SQL 自动注入的全局表白名单：

```text
user
password_setup_token
organization
role
permission
role_permission
```

`organization_member` 是租户入口表，平台校验成员关系时可通过受控入口绕过自动注入，普通业务查询仍必须按 `organization_id` 限定。`rule_set` 和 `dictionary_version` 允许 `organization_id` 为空，以表示平台通用模板，因此也使用独立的受控查询。

Redis Key 和对象存储路径同样包含租户：

```text
permission:{organizationId}:{casId}
idempotency:{organizationId}:{casId}:{key}
organizations/{organizationId}/invoices/{invoiceId}/...
organizations/{organizationId}/exports/{batchId}/...
```

## 2. 数据表总览

| 领域 | 数据表 |
|---|---|
| 账号与权限 | `user`、`password_setup_token`、`organization`、`organization_member`、`role`、`organization_member_role`、`permission`、`role_permission` |
| 规则与字典 | `rule_set`、`rule_set_version`、`dictionary_version`、`dictionary_item` |
| 项目与表单 | `project`、`project_manager`、`project_access`、`application_form`、`form_version` |
| 申请与文件 | `application`、`application_revision`、`file_object`、`async_job` |
| 发票与预检 | `invoice`、`invoice_file_revision`、`attachment`、`recognition_suggestion`、`scan_event`、`invoice_precheck_result` |
| 整理与审核 | `club_review`、`invoice_tag`、`paper_item`、`paper_event` |
| 导出与外部状态 | `export_batch`、`export_batch_invoice`、`invoice_export_reservation`、`export_artifact`、`external_status_event`、`external_status_event_attachment` |
| 通知、交接与审计 | `notification`、`saved_invoice_filter`、`handover_record`、`idempotency_record`、`audit_log` |

## 3. 账号、社团与 RBAC

RBAC 关系：

```text
user -> organization_member -> organization_member_role
     -> role -> role_permission -> permission
```

### 3.1 user 用户表

```sql
CREATE TABLE `user` (
    cas_id VARCHAR(20) PRIMARY KEY COMMENT '学工号/CAS 唯一标识',
    name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NULL COMMENT '使用 CAS 登录时可为空',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_platform_admin BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT ck_user_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED', 'LEFT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 password_setup_token 一次性密码设置令牌

只保存令牌哈希，明文令牌仅在创建时显示一次。

```sql
CREATE TABLE password_setup_token (
    id CHAR(26) PRIMARY KEY,
    cas_id VARCHAR(20) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    created_by_cas_id VARCHAR(20) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_password_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_token_user FOREIGN KEY (cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_password_token_creator FOREIGN KEY (created_by_cas_id)
        REFERENCES `user`(cas_id),
    INDEX idx_password_token_user (cas_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 organization 社团表

```sql
CREATE TABLE organization (
    id CHAR(26) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(30) NOT NULL DEFAULT 'CLUB',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT ck_organization_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    INDEX idx_organization_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.4 organization_member 社团成员表

```sql
CREATE TABLE organization_member (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    cas_id VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    term_start DATE NULL,
    term_end DATE NULL,
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT uk_org_member UNIQUE (organization_id, cas_id),
    CONSTRAINT fk_org_member_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_org_member_user FOREIGN KEY (cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_org_member_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'LEFT')),
    CONSTRAINT ck_org_member_term
        CHECK (term_end IS NULL OR term_start IS NULL OR term_end >= term_start),
    INDEX idx_org_member_user (cas_id, status),
    INDEX idx_org_member_org_status (organization_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.5 role、permission 及关联表

```sql
CREATE TABLE role (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_role_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE permission (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_permission_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE organization_member_role (
    member_id CHAR(26) NOT NULL,
    organization_id CHAR(26) NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    effective_from DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    effective_until DATETIME(3) NULL,
    assigned_by_cas_id VARCHAR(20) NULL,
    PRIMARY KEY (member_id, role_id),
    CONSTRAINT fk_member_role_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_member_role_member FOREIGN KEY (member_id)
        REFERENCES organization_member(id),
    CONSTRAINT fk_member_role_role FOREIGN KEY (role_id)
        REFERENCES role(id),
    CONSTRAINT fk_member_role_assigner FOREIGN KEY (assigned_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_member_role_period
        CHECK (effective_until IS NULL OR effective_until >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE role_permission (
    role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id)
        REFERENCES role(id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

角色和权限初始数据：

```sql
INSERT INTO role (code, name) VALUES
('MEMBER', '社员'),
('PROJECT_MANAGER', '项目负责人'),
('REVIEWER', '社团审核人'),
('CLUB_ADMIN', '社团管理员'),
('AUDITOR', '审计只读用户');

INSERT INTO permission (code, name) VALUES
('project:read', '查看项目'),
('project:create', '创建项目'),
('form:create', '创建申请表'),
('form:publish', '发布申请表'),
('application:create', '创建本人申请'),
('application:update-own-answer', '修改本人原始答案'),
('application:add-internal-field', '添加社团整理字段'),
('application:review', '作出内部审核结论'),
('ticket:confirm-own', '确认本人已交纸票'),
('ticket:mark-received', '标记纸票已收'),
('export:create', '创建导出批次'),
('member:manage', '管理成员'),
('role:manage', '管理成员角色'),
('audit-log:read', '查看审计日志');
```

## 4. 规则与字典

### 4.1 rule_set 和 rule_set_version

`rule_set` 表示可持续维护的规则集，`rule_set_version` 表示每次发布后的不可变版本。

```sql
CREATE TABLE rule_set (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NULL COMMENT '为空表示平台通用模板',
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by_cas_id VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_rule_set_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_rule_set_creator FOREIGN KEY (created_by_cas_id)
        REFERENCES `user`(cas_id),
    INDEX idx_rule_set_org (organization_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE rule_set_version (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NULL COMMENT '为空表示平台通用模板版本',
    rule_set_id CHAR(26) NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    rules_json JSON NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    published_by_cas_id VARCHAR(20) NOT NULL,
    published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_rule_version_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_rule_set_version UNIQUE (rule_set_id, version_no),
    CONSTRAINT fk_rule_version_set FOREIGN KEY (rule_set_id)
        REFERENCES rule_set(id),
    CONSTRAINT fk_rule_version_publisher FOREIGN KEY (published_by_cas_id)
        REFERENCES `user`(cas_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

“每个社团最多一个启用默认规则集”由服务端在事务内锁定社团后校验。

### 4.2 dictionary_version 和 dictionary_item

费用类别、退回原因、标签、附件要求及外部状态显示名称均通过版本化字典管理。

```sql
CREATE TABLE dictionary_version (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NULL COMMENT '为空表示平台通用字典',
    dictionary_type VARCHAR(40) NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_by_cas_id VARCHAR(20) NULL,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_dictionary_version
        UNIQUE (organization_id, dictionary_type, version_no),
    CONSTRAINT fk_dictionary_version_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_dictionary_version_publisher FOREIGN KEY (published_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_dictionary_version_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dictionary_item (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NULL COMMENT '为空表示平台通用字典项',
    dictionary_version_id CHAR(26) NOT NULL,
    code VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    metadata_json JSON NULL,
    CONSTRAINT fk_dictionary_item_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_dictionary_item
        UNIQUE (dictionary_version_id, code),
    CONSTRAINT fk_dictionary_item_version FOREIGN KEY (dictionary_version_id)
        REFERENCES dictionary_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 5. 项目与动态表单

### 5.1 project 项目表

```sql
CREATE TABLE project (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    budget DECIMAL(12,2) NULL,
    funding_source VARCHAR(200) NULL,
    paper_required BOOLEAN NOT NULL DEFAULT FALSE,
    visibility VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED',
    rule_set_version_id CHAR(26) NULL,
    start_at DATETIME(3) NULL,
    end_at DATETIME(3) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by_cas_id VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    archived_at DATETIME(3) NULL,
    CONSTRAINT fk_project_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_project_rule_version FOREIGN KEY (rule_set_version_id)
        REFERENCES rule_set_version(id),
    CONSTRAINT fk_project_creator FOREIGN KEY (created_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_project_status CHECK (status IN
        ('DRAFT', 'COLLECTING', 'COLLECTION_STOPPED', 'ORGANIZING', 'ARCHIVED')),
    CONSTRAINT ck_project_period
        CHECK (end_at IS NULL OR start_at IS NULL OR end_at >= start_at),
    INDEX idx_project_org_status (organization_id, status),
    INDEX idx_project_period (start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 5.2 project_manager 和 project_access

```sql
CREATE TABLE project_manager (
    project_id CHAR(26) NOT NULL,
    organization_id CHAR(26) NOT NULL,
    member_id CHAR(26) NOT NULL,
    assigned_by_cas_id VARCHAR(20) NULL,
    assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id, member_id),
    CONSTRAINT fk_project_manager_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_project_manager_project FOREIGN KEY (project_id)
        REFERENCES project(id),
    CONSTRAINT fk_project_manager_member FOREIGN KEY (member_id)
        REFERENCES organization_member(id),
    CONSTRAINT fk_project_manager_assigner FOREIGN KEY (assigned_by_cas_id)
        REFERENCES `user`(cas_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE project_access (
    project_id CHAR(26) NOT NULL,
    organization_id CHAR(26) NOT NULL,
    member_id CHAR(26) NOT NULL,
    access_type VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    granted_by_cas_id VARCHAR(20) NULL,
    granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (project_id, member_id, access_type),
    CONSTRAINT fk_project_access_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_project_access_project FOREIGN KEY (project_id)
        REFERENCES project(id),
    CONSTRAINT fk_project_access_member FOREIGN KEY (member_id)
        REFERENCES organization_member(id),
    CONSTRAINT fk_project_access_granter FOREIGN KEY (granted_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_project_access_type
        CHECK (access_type IN ('VIEW', 'SUBMIT', 'REVIEW', 'MANAGE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

项目负责人或授权成员必须属于项目所属社团，该跨表规则由服务端在事务内校验。

### 5.3 application_form 和 form_version

`application_form` 避免使用过于宽泛的表名 `form`。表单每次发布新增一条不可变的 `form_version`。

```sql
CREATE TABLE application_form (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    project_id CHAR(26) NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    submission_scope VARCHAR(30) NOT NULL DEFAULT 'PROJECT_AUTHORIZED',
    starts_at DATETIME(3) NULL,
    ends_at DATETIME(3) NULL,
    max_submissions_per_user INT UNSIGNED NOT NULL DEFAULT 1,
    draft_schema_json JSON NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by_cas_id VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_form_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_form_project FOREIGN KEY (project_id)
        REFERENCES project(id),
    CONSTRAINT fk_form_creator FOREIGN KEY (created_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_form_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'ENDED')),
    CONSTRAINT ck_form_period
        CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at >= starts_at),
    INDEX idx_form_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE form_version (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    form_id CHAR(26) NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    schema_json JSON NOT NULL,
    dictionary_snapshot_json JSON NULL,
    published_by_cas_id VARCHAR(20) NOT NULL,
    published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_form_version_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_form_version UNIQUE (form_id, version_no),
    CONSTRAINT fk_form_version_form FOREIGN KEY (form_id)
        REFERENCES application_form(id),
    CONSTRAINT fk_form_version_publisher FOREIGN KEY (published_by_cas_id)
        REFERENCES `user`(cas_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 6. 申请、文件与异步任务

### 6.1 application 和 application_revision

```sql
CREATE TABLE application (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    form_version_id CHAR(26) NOT NULL,
    applicant_cas_id VARCHAR(20) NOT NULL,
    answers_json JSON NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    submitted_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_application_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_application_form_version FOREIGN KEY (form_version_id)
        REFERENCES form_version(id),
    CONSTRAINT fk_application_applicant FOREIGN KEY (applicant_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_application_status CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'PROCESSING', 'RETURNED', 'PARTIALLY_APPROVED',
         'APPROVED', 'REJECTED', 'COMPLETED')),
    INDEX idx_application_form_user
        (organization_id, form_version_id, applicant_cas_id),
    INDEX idx_application_user_status
        (organization_id, applicant_cas_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_revision (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    application_id CHAR(26) NOT NULL,
    revision_no INT UNSIGNED NOT NULL,
    answers_json JSON NOT NULL,
    changed_fields_json JSON NULL,
    change_reason VARCHAR(500) NULL,
    actor_cas_id VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_application_revision_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_application_revision UNIQUE (application_id, revision_no),
    CONSTRAINT fk_application_revision_application FOREIGN KEY (application_id)
        REFERENCES application(id),
    CONSTRAINT fk_application_revision_actor FOREIGN KEY (actor_cas_id)
        REFERENCES `user`(cas_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

每人最大提交次数取决于表单配置，无法用固定唯一索引表达，由服务端在创建申请时校验。

### 6.2 file_object 文件对象表

```sql
CREATE TABLE file_object (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    uploader_cas_id VARCHAR(20) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    image_fingerprint VARCHAR(255) NULL,
    purpose VARCHAR(40) NOT NULL,
    scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    preview_file_id CHAR(26) NULL,
    ready_at DATETIME(3) NULL,
    expires_at DATETIME(3) NULL COMMENT '孤立上传保留期',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_file_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_file_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_file_uploader FOREIGN KEY (uploader_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_file_preview FOREIGN KEY (preview_file_id)
        REFERENCES file_object(id),
    CONSTRAINT ck_file_scan_status
        CHECK (scan_status IN ('PENDING', 'SCANNING', 'READY', 'REJECTED', 'FAILED')),
    INDEX idx_file_hash (organization_id, sha256),
    INDEX idx_file_cleanup (scan_status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.3 async_job 异步任务表

OCR、预览生成、导出生成等共用此表。

```sql
CREATE TABLE async_job (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    job_type VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 3,
    request_json JSON NULL,
    result_json JSON NULL,
    error_code VARCHAR(80) NULL,
    error_message VARCHAR(1000) NULL,
    created_by_cas_id VARCHAR(20) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    CONSTRAINT fk_job_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_job_creator FOREIGN KEY (created_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_job_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_job_progress CHECK (progress <= 100),
    INDEX idx_job_dispatch (status, job_type, created_at),
    INDEX idx_job_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 7. 发票、附件与预检

### 7.1 invoice 发票表

```sql
CREATE TABLE invoice (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    application_id CHAR(26) NOT NULL,
    invoice_type VARCHAR(40) NOT NULL,
    invoice_code VARCHAR(50) NULL,
    invoice_number VARCHAR(100) NULL,
    digital_invoice_no VARCHAR(100) NULL,
    invoice_date DATE NULL,
    buyer_name VARCHAR(200) NULL,
    buyer_tax_no VARCHAR(50) NULL,
    seller_name VARCHAR(200) NULL,
    seller_tax_no VARCHAR(50) NULL,
    face_amount DECIMAL(12,2) NOT NULL,
    claimed_amount DECIMAL(12,2) NOT NULL,
    current_file_id CHAR(26) NOT NULL,
    expense_category_item_id CHAR(26) NULL,
    internal_note TEXT NULL,
    field_sources_json JSON NULL COMMENT '字段来源与人工确认摘要',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    void_reason VARCHAR(500) NULL,
    voided_by_cas_id VARCHAR(20) NULL,
    voided_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_invoice_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_invoice_application FOREIGN KEY (application_id)
        REFERENCES application(id),
    CONSTRAINT fk_invoice_file FOREIGN KEY (current_file_id)
        REFERENCES file_object(id),
    CONSTRAINT fk_invoice_category FOREIGN KEY (expense_category_item_id)
        REFERENCES dictionary_item(id),
    CONSTRAINT fk_invoice_void_actor FOREIGN KEY (voided_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_invoice_amount
        CHECK (face_amount >= 0 AND claimed_amount > 0),
    CONSTRAINT ck_invoice_status CHECK (status IN
        ('DRAFT', 'PENDING_RECOGNITION', 'SUBMITTED', 'IN_REVIEW', 'RETURNED',
         'INTERNALLY_APPROVED', 'REJECTED', 'IN_EXPORT_BATCH', 'VOIDED', 'ARCHIVED')),
    INDEX idx_invoice_application (organization_id, application_id, status),
    INDEX idx_invoice_identity
        (organization_id, invoice_code, invoice_number),
    INDEX idx_invoice_digital_no (organization_id, digital_invoice_no),
    INDEX idx_invoice_date_amount
        (organization_id, invoice_date, claimed_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`claimed_amount <= face_amount` 可根据项目规则设置为阻断或警告，因此不设为固定 `CHECK`。

### 7.2 invoice_file_revision 和 attachment

```sql
CREATE TABLE invoice_file_revision (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    invoice_id CHAR(26) NOT NULL,
    file_id CHAR(26) NOT NULL,
    revision_no INT UNSIGNED NOT NULL,
    replacement_reason VARCHAR(500) NULL,
    replaced_by_cas_id VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_invoice_file_revision_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_invoice_file_revision UNIQUE (invoice_id, revision_no),
    CONSTRAINT fk_invoice_file_revision_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_invoice_file_revision_file FOREIGN KEY (file_id)
        REFERENCES file_object(id),
    CONSTRAINT fk_invoice_file_revision_actor FOREIGN KEY (replaced_by_cas_id)
        REFERENCES `user`(cas_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attachment (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    invoice_id CHAR(26) NOT NULL,
    attachment_type VARCHAR(40) NOT NULL,
    file_id CHAR(26) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    void_reason VARCHAR(500) NULL,
    voided_by_cas_id VARCHAR(20) NULL,
    voided_at DATETIME(3) NULL,
    created_by_cas_id VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_attachment_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_attachment_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_attachment_file FOREIGN KEY (file_id)
        REFERENCES file_object(id),
    CONSTRAINT fk_attachment_creator FOREIGN KEY (created_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_attachment_void_actor FOREIGN KEY (voided_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_attachment_status CHECK (status IN ('ACTIVE', 'VOIDED')),
    INDEX idx_attachment_invoice
        (organization_id, invoice_id, attachment_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 7.3 recognition_suggestion 识别建议

```sql
CREATE TABLE recognition_suggestion (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    invoice_id CHAR(26) NOT NULL,
    source_job_id CHAR(26) NOT NULL,
    field_path VARCHAR(200) NOT NULL,
    suggested_value TEXT NULL,
    confidence DECIMAL(5,4) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    final_value TEXT NULL,
    confirmed_by_cas_id VARCHAR(20) NULL,
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_recognition_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_recognition_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_recognition_job FOREIGN KEY (source_job_id)
        REFERENCES async_job(id),
    CONSTRAINT fk_recognition_confirmer FOREIGN KEY (confirmed_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_recognition_confidence
        CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT ck_recognition_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'CORRECTED', 'REJECTED')),
    INDEX idx_recognition_invoice (organization_id, invoice_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 7.4 scan_event 扫码事件

```sql
CREATE TABLE scan_event (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    invoice_id CHAR(26) NULL COMMENT '未匹配成功时可为空',
    project_id CHAR(26) NOT NULL,
    actor_cas_id VARCHAR(20) NOT NULL,
    context VARCHAR(30) NOT NULL,
    raw_hash CHAR(64) NULL COMMENT '二维码原文哈希',
    parsed_identity_json JSON NULL,
    result VARCHAR(30) NOT NULL,
    result_detail VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_scan_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_scan_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_scan_project FOREIGN KEY (project_id)
        REFERENCES project(id),
    CONSTRAINT fk_scan_actor FOREIGN KEY (actor_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_scan_context
        CHECK (context IN ('SUBMISSION', 'PAPER_RECEIPT', 'SEARCH')),
    CONSTRAINT ck_scan_result CHECK (result IN
        ('SUCCESS', 'ALREADY_SCANNED', 'OTHER_PROJECT', 'POSSIBLE_DUPLICATE',
         'DATA_MISMATCH', 'UNSUPPORTED', 'NOT_FOUND')),
    INDEX idx_scan_project_time (organization_id, project_id, created_at),
    INDEX idx_scan_invoice_time (organization_id, invoice_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 7.5 invoice_precheck_result 规则与查重结果

每次预检均追加记录，规则更新不覆盖历史结果。

```sql
CREATE TABLE invoice_precheck_result (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    invoice_id CHAR(26) NOT NULL,
    rule_set_version_id CHAR(26) NULL,
    check_type VARCHAR(30) NOT NULL,
    rule_code VARCHAR(100) NULL,
    severity VARCHAR(20) NOT NULL,
    result VARCHAR(20) NOT NULL,
    reason VARCHAR(1000) NULL,
    matched_invoice_id CHAR(26) NULL COMMENT '仅服务端可见，跨社团不向客户端返回',
    evidence_json JSON NULL,
    resolution VARCHAR(20) NULL,
    resolved_by_cas_id VARCHAR(20) NULL,
    resolution_comment VARCHAR(500) NULL,
    resolved_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_precheck_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_precheck_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_precheck_rule_version FOREIGN KEY (rule_set_version_id)
        REFERENCES rule_set_version(id),
    CONSTRAINT fk_precheck_matched_invoice FOREIGN KEY (matched_invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_precheck_resolver FOREIGN KEY (resolved_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_precheck_severity
        CHECK (severity IN ('BLOCK', 'WARNING', 'INFO')),
    CONSTRAINT ck_precheck_result
        CHECK (result IN ('PASS', 'HIT', 'ERROR')),
    CONSTRAINT ck_precheck_resolution
        CHECK (resolution IS NULL OR resolution IN ('CONFIRMED', 'FALSE_POSITIVE', 'ACCEPTED_RISK')),
    INDEX idx_precheck_invoice (organization_id, invoice_id, created_at),
    INDEX idx_precheck_match (organization_id, matched_invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 8. 社团整理、审核与纸票

### 8.1 club_review 审核记录

审核记录只追加、不覆盖。`START_REVIEW` 也是一条审核记录。

```sql
CREATE TABLE club_review (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    invoice_id CHAR(26) NOT NULL,
    reviewer_cas_id VARCHAR(20) NOT NULL,
    action VARCHAR(30) NOT NULL,
    reason_item_id CHAR(26) NULL,
    return_fields_json JSON NULL,
    comment VARCHAR(1000) NULL,
    batch_operation_id CHAR(26) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_review_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_review_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_review_reason FOREIGN KEY (reason_item_id)
        REFERENCES dictionary_item(id),
    CONSTRAINT ck_review_action CHECK (action IN
        ('START_REVIEW', 'APPROVE', 'RETURN', 'REJECT', 'REOPEN', 'VOID')),
    INDEX idx_review_invoice_time (organization_id, invoice_id, created_at),
    INDEX idx_review_reviewer_time
        (organization_id, reviewer_cas_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`RETURN` 必须有退回原因且 `return_fields_json` 至少包含一个稳定字段路径，由服务端校验。

### 8.2 invoice_tag 发票标签关联表

```sql
CREATE TABLE invoice_tag (
    invoice_id CHAR(26) NOT NULL,
    organization_id CHAR(26) NOT NULL,
    tag_item_id CHAR(26) NOT NULL,
    added_by_cas_id VARCHAR(20) NOT NULL,
    added_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (invoice_id, tag_item_id),
    CONSTRAINT fk_invoice_tag_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_invoice_tag_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_invoice_tag_item FOREIGN KEY (tag_item_id)
        REFERENCES dictionary_item(id),
    CONSTRAINT fk_invoice_tag_actor FOREIGN KEY (added_by_cas_id)
        REFERENCES `user`(cas_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 8.3 paper_item 和 paper_event

`paper_item` 保存当前状态，`paper_event` 保存完整状态历史。

```sql
CREATE TABLE paper_item (
    invoice_id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_DELIVERY',
    member_declared_at DATETIME(3) NULL,
    received_by_cas_id VARCHAR(20) NULL,
    received_at DATETIME(3) NULL,
    note VARCHAR(500) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_paper_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_paper_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_paper_receiver FOREIGN KEY (received_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_paper_status CHECK (status IN
        ('PENDING_DELIVERY', 'MEMBER_DECLARED', 'CLUB_RECEIVED', 'RETURNED_TO_MEMBER',
         'TRANSFERRED_EXTERNAL', 'ARCHIVED', 'EXCEPTION')),
    INDEX idx_paper_org_status (organization_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE paper_event (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    invoice_id CHAR(26) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NULL,
    actor_cas_id VARCHAR(20) NOT NULL,
    scan_event_id CHAR(26) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_paper_event_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_paper_event_item FOREIGN KEY (invoice_id)
        REFERENCES paper_item(invoice_id),
    CONSTRAINT fk_paper_event_actor FOREIGN KEY (actor_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_paper_event_scan FOREIGN KEY (scan_event_id)
        REFERENCES scan_event(id),
    INDEX idx_paper_event_invoice_time
        (organization_id, invoice_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 9. 导出批次与平台外状态

### 9.1 export_batch 导出批次

```sql
CREATE TABLE export_batch (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    project_id CHAR(26) NOT NULL,
    batch_no VARCHAR(50) NOT NULL,
    revision_no INT UNSIGNED NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    invoice_count INT UNSIGNED NOT NULL DEFAULT 0,
    total_face_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_claimed_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    snapshot_json JSON NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_by_cas_id VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    generated_at DATETIME(3) NULL,
    first_downloaded_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    archived_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    CONSTRAINT fk_export_batch_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_export_batch_revision
        UNIQUE (project_id, batch_no, revision_no),
    CONSTRAINT fk_export_batch_project FOREIGN KEY (project_id)
        REFERENCES project(id),
    CONSTRAINT fk_export_batch_creator FOREIGN KEY (created_by_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_export_batch_status CHECK (status IN
        ('DRAFT', 'GENERATED', 'EXPORTED', 'EXTERNAL_PROCESSING',
         'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    INDEX idx_export_batch_project_status
        (organization_id, project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 9.2 export_batch_invoice 与 invoice_export_reservation

`export_batch_invoice` 保留不可变快照；`invoice_export_reservation` 以发票主键作唯一键，保证同一发票不能同时进入两个未结束批次。取消批次时删除预留记录，完成或归档时保留。

```sql
CREATE TABLE export_batch_invoice (
    batch_id CHAR(26) NOT NULL,
    organization_id CHAR(26) NOT NULL,
    invoice_id CHAR(26) NOT NULL,
    sequence_no INT UNSIGNED NOT NULL,
    snapshot_face_amount DECIMAL(12,2) NOT NULL,
    snapshot_claimed_amount DECIMAL(12,2) NOT NULL,
    snapshot_json JSON NOT NULL,
    PRIMARY KEY (batch_id, invoice_id),
    CONSTRAINT fk_export_item_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_export_batch_sequence UNIQUE (batch_id, sequence_no),
    CONSTRAINT fk_export_item_batch FOREIGN KEY (batch_id)
        REFERENCES export_batch(id),
    CONSTRAINT fk_export_item_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invoice_export_reservation (
    invoice_id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    batch_id CHAR(26) NOT NULL,
    reserved_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_export_reservation_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_export_reservation_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_export_reservation_batch FOREIGN KEY (batch_id)
        REFERENCES export_batch(id),
    INDEX idx_export_reservation_batch (organization_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

选择发票、写入快照和建立预留必须在同一事务内完成。同项目校验和 1000 张上限由服务端强制执行。

### 9.3 export_artifact 导出产物

```sql
CREATE TABLE export_artifact (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    batch_id CHAR(26) NOT NULL,
    job_id CHAR(26) NOT NULL,
    artifact_type VARCHAR(30) NOT NULL,
    file_id CHAR(26) NOT NULL,
    relative_path VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_export_artifact_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_export_artifact_path UNIQUE (batch_id, relative_path),
    CONSTRAINT fk_export_artifact_batch FOREIGN KEY (batch_id)
        REFERENCES export_batch(id),
    CONSTRAINT fk_export_artifact_job FOREIGN KEY (job_id)
        REFERENCES async_job(id),
    CONSTRAINT fk_export_artifact_file FOREIGN KEY (file_id)
        REFERENCES file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 9.4 external_status_event 及附件

平台外状态仅追加事件。更正事件通过 `correction_of_event_id` 引用被更正记录。

```sql
CREATE TABLE external_status_event (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    batch_id CHAR(26) NOT NULL,
    actor_cas_id VARCHAR(20) NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    correction_of_event_id CHAR(26) NULL,
    comment VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_external_event_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_external_event_batch FOREIGN KEY (batch_id)
        REFERENCES export_batch(id),
    CONSTRAINT fk_external_event_actor FOREIGN KEY (actor_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_external_event_correction FOREIGN KEY (correction_of_event_id)
        REFERENCES external_status_event(id),
    CONSTRAINT ck_external_event_type
        CHECK (event_type IN ('STATUS_CHANGE', 'COMMENT', 'CORRECTION')),
    CONSTRAINT ck_external_status CHECK (status IN
        ('PENDING_EXTERNAL', 'SUBMITTED_EXTERNAL', 'RETURNED_EXTERNAL',
         'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    INDEX idx_external_event_batch_time
        (organization_id, batch_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE external_status_event_attachment (
    event_id CHAR(26) NOT NULL,
    organization_id CHAR(26) NOT NULL,
    file_id CHAR(26) NOT NULL,
    PRIMARY KEY (event_id, file_id),
    CONSTRAINT fk_external_attachment_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_external_attachment_event FOREIGN KEY (event_id)
        REFERENCES external_status_event(id),
    CONSTRAINT fk_external_attachment_file FOREIGN KEY (file_id)
        REFERENCES file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 10. 通知、筛选、交接与审计

### 10.1 notification 通知表

```sql
CREATE TABLE notification (
    id CHAR(26) PRIMARY KEY,
    recipient_cas_id VARCHAR(20) NOT NULL,
    organization_id CHAR(26) NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    deduplication_key VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_notification_dedup
        UNIQUE (recipient_cas_id, deduplication_key),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_notification_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT ck_notification_status
        CHECK (status IN ('UNREAD', 'READ', 'DISMISSED')),
    INDEX idx_notification_inbox
        (organization_id, recipient_cas_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`target_type + target_id` 仅保存受限资源定位，点击通知时必须重新鉴权，不保存任意 URL。

### 10.2 saved_invoice_filter 常用发票筛选

```sql
CREATE TABLE saved_invoice_filter (
    id CHAR(26) PRIMARY KEY,
    owner_cas_id VARCHAR(20) NOT NULL,
    organization_id CHAR(26) NOT NULL,
    name VARCHAR(100) NOT NULL,
    filter_json JSON NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_saved_filter_name
        UNIQUE (organization_id, owner_cas_id, name),
    CONSTRAINT fk_saved_filter_owner FOREIGN KEY (owner_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_saved_filter_org FOREIGN KEY (organization_id)
        REFERENCES organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 10.3 handover_record 换届交接记录

```sql
CREATE TABLE handover_record (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    outgoing_member_id CHAR(26) NOT NULL,
    incoming_member_id CHAR(26) NOT NULL,
    performed_by_cas_id VARCHAR(20) NOT NULL,
    snapshot_json JSON NOT NULL,
    comment VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_handover_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_handover_outgoing FOREIGN KEY (outgoing_member_id)
        REFERENCES organization_member(id),
    CONSTRAINT fk_handover_incoming FOREIGN KEY (incoming_member_id)
        REFERENCES organization_member(id),
    CONSTRAINT fk_handover_actor FOREIGN KEY (performed_by_cas_id)
        REFERENCES `user`(cas_id),
    INDEX idx_handover_org_time (organization_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

转移项目负责人、待处理授权、结束旧成员关系和写入交接记录必须在同一事务内完成。

### 10.4 idempotency_record 幂等记录

```sql
CREATE TABLE idempotency_record (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NOT NULL,
    cas_id VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INT NULL,
    response_body MEDIUMTEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_idempotency_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT uk_idempotency_user_key
        UNIQUE (organization_id, cas_id, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT ck_idempotency_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    INDEX idx_idempotency_cleanup (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 10.5 audit_log 审计日志

```sql
CREATE TABLE audit_log (
    id CHAR(26) PRIMARY KEY,
    organization_id CHAR(26) NULL,
    actor_cas_id VARCHAR(20) NULL COMMENT '系统任务可为空',
    action VARCHAR(100) NOT NULL,
    object_type VARCHAR(50) NOT NULL,
    object_id VARCHAR(64) NOT NULL,
    project_id CHAR(26) NULL,
    invoice_id CHAR(26) NULL,
    batch_id CHAR(26) NULL,
    before_digest CHAR(64) NULL,
    after_digest CHAR(64) NULL,
    change_summary_json JSON NULL,
    request_id VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_audit_org FOREIGN KEY (organization_id)
        REFERENCES organization(id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_cas_id)
        REFERENCES `user`(cas_id),
    CONSTRAINT fk_audit_project FOREIGN KEY (project_id)
        REFERENCES project(id),
    CONSTRAINT fk_audit_invoice FOREIGN KEY (invoice_id)
        REFERENCES invoice(id),
    CONSTRAINT fk_audit_batch FOREIGN KEY (batch_id)
        REFERENCES export_batch(id),
    INDEX idx_audit_org_time (organization_id, created_at),
    INDEX idx_audit_actor_time (actor_cas_id, created_at),
    INDEX idx_audit_object (object_type, object_id, created_at),
    INDEX idx_audit_project_time (project_id, created_at),
    INDEX idx_audit_invoice_time (invoice_id, created_at),
    INDEX idx_audit_batch_time (batch_id, created_at),
    INDEX idx_audit_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`audit_log` 只允许追加，业务账号不提供修改或删除接口。摘要中禁止保存密码、令牌、二维码原文和完整文件内容。

## 11. 关键事务与约束

MySQL 外键和 `CHECK` 无法表达的规则，必须在服务层事务中强制执行：

1. 社团、项目、表单、申请、发票、文件和导出批次必须处于同一组织数据边界。
2. 项目负责人和项目授权成员必须是项目所属社团的有效成员。
3. 表单版本、规则版本、申请修订、审核记录和各类状态事件只追加不覆盖。
4. 文件只有在 `scan_status = 'READY'` 后才能被发票、附件或导出产物引用。
5. 申请状态从所属发票状态派生，不允许客户端直接伪造。
6. 审核人不得修改社员原始答案；退回时必须有原因和至少一个可修改字段路径。
7. 启用纸票的项目中，发票内部通过时才创建 `paper_item`。纸票写操作只使用 `paper_item.version` 作乐观锁。
8. 导出批次只能选择同项目的内部通过发票，最多 1000 张；快照、金额和预留记录在同一事务中写入。
9. 取消导出批次删除 `invoice_export_reservation`，完成或归档不删除。
10. 更正已归档的平台外状态时，必须追加 `CORRECTION` 事件并引用原事件。
11. 跨社团查重只返回风险结论，不返回 `matched_invoice_id` 或另一社团的任何信息。
12. 所有改变服务端状态的操作使用幂等键，并在业务事务内写入审计日志。

## 12. 删除策略

- 字典、规则、表单草稿在未发布且未被引用时可硬删除。
- 申请草稿和发票草稿在未提交时可硬删除。
- 正式申请、发票、附件、审核、纸票、批次、平台外状态和审计记录不硬删除。
- 孤立文件可在 `expires_at` 到期后由定时任务清理，被任何业务表引用的文件不得清理。
- 通知、幂等记录和已完成异步任务可按配置的数据保留周期归档或清理。
