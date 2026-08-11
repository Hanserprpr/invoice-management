package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.ApplicationDtos.SaveDraftRequest;
import cn.sduonline.invoice.data.dto.ApplicationDtos.SubmitRequest;
import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.Application;
import cn.sduonline.invoice.data.po.ApplicationForm;
import cn.sduonline.invoice.data.po.ApplicationRevision;
import cn.sduonline.invoice.data.po.FormVersion;
import cn.sduonline.invoice.data.po.Project;
import cn.sduonline.invoice.data.vo.ApplicationRevisionVO;
import cn.sduonline.invoice.data.vo.ApplicationVO;
import cn.sduonline.invoice.data.vo.AvailableFormVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.ApplicationFormMapper;
import cn.sduonline.invoice.mapper.ApplicationMapper;
import cn.sduonline.invoice.mapper.ApplicationRevisionMapper;
import cn.sduonline.invoice.mapper.FormVersionMapper;
import cn.sduonline.invoice.mapper.ProjectMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ApplicationService {
    private final ApplicationMapper applicationMapper;
    private final ApplicationRevisionMapper revisionMapper;
    private final ApplicationFormMapper formMapper;
    private final FormVersionMapper formVersionMapper;
    private final ProjectMapper projectMapper;
    private final AuthorizationService authorizationService;
    private final ApplicationAnswerValidator answerValidator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ApplicationService(ApplicationMapper applicationMapper,
                              ApplicationRevisionMapper revisionMapper,
                              ApplicationFormMapper formMapper,
                              FormVersionMapper formVersionMapper,
                              ProjectMapper projectMapper,
                              AuthorizationService authorizationService,
                              ApplicationAnswerValidator answerValidator,
                              AuditService auditService,
                              ObjectMapper objectMapper) {
        this.applicationMapper = applicationMapper;
        this.revisionMapper = revisionMapper;
        this.formMapper = formMapper;
        this.formVersionMapper = formVersionMapper;
        this.projectMapper = projectMapper;
        this.authorizationService = authorizationService;
        this.answerValidator = answerValidator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<AvailableFormVO> availableForms() {
        String organizationId = TenantContext.requireOrganizationId();
        return formMapper.findCurrentlyAvailable(organizationId).stream()
                .filter(this::canSubmitToScope)
                .map(this::toAvailableForm)
                .toList();
    }

    public AvailableFormVO availableForm(String formId) {
        ApplicationForm form = requireForm(formId);
        requireCurrentlyAvailable(form);
        requireSubmissionScope(form);
        return toAvailableForm(form);
    }

    @Transactional
    public ApplicationVO createDraft(String formId, String actorCasId) {
        String organizationId = TenantContext.requireOrganizationId();
        ApplicationForm form = requireForm(formId);
        requireSubmissionScope(form);
        applicationMapper.lockApplicant(actorCasId);
        Application existing = applicationMapper.findEditableForApplicant(
                organizationId, formId, actorCasId);
        if (existing != null) return toVO(existing);
        requireCurrentlyAvailable(form);
        int submitted = applicationMapper.countSubmittedForApplicant(
                organizationId, formId, actorCasId);
        if (submitted >= form.getMaxSubmissionsPerUser()) {
            throw new BusinessException(BizCode.SUBMISSION_LIMIT_REACHED, HttpStatus.CONFLICT);
        }
        FormVersion latest = requireLatestVersion(form);
        Application application = Application.builder()
                .id(UlidGenerator.next()).organizationId(organizationId)
                .formVersionId(latest.getId()).applicantCasId(actorCasId)
                .answersJson("{}").status("DRAFT").version(0L).build();
        applicationMapper.insert(application);
        auditService.append(organizationId, actorCasId, "APPLICATION_DRAFT_CREATED",
                "APPLICATION", application.getId(), "{\"formId\":\"" + formId + "\"}");
        return toVO(application);
    }

    public PageResult<ApplicationVO> listMine(long page, long pageSize, String status) {
        if (status != null && !Set.of("DRAFT", "SUBMITTED", "PROCESSING", "RETURNED",
                "PARTIALLY_APPROVED", "APPROVED", "REJECTED", "COMPLETED").contains(status)) {
            invalid();
        }
        String organizationId = TenantContext.requireOrganizationId();
        String casId = TenantContext.requireCasId();
        LambdaQueryWrapper<Application> query = new LambdaQueryWrapper<Application>()
                .eq(Application::getOrganizationId, organizationId)
                .eq(Application::getApplicantCasId, casId)
                .orderByDesc(Application::getUpdatedAt);
        if (status != null) query.eq(Application::getStatus, status);
        Page<Application> result = applicationMapper.selectPage(new Page<>(page, pageSize), query);
        return new PageResult<>(result.getRecords().stream().map(this::toVO).toList(),
                page, pageSize, result.getTotal());
    }

    public ApplicationVO detail(String applicationId) {
        return toVO(requireOwnApplication(applicationId));
    }

    @Transactional
    public ApplicationVO saveDraft(String applicationId, String actorCasId, SaveDraftRequest request) {
        Application application = requireOwnApplication(applicationId);
        requireEditable(application);
        requireVersion(application, request.version());
        FormVersion formVersion = requireFormVersion(application.getFormVersionId());
        FormSchema schema = readSchema(formVersion.getSchemaJson());
        answerValidator.validate(schema, request.answers(), false);
        JsonNode previous = readTree(application.getAnswersJson());
        Set<String> changedFields = changedFields(previous, request.answers());
        if (changedFields.isEmpty()) return toVO(application);
        String answerJson = writeJson(request.answers());
        if (answerJson.length() > 1_000_000) invalid();
        application.setAnswersJson(answerJson);
        updateWithVersion(application, request.version());
        appendRevision(application, request.answers(), changedFields, null, actorCasId);
        auditService.append(application.getOrganizationId(), actorCasId, "APPLICATION_DRAFT_SAVED",
                "APPLICATION", applicationId, "{\"changedFieldCount\":" + changedFields.size() + "}");
        return toVO(application);
    }

    @Transactional
    public ApplicationVO submit(String applicationId, String actorCasId, SubmitRequest request) {
        Application application = requireOwnApplication(applicationId);
        if ("SUBMITTED".equals(application.getStatus())) return toVO(application);
        requireEditable(application);
        requireVersion(application, request.version());
        FormVersion formVersion = requireFormVersion(application.getFormVersionId());
        ApplicationForm form = requireForm(formVersion.getFormId());
        requireCurrentlyAvailable(form);
        requireSubmissionScope(form);
        FormSchema schema = readSchema(formVersion.getSchemaJson());
        answerValidator.validate(schema, readTree(application.getAnswersJson()), true);
        applicationMapper.lockApplicant(actorCasId);
        int submitted = applicationMapper.countSubmittedForApplicant(
                application.getOrganizationId(), form.getId(), actorCasId);
        if (("DRAFT".equals(application.getStatus()) && submitted >= form.getMaxSubmissionsPerUser())
                || ("RETURNED".equals(application.getStatus())
                && submitted > form.getMaxSubmissionsPerUser())) {
            throw new BusinessException(BizCode.SUBMISSION_LIMIT_REACHED, HttpStatus.CONFLICT);
        }
        application.setStatus("SUBMITTED");
        application.setSubmittedAt(Instant.now());
        updateWithVersion(application, request.version());
        auditService.append(application.getOrganizationId(), actorCasId, "APPLICATION_SUBMITTED",
                "APPLICATION", applicationId, "{\"formVersionId\":\""
                        + application.getFormVersionId() + "\"}");
        return toVO(application);
    }

    public List<ApplicationRevisionVO> revisions(String applicationId) {
        Application application = requireOwnApplication(applicationId);
        return revisionMapper.findForApplication(application.getOrganizationId(), applicationId)
                .stream().map(this::toRevisionVO).toList();
    }

    private ApplicationForm requireForm(String formId) {
        String organizationId = TenantContext.requireOrganizationId();
        ApplicationForm form = formMapper.selectOne(new LambdaQueryWrapper<ApplicationForm>()
                .eq(ApplicationForm::getOrganizationId, organizationId)
                .eq(ApplicationForm::getId, formId));
        if (form == null) throw new BusinessException(BizCode.FORM_NOT_FOUND, HttpStatus.NOT_FOUND);
        return form;
    }

    private void requireCurrentlyAvailable(ApplicationForm form) {
        if (!"PUBLISHED".equals(form.getStatus())) {
            throw new BusinessException(BizCode.FORM_NOT_PUBLISHED, HttpStatus.CONFLICT);
        }
        Instant now = Instant.now();
        if (form.getStartsAt() != null && now.isBefore(form.getStartsAt())
                || form.getEndsAt() != null && now.isAfter(form.getEndsAt())) {
            throw new BusinessException(BizCode.SUBMISSION_WINDOW_CLOSED, HttpStatus.CONFLICT);
        }
        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getOrganizationId, form.getOrganizationId())
                .eq(Project::getId, form.getProjectId()));
        if (project == null) throw new BusinessException(BizCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND);
        if (!"COLLECTING".equals(project.getStatus())) {
            throw new BusinessException(BizCode.SUBMISSION_WINDOW_CLOSED, HttpStatus.CONFLICT);
        }
    }

    private boolean canSubmitToScope(ApplicationForm form) {
        return "ALL_MEMBERS".equals(form.getSubmissionScope())
                || authorizationService.canSubmitProject(form.getProjectId());
    }

    private void requireSubmissionScope(ApplicationForm form) {
        if (!canSubmitToScope(form)) {
            throw new BusinessException(BizCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    private Application requireOwnApplication(String applicationId) {
        String organizationId = TenantContext.requireOrganizationId();
        String casId = TenantContext.requireCasId();
        Application application = applicationMapper.selectOne(new LambdaQueryWrapper<Application>()
                .eq(Application::getOrganizationId, organizationId)
                .eq(Application::getId, applicationId)
                .eq(Application::getApplicantCasId, casId));
        if (application == null) {
            throw new BusinessException(BizCode.APPLICATION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return application;
    }

    private FormVersion requireLatestVersion(ApplicationForm form) {
        FormVersion latest = formVersionMapper.findLatest(form.getOrganizationId(), form.getId());
        if (latest == null) {
            throw new BusinessException(BizCode.FORM_VERSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return latest;
    }

    private FormVersion requireFormVersion(String versionId) {
        FormVersion version = formVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(BizCode.FORM_VERSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return version;
    }

    private void requireEditable(Application application) {
        if (!Set.of("DRAFT", "RETURNED").contains(application.getStatus())) {
            throw new BusinessException(BizCode.APPLICATION_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
    }

    private void requireVersion(Application application, long version) {
        if (application.getVersion() == null || application.getVersion() != version) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
    }

    private void updateWithVersion(Application application, long version) {
        application.setVersion(version);
        if (applicationMapper.updateById(application) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        application.setVersion(version + 1);
    }

    private void appendRevision(Application application, JsonNode answers, Set<String> changedFields,
                                String reason, String actorCasId) {
        int revisionNo = revisionMapper.findMaxRevisionNo(
                application.getOrganizationId(), application.getId()) + 1;
        revisionMapper.insert(ApplicationRevision.builder()
                .id(UlidGenerator.next()).organizationId(application.getOrganizationId())
                .applicationId(application.getId()).revisionNo(revisionNo)
                .answersJson(writeJson(answers)).changedFieldsJson(writeJson(changedFields))
                .changeReason(reason).actorCasId(actorCasId).build());
    }

    private Set<String> changedFields(JsonNode previous, JsonNode current) {
        Set<String> keys = new HashSet<>();
        keys.addAll(previous.propertyNames());
        keys.addAll(current.propertyNames());
        keys.removeIf(key -> {
            JsonNode before = previous.get(key);
            JsonNode after = current.get(key);
            return before == null ? after == null : before.equals(after);
        });
        return keys;
    }

    private AvailableFormVO toAvailableForm(ApplicationForm form) {
        FormVersion latest = requireLatestVersion(form);
        return new AvailableFormVO(form.getId(), form.getProjectId(), form.getName(),
                form.getSubmissionScope(), form.getStartsAt(), form.getEndsAt(),
                form.getMaxSubmissionsPerUser(), latest.getId(), latest.getVersionNo(),
                readSchema(latest.getSchemaJson()));
    }

    private ApplicationVO toVO(Application application) {
        FormVersion formVersion = requireFormVersion(application.getFormVersionId());
        ApplicationForm form = requireForm(formVersion.getFormId());
        return new ApplicationVO(application.getId(), application.getOrganizationId(), form.getId(),
                form.getName(), formVersion.getId(), formVersion.getVersionNo(),
                application.getApplicantCasId(), readTree(application.getAnswersJson()),
                readSchema(formVersion.getSchemaJson()), application.getStatus(),
                application.getVersion() == null ? 0 : application.getVersion(),
                application.getSubmittedAt(), application.getCreatedAt(), application.getUpdatedAt());
    }

    private ApplicationRevisionVO toRevisionVO(ApplicationRevision revision) {
        Set<String> changed = new HashSet<>();
        JsonNode node = readTree(revision.getChangedFieldsJson());
        if (node != null && node.isArray()) for (JsonNode item : node) changed.add(item.asText());
        return new ApplicationRevisionVO(revision.getRevisionNo(), readTree(revision.getAnswersJson()),
                changed, revision.getChangeReason(), revision.getActorCasId(), revision.getCreatedAt());
    }

    private FormSchema readSchema(String json) {
        try {
            return objectMapper.readValue(json, FormSchema.class);
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void invalid() {
        throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
    }
}
