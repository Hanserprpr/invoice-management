-- Stable global RBAC reference data. Never use generated numeric ids in relationships.
INSERT INTO role (code, name) VALUES
    ('MEMBER', '社员'),
    ('PROJECT_MANAGER', '项目负责人'),
    ('REVIEWER', '社团审核人'),
    ('CLUB_ADMIN', '社团管理员'),
    ('AUDITOR', '审计只读用户')
ON DUPLICATE KEY UPDATE name = role.name;

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
    ('audit-log:read', '查看审计日志')
ON DUPLICATE KEY UPDATE name = permission.name;

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r
JOIN permission p ON
       r.code = 'CLUB_ADMIN'
    OR (r.code = 'MEMBER' AND p.code IN
        ('project:read', 'application:create', 'application:update-own-answer', 'ticket:confirm-own'))
    OR (r.code = 'PROJECT_MANAGER' AND p.code IN
        ('project:read', 'project:create', 'form:create', 'form:publish',
         'application:create', 'application:add-internal-field'))
    OR (r.code = 'REVIEWER' AND p.code IN
        ('project:read', 'application:review', 'ticket:mark-received', 'export:create'))
    OR (r.code = 'AUDITOR' AND p.code IN ('project:read', 'audit-log:read'));

-- Platform dictionary templates are cloned into an organization during creation.
INSERT INTO dictionary_version
    (id, organization_id, dictionary_type, version_no, status, published_at)
VALUES
    ('01K00000000000000000000001', NULL, 'EXPENSE_CATEGORY', 1, 'PUBLISHED', CURRENT_TIMESTAMP(3)),
    ('01K00000000000000000000002', NULL, 'RETURN_REASON', 1, 'PUBLISHED', CURRENT_TIMESTAMP(3));

INSERT INTO dictionary_item
    (id, organization_id, dictionary_version_id, code, display_name, sort_order, enabled)
VALUES
    ('01K00000000000000000000101', NULL, '01K00000000000000000000001', 'MATERIAL', '物资', 10, TRUE),
    ('01K00000000000000000000102', NULL, '01K00000000000000000000001', 'TRANSPORT', '交通', 20, TRUE),
    ('01K00000000000000000000103', NULL, '01K00000000000000000000001', 'PUBLICITY', '宣传制作', 30, TRUE),
    ('01K00000000000000000000104', NULL, '01K00000000000000000000001', 'VENUE', '场地', 40, TRUE),
    ('01K00000000000000000000105', NULL, '01K00000000000000000000001', 'CATERING', '餐饮', 50, TRUE),
    ('01K00000000000000000000106', NULL, '01K00000000000000000000001', 'OTHER', '其他', 60, TRUE),
    ('01K00000000000000000000201', NULL, '01K00000000000000000000002', 'TITLE_ERROR', '抬头错误', 10, TRUE),
    ('01K00000000000000000000202', NULL, '01K00000000000000000000002', 'TAX_ID_ERROR', '税号错误', 20, TRUE),
    ('01K00000000000000000000203', NULL, '01K00000000000000000000002', 'AMOUNT_MISMATCH', '金额不一致', 30, TRUE),
    ('01K00000000000000000000204', NULL, '01K00000000000000000000002', 'MISSING_PAYMENT', '缺少支付记录', 40, TRUE),
    ('01K00000000000000000000205', NULL, '01K00000000000000000000002', 'MISSING_ORDER', '缺少订单明细', 50, TRUE),
    ('01K00000000000000000000206', NULL, '01K00000000000000000000002', 'SUSPECTED_DUPLICATE', '疑似重复', 60, TRUE),
    ('01K00000000000000000000207', NULL, '01K00000000000000000000002', 'UNCLEAR_INVOICE', '发票不清晰', 70, TRUE),
    ('01K00000000000000000000208', NULL, '01K00000000000000000000002', 'OUT_OF_PERIOD', '超出时间范围', 80, TRUE),
    ('01K00000000000000000000209', NULL, '01K00000000000000000000002', 'OTHER', '其他', 90, TRUE);
