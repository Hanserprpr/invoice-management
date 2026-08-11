package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.FormDtos.CopyFormRequest;
import cn.sduonline.invoice.data.dto.FormDtos.CreateFormRequest;
import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import cn.sduonline.invoice.data.dto.FormDtos.FormVersionRequest;
import cn.sduonline.invoice.data.dto.FormDtos.UpdateFormRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.ApplicationForm;
import cn.sduonline.invoice.data.po.DictionaryItem;
import cn.sduonline.invoice.data.po.DictionaryVersion;
import cn.sduonline.invoice.data.po.FormVersion;
import cn.sduonline.invoice.data.po.Project;
import cn.sduonline.invoice.data.vo.ApplicationFormVO;
import cn.sduonline.invoice.data.vo.FormVersionVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.ApplicationFormMapper;
import cn.sduonline.invoice.mapper.DictionaryItemMapper;
import cn.sduonline.invoice.mapper.DictionaryVersionMapper;
import cn.sduonline.invoice.mapper.FormVersionMapper;
import cn.sduonline.invoice.mapper.ProjectMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationFormService {
    private final ApplicationFormMapper formMapper;
    private final FormVersionMapper formVersionMapper;
    private final ProjectMapper projectMapper;
    private final DictionaryVersionMapper dictionaryVersionMapper;
    private final DictionaryItemMapper dictionaryItemMapper;
    private final AuthorizationService authorizationService;
    private final FormSchemaValidator schemaValidator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ApplicationFormService(ApplicationFormMapper formMapper,
                                  FormVersionMapper formVersionMapper,
                                  ProjectMapper projectMapper,
                                  DictionaryVersionMapper dictionaryVersionMapper,
                                  DictionaryItemMapper dictionaryItemMapper,
                                  AuthorizationService authorizationService,
                                  FormSchemaValidator schemaValidator,
                                  AuditService auditService,
                                  ObjectMapper objectMapper) {
        this.formMapper = formMapper;
        this.formVersionMapper = formVersionMapper;
        this.projectMapper = projectMapper;
        this.dictionaryVersionMapper = dictionaryVersionMapper;
        this.dictionaryItemMapper = dictionaryItemMapper;
        this.authorizationService = authorizationService;
        this.schemaValidator = schemaValidator;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<ApplicationFormVO> list(String projectId) {
        Project project = requireProject(projectId);
        authorizationService.requireProjectManage(project.getId());
        return formMapper.selectList(new LambdaQueryWrapper<ApplicationForm>()
                        .eq(ApplicationForm::getOrganizationId, project.getOrganizationId())
                        .eq(ApplicationForm::getProjectId, projectId)
                        .orderByDesc(ApplicationForm::getCreatedAt))
                .stream().map(this::toVO).toList();
    }

    public ApplicationFormVO detail(String formId) {
        ApplicationForm form = requireForm(formId);
        authorizationService.requireProjectManage(form.getProjectId());
        return toVO(form);
    }

    @Transactional
    public ApplicationFormVO create(String projectId, String actorCasId, CreateFormRequest request) {
        Project project = requireWritableProject(projectId);
        authorizationService.requireProjectManage(projectId);
        schemaValidator.validate(request.schema(), false);
        validatePeriod(request.startsAt(), request.endsAt());
        ApplicationForm form = ApplicationForm.builder()
                .id(UlidGenerator.next()).organizationId(project.getOrganizationId())
                .projectId(projectId).name(request.name().trim()).status("DRAFT")
                .submissionScope(request.submissionScope()).startsAt(request.startsAt())
                .endsAt(request.endsAt()).maxSubmissionsPerUser(request.maxSubmissionsPerUser())
                .draftSchemaJson(writeJson(request.schema())).version(0L)
                .createdByCasId(actorCasId).build();
        formMapper.insert(form);
        auditService.append(project.getOrganizationId(), actorCasId, "FORM_CREATED",
                "APPLICATION_FORM", form.getId(), "{\"projectId\":\"" + projectId + "\"}");
        return toVO(form);
    }

    @Transactional
    public ApplicationFormVO copy(String projectId, String actorCasId, CopyFormRequest request) {
        Project target = requireWritableProject(projectId);
        authorizationService.requireProjectManage(projectId);
        ApplicationForm source = requireForm(request.sourceFormId());
        authorizationService.requireProjectManage(source.getProjectId());
        FormVersion latest = formVersionMapper.findLatest(target.getOrganizationId(), source.getId());
        if (latest == null) {
            throw new BusinessException(BizCode.FORM_VERSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        FormSchema schema = readSchema(latest.getSchemaJson());
        ApplicationForm copy = ApplicationForm.builder()
                .id(UlidGenerator.next()).organizationId(target.getOrganizationId())
                .projectId(projectId).name(request.name().trim()).status("DRAFT")
                .submissionScope(source.getSubmissionScope()).startsAt(null).endsAt(null)
                .maxSubmissionsPerUser(source.getMaxSubmissionsPerUser())
                .draftSchemaJson(writeJson(schema)).version(0L).createdByCasId(actorCasId).build();
        formMapper.insert(copy);
        auditService.append(target.getOrganizationId(), actorCasId, "FORM_COPIED",
                "APPLICATION_FORM", copy.getId(), "{\"sourceFormId\":\"" + source.getId() + "\"}");
        return toVO(copy);
    }

    @Transactional
    public ApplicationFormVO update(String formId, String actorCasId, UpdateFormRequest request) {
        ApplicationForm form = requireForm(formId);
        authorizationService.requireProjectManage(form.getProjectId());
        requireVersion(form, request.version());
        if ("ENDED".equals(form.getStatus())) stateConflict();
        requireWritableProject(form.getProjectId());
        if (request.name() != null) {
            if (request.name().isBlank()) invalid();
            form.setName(request.name().trim());
        }
        if (request.submissionScope() != null) form.setSubmissionScope(request.submissionScope());
        Instant startsAt = request.clearStartsAt() ? null
                : request.startsAt() == null ? form.getStartsAt() : request.startsAt();
        Instant endsAt = request.clearEndsAt() ? null
                : request.endsAt() == null ? form.getEndsAt() : request.endsAt();
        validatePeriod(startsAt, endsAt);
        form.setStartsAt(startsAt);
        form.setEndsAt(endsAt);
        if (request.maxSubmissionsPerUser() != null) {
            form.setMaxSubmissionsPerUser(request.maxSubmissionsPerUser());
        }
        if (request.schema() != null) {
            schemaValidator.validate(request.schema(), false);
            form.setDraftSchemaJson(writeJson(request.schema()));
        }
        updateWithVersion(form, request.version());
        auditService.append(form.getOrganizationId(), actorCasId, "FORM_UPDATED",
                "APPLICATION_FORM", formId, "{\"version\":" + form.getVersion() + "}");
        return toVO(form);
    }

    @Transactional
    public FormVersionVO publish(String formId, String actorCasId, FormVersionRequest request) {
        ApplicationForm form = requireForm(formId);
        authorizationService.requireProjectManage(form.getProjectId());
        requireVersion(form, request.version());
        if ("ENDED".equals(form.getStatus())) stateConflict();
        requireWritableProject(form.getProjectId());
        FormSchema schema = readSchema(form.getDraftSchemaJson());
        schemaValidator.validate(schema, true);
        String schemaJson = writeJson(schema);
        FormVersion previous = formVersionMapper.findLatest(form.getOrganizationId(), formId);
        if (previous != null && jsonEquals(previous.getSchemaJson(), schemaJson)) {
            throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        form.setStatus("PUBLISHED");
        updateWithVersion(form, request.version());
        int versionNo = formVersionMapper.findMaxVersionNo(form.getOrganizationId(), formId) + 1;
        FormVersion version = FormVersion.builder()
                .id(UlidGenerator.next()).organizationId(form.getOrganizationId()).formId(formId)
                .versionNo(versionNo).schemaJson(schemaJson)
                .dictionarySnapshotJson(writeJson(dictionarySnapshot()))
                .publishedByCasId(actorCasId).publishedAt(Instant.now()).build();
        formVersionMapper.insert(version);
        auditService.append(form.getOrganizationId(), actorCasId, "FORM_VERSION_PUBLISHED",
                "FORM_VERSION", version.getId(), "{\"formId\":\"" + formId
                        + "\",\"versionNo\":" + versionNo + "}");
        return toVersionVO(version);
    }

    @Transactional
    public ApplicationFormVO pause(String formId, String actorCasId, FormVersionRequest request) {
        return changeStatus(formId, actorCasId, request.version(), "PUBLISHED", "PAUSED", "FORM_PAUSED");
    }

    @Transactional
    public ApplicationFormVO resume(String formId, String actorCasId, FormVersionRequest request) {
        return changeStatus(formId, actorCasId, request.version(), "PAUSED", "PUBLISHED", "FORM_RESUMED");
    }

    @Transactional
    public ApplicationFormVO end(String formId, String actorCasId, FormVersionRequest request) {
        ApplicationForm form = requireForm(formId);
        authorizationService.requireProjectManage(form.getProjectId());
        requireVersion(form, request.version());
        if (!List.of("PUBLISHED", "PAUSED").contains(form.getStatus())) stateConflict();
        form.setStatus("ENDED");
        updateWithVersion(form, request.version());
        auditService.append(form.getOrganizationId(), actorCasId, "FORM_ENDED",
                "APPLICATION_FORM", formId, "{\"status\":\"ENDED\"}");
        return toVO(form);
    }

    public List<FormVersionVO> versions(String formId) {
        ApplicationForm form = requireForm(formId);
        authorizationService.requireProjectManage(form.getProjectId());
        return formVersionMapper.findForForm(form.getOrganizationId(), formId)
                .stream().map(this::toVersionVO).toList();
    }

    public FormVersionVO version(String formId, int versionNo) {
        ApplicationForm form = requireForm(formId);
        authorizationService.requireProjectManage(form.getProjectId());
        FormVersion version = formVersionMapper.findByVersionNo(form.getOrganizationId(), formId, versionNo);
        if (version == null) {
            throw new BusinessException(BizCode.FORM_VERSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return toVersionVO(version);
    }

    private ApplicationFormVO changeStatus(String formId, String actorCasId, long version,
                                           String expected, String next, String action) {
        ApplicationForm form = requireForm(formId);
        authorizationService.requireProjectManage(form.getProjectId());
        requireVersion(form, version);
        if (!expected.equals(form.getStatus())) stateConflict();
        if ("PUBLISHED".equals(next)) requireWritableProject(form.getProjectId());
        form.setStatus(next);
        updateWithVersion(form, version);
        auditService.append(form.getOrganizationId(), actorCasId, action,
                "APPLICATION_FORM", formId, "{\"status\":\"" + next + "\"}");
        return toVO(form);
    }

    private void updateWithVersion(ApplicationForm form, long version) {
        form.setVersion(version);
        if (formMapper.updateById(form) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        form.setVersion(version + 1);
    }

    private void requireVersion(ApplicationForm form, long version) {
        if (form.getVersion() == null || form.getVersion() != version) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
    }

    private Project requireProject(String projectId) {
        String organizationId = TenantContext.requireOrganizationId();
        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getOrganizationId, organizationId).eq(Project::getId, projectId));
        if (project == null) {
            throw new BusinessException(BizCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return project;
    }

    private Project requireWritableProject(String projectId) {
        Project project = requireProject(projectId);
        if (List.of("ORGANIZING", "ARCHIVED").contains(project.getStatus())) {
            throw new BusinessException(BizCode.PROJECT_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        return project;
    }

    private ApplicationForm requireForm(String formId) {
        String organizationId = TenantContext.requireOrganizationId();
        ApplicationForm form = formMapper.selectOne(new LambdaQueryWrapper<ApplicationForm>()
                .eq(ApplicationForm::getOrganizationId, organizationId)
                .eq(ApplicationForm::getId, formId));
        if (form == null) {
            throw new BusinessException(BizCode.FORM_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return form;
    }

    private ApplicationFormVO toVO(ApplicationForm form) {
        FormVersion latest = formVersionMapper.findLatest(form.getOrganizationId(), form.getId());
        boolean changed = latest == null || !jsonEquals(latest.getSchemaJson(), form.getDraftSchemaJson());
        return new ApplicationFormVO(form.getId(), form.getOrganizationId(), form.getProjectId(),
                form.getName(), form.getStatus(), form.getSubmissionScope(), form.getStartsAt(),
                form.getEndsAt(), form.getMaxSubmissionsPerUser(), value(form.getVersion()),
                latest == null ? 0 : latest.getVersionNo(), changed, readSchema(form.getDraftSchemaJson()),
                form.getCreatedByCasId(), form.getCreatedAt(), form.getUpdatedAt());
    }

    private FormVersionVO toVersionVO(FormVersion version) {
        return new FormVersionVO(version.getId(), version.getFormId(), version.getVersionNo(),
                readSchema(version.getSchemaJson()), readTree(version.getDictionarySnapshotJson()),
                version.getPublishedByCasId(), version.getPublishedAt());
    }

    private Map<String, Object> dictionarySnapshot() {
        List<DictionaryVersion> versions = dictionaryVersionMapper.selectList(
                new LambdaQueryWrapper<DictionaryVersion>()
                        .eq(DictionaryVersion::getStatus, "PUBLISHED")
                        .orderByAsc(DictionaryVersion::getDictionaryType)
                        .orderByDesc(DictionaryVersion::getVersionNo));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (DictionaryVersion version : versions) {
            if (snapshot.containsKey(version.getDictionaryType())) continue;
            List<Map<String, Object>> items = new ArrayList<>();
            for (DictionaryItem item : dictionaryItemMapper.selectList(
                    new LambdaQueryWrapper<DictionaryItem>()
                            .eq(DictionaryItem::getDictionaryVersionId, version.getId())
                            .eq(DictionaryItem::getEnabled, true)
                            .orderByAsc(DictionaryItem::getSortOrder))) {
                items.add(Map.of("code", item.getCode(), "displayName", item.getDisplayName()));
            }
            snapshot.put(version.getDictionaryType(), Map.of(
                    "versionId", version.getId(), "versionNo", version.getVersionNo(), "items", items));
        }
        return snapshot;
    }

    private boolean jsonEquals(String left, String right) {
        return readTree(left).equals(readTree(right));
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

    private void validatePeriod(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) invalid();
    }

    private long value(Long version) {
        return version == null ? 0 : version;
    }

    private void invalid() {
        throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
    }

    private void stateConflict() {
        throw new BusinessException(BizCode.FORM_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
    }
}
