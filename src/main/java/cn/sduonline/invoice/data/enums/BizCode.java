package cn.sduonline.invoice.data.enums;

import lombok.Getter;

/**
 * 业务状态码枚举。
 * <p>
 * 除成功为 0 外，其余状态码统一为五位数字。命名空间约定：
 * 1xxxx 通用平台、2xxxx 认证鉴权、3xxxx 用户与社团成员、
 * 4xxxx 项目/表单/申请、5xxxx 发票/扫码/查重/规则、
 * 6xxxx 审核/纸票/台账、7xxxx 导出批次/平台外状态/通知/交接、
 * 8xxxx 外部依赖、9xxxx 系统。
 */
@Getter
public enum BizCode {

    /** 成功。 */
    SUCCESS(0, "请求成功"),

    // ---------- 1xxxx 通用平台 ----------
    /** 参数非法。 */
    PARAM_INVALID(10000, "参数范围或格式错误"),
    /** 当前状态或时间不允许该操作。 */
    STATE_NOT_ALLOWED(10001, "当前条件或时间不允许〒▽〒"),
    /** 不支持的操作。 */
    NOT_SUPPORTED(10002, "方法不允许"),
    /** 请求的接口或资源不存在。 */
    RESOURCE_NOT_FOUND(10003, "你要找的东西好像走丢啦X﹏X"),
    /** 请求频率过高。 */
    TOO_MANY_REQUESTS(10004, "请求繁忙，请稍后再试"),
    /** 重复提交。 */
    DUPLICATE_SUBMIT(10005, "重复提交"),
    /** 幂等键冲突。 */
    IDEMPOTENCY_CONFLICT(10006, "幂等键冲突，请勿重复提交"),
    /** 乐观锁版本冲突。 */
    VERSION_CONFLICT(10007, "数据已被修改，请刷新后重试"),
    /** 操作失败。 */
    OP_FAILED(10008, "操作失败"),

    // ---------- 2xxxx 认证与鉴权 ----------
    /** 用户未登录。 */
    NOT_LOGIN(20000, "用户未登录"),
    /** Token 无效。 */
    TOKEN_INVALID(20001, "登录状态已失效"),
    /** 密码错误。 */
    PASSWORD_ERROR(20002, "学号或统一身份认证密码错误"),
    /** 权限不足。 */
    NO_PERMISSION(20003, "无权限访问"),
    /** 跨社团访问被拒绝。 */
    CROSS_CLUB_FORBIDDEN(20004, "无权访问其他社团数据"),
    /** 密码设置令牌无效或已过期。 */
    PASSWORD_SETUP_TOKEN_INVALID(20005, "密码设置链接无效或已过期"),

    // ---------- 3xxxx 用户与社团成员 ----------
    /** 用户不存在。 */
    USER_NOT_FOUND(30000, "用户不存在"),
    /** 用户被禁用或已离任。 */
    USER_DISABLED(30001, "用户已被禁用"),
    /** 账号已存在。 */
    USERNAME_EXISTS(30002, "账号已存在"),
    /** 社团不存在。 */
    CLUB_NOT_FOUND(31000, "社团不存在"),
    /** 社团已停用，禁止新增项目和申请。 */
    CLUB_DISABLED(31001, "社团已停用，禁止新增项目和申请"),
    /** 社团名称或标识发生冲突。 */
    CLUB_ALREADY_EXISTS(31002, "社团已存在"),
    /** 成员关系不存在。 */
    MEMBERSHIP_NOT_FOUND(32000, "成员关系不存在"),
    /** 无权授予该角色或范围。 */
    ROLE_ASSIGNMENT_FORBIDDEN(32001, "无权授予该角色或范围"),
    /** 成员批量导入失败。 */
    MEMBER_IMPORT_FAILED(32002, "成员批量导入失败"),
    /** 成员关系已存在。 */
    MEMBERSHIP_ALREADY_EXISTS(32003, "成员关系已存在"),
    /** 成员任期范围非法。 */
    MEMBER_TERM_INVALID(32004, "成员任期范围非法"),
    /** 角色编码不存在。 */
    ROLE_NOT_FOUND(32005, "角色不存在"),
    /** 项目授权范围非法。 */
    PROJECT_ACCESS_INVALID(32006, "项目授权范围非法"),

    // ---------- 4xxxx 项目、表单与申请 ----------
    /** 项目不存在。 */
    PROJECT_NOT_FOUND(40000, "项目不存在"),
    /** 项目当前状态不允许该操作。 */
    PROJECT_STATE_NOT_ALLOWED(40001, "项目当前状态不允许该操作"),
    /** 项目已归档，禁止新增提交。 */
    PROJECT_ARCHIVED(40002, "项目已归档，禁止新增提交"),
    /** 申请表不存在。 */
    FORM_NOT_FOUND(41000, "申请表不存在"),
    /** 表单版本不存在。 */
    FORM_VERSION_NOT_FOUND(41001, "表单版本不存在"),
    /** 已有申请的表单版本不可修改或删除。 */
    FORM_VERSION_IMMUTABLE(41002, "已有申请的表单版本不可修改或删除"),
    /** 申请表尚未发布。 */
    FORM_NOT_PUBLISHED(41003, "申请表尚未发布"),
    /** 申请表当前状态不允许该操作。 */
    FORM_STATE_NOT_ALLOWED(41004, "申请表当前状态不允许该操作"),
    /** 申请记录不存在。 */
    APPLICATION_NOT_FOUND(42000, "申请记录不存在"),
    /** 申请当前状态不允许该操作。 */
    APPLICATION_STATE_NOT_ALLOWED(42001, "申请当前状态不允许该操作"),
    /** 不在申请提交时间范围内。 */
    SUBMISSION_WINDOW_CLOSED(42002, "不在申请提交时间范围内"),
    /** 已达到每人提交次数上限。 */
    SUBMISSION_LIMIT_REACHED(42003, "已达到每人提交次数上限"),

    // ---------- 5xxxx 发票、扫码、查重与规则 ----------
    /** 发票不存在。 */
    INVOICE_NOT_FOUND(50000, "发票不存在"),
    /** 发票当前状态不允许该操作。 */
    INVOICE_STATE_NOT_ALLOWED(50001, "发票当前状态不允许该操作"),
    /** 申请金额非法。 */
    INVOICE_AMOUNT_INVALID(50002, "申请金额须大于 0 且不高于票面金额"),
    /** 作废发票必须填写原因。 */
    INVOICE_VOID_REASON_REQUIRED(50003, "作废发票必须填写原因"),
    /** 发票二维码无法识别。 */
    SCAN_PARSE_FAILED(51000, "发票二维码无法识别"),
    /** 扫描到非当前项目的发票。 */
    SCAN_NOT_CURRENT_PROJECT(51001, "非当前项目发票"),
    /** 扫码数据与线上记录不一致。 */
    SCAN_DATA_MISMATCH(51002, "扫码数据与线上记录不一致"),
    /** 不支持的二维码格式。 */
    SCAN_FORMAT_UNSUPPORTED(51003, "不支持的二维码格式"),
    /** 发票精确重复，禁止内部通过。 */
    INVOICE_DUPLICATE(52000, "发票重复，禁止内部通过"),
    /** 疑似重复发票。 */
    INVOICE_SUSPECTED_DUPLICATE(52001, "疑似重复发票，请人工确认"),
    /** 规则集不存在或未发布。 */
    RULE_SET_NOT_FOUND(53000, "规则集不存在或未发布"),
    /** 未通过规则校验（阻断）。 */
    RULE_BLOCKED(53001, "未通过规则校验"),
    /** 字典项不存在。 */
    DICTIONARY_ITEM_NOT_FOUND(53002, "字典项不存在"),

    // ---------- 6xxxx 审核、纸票与台账 ----------
    /** 需先开始审核。 */
    REVIEW_NOT_STARTED(60000, "请先开始审核"),
    /** 该发票已有审核结论。 */
    REVIEW_ALREADY_CONCLUDED(60001, "该发票已有审核结论"),
    /** 退回或拒绝必须填写原因。 */
    REVIEW_REASON_REQUIRED(60002, "退回或拒绝必须填写原因"),
    /** 退回必须至少指定一个可修改字段。 */
    REVIEW_RETURN_FIELD_REQUIRED(60003, "退回必须至少指定一个可修改字段"),
    /** 不能修改社员原始答案。 */
    REVIEW_ANSWER_IMMUTABLE(60004, "不能修改社员原始答案"),
    /** 超出批量操作数量上限。 */
    BATCH_SIZE_EXCEEDED(60005, "超出批量操作数量上限"),
    /** 项目未启用纸票。 */
    PAPER_NOT_ENABLED(61000, "项目未启用纸票"),
    /** 纸票记录不存在。 */
    PAPER_ITEM_NOT_FOUND(61001, "纸票记录不存在"),
    /** 纸票当前状态不允许该操作。 */
    PAPER_STATE_NOT_ALLOWED(61002, "纸票当前状态不允许该操作"),
    /** 人工修改纸票状态必须填写原因。 */
    PAPER_REASON_REQUIRED(61003, "人工修改纸票状态必须填写原因"),
    /** 保存的筛选不存在。 */
    SAVED_FILTER_NOT_FOUND(62000, "保存的筛选不存在"),

    // ---------- 7xxxx 导出批次、平台外状态、通知与交接 ----------
    /** 导出批次不存在。 */
    EXPORT_BATCH_NOT_FOUND(70000, "导出批次不存在"),
    /** 导出批次当前状态不允许该操作。 */
    EXPORT_BATCH_STATE_NOT_ALLOWED(70001, "导出批次当前状态不允许该操作"),
    /** 只能选择社团内部通过的发票。 */
    EXPORT_INVOICE_NOT_AVAILABLE(70002, "只能选择社团内部通过的发票"),
    /** 不同项目发票不能进入同一批次。 */
    EXPORT_INVOICE_CROSS_PROJECT(70003, "不同项目发票不能进入同一批次"),
    /** 发票已在其他未结束批次中。 */
    EXPORT_INVOICE_RESERVED(70004, "发票已在其他未结束批次中"),
    /** 导出选择数量超出上限。 */
    EXPORT_SELECTION_LIMIT_EXCEEDED(70005, "导出选择数量超出上限"),
    /** 导出产物尚未生成。 */
    EXPORT_ARTIFACT_NOT_READY(70006, "导出产物尚未生成"),
    /** 平台外状态记录不存在。 */
    EXTERNAL_STATUS_NOT_FOUND(71000, "平台外状态记录不存在"),
    /** 更正事件必须引用被更正事件并填写原因。 */
    EXTERNAL_STATUS_CORRECTION_INVALID(71001, "更正事件必须引用被更正事件并填写原因"),
    /** 通知不存在。 */
    NOTIFICATION_NOT_FOUND(72000, "通知不存在"),
    /** 换届交接状态不允许该操作。 */
    HANDOVER_STATE_INVALID(73000, "换届交接状态不允许该操作"),
    /** 审计日志不可修改或删除。 */
    AUDIT_LOG_IMMUTABLE(73001, "审计日志不可修改或删除"),

    // ---------- 8xxxx 外部依赖 ----------
    /** 文件尚未就绪。 */
    FILE_NOT_READY(80000, "文件尚未就绪"),
    /** 不支持的文件类型。 */
    FILE_TYPE_NOT_ALLOWED(80001, "不支持的文件类型"),
    /** 文件大小超出限制。 */
    FILE_TOO_LARGE(80002, "文件大小超出限制"),
    /** OCR 服务不可用。 */
    OCR_UNAVAILABLE(80003, "OCR 服务暂不可用，请人工录入"),
    /** 第三方服务不可用。 */
    THIRD_PARTY_UNAVAILABLE(80004, "第三方服务不可用"),
    /** 第三方接口超时。 */
    THIRD_PARTY_TIMEOUT(80005, "第三方接口超时"),
    /** 第三方返回异常。 */
    THIRD_PARTY_BAD_RESPONSE(80006, "第三方返回异常"),
    /** 文件记录不存在。 */
    FILE_NOT_FOUND(80007, "文件不存在"),
    /** 文件已被其他业务记录引用。 */
    FILE_ALREADY_USED(80008, "文件已被使用"),

    // ---------- 9xxxx 系统 ----------
    /** 系统内部异常。 */
    SYSTEM_ERROR(90000, "服务器错误"),
    /** 未捕获异常。 */
    UNKNOWN_ERROR(90001, "未知错误");

    /** 数字状态码。 */
    private final int code;

    /** 默认提示文案。 */
    private final String msg;

    BizCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
