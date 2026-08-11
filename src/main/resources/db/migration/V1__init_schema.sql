-- Baseline schema. Once applied to a shared environment this file is immutable.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

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
