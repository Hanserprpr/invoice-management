package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.ProjectDtos.AccessGrant;
import cn.sduonline.invoice.data.dto.ProjectDtos.ChangeStateRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.CreateProjectRequest;
import cn.sduonline.invoice.data.dto.ProjectDtos.UpdateProjectRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.OrganizationMember;
import cn.sduonline.invoice.data.po.Project;
import cn.sduonline.invoice.data.po.ProjectAccess;
import cn.sduonline.invoice.data.po.ProjectManager;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.data.vo.ProjectVO;
import cn.sduonline.invoice.data.vo.ProjectVO.AccessGrantVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.mapper.ProjectAccessMapper;
import cn.sduonline.invoice.mapper.ProjectManagerMapper;
import cn.sduonline.invoice.mapper.ProjectMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProjectService {
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "COLLECTING", "COLLECTION_STOPPED", "ORGANIZING", "ARCHIVED");

    private final ProjectMapper projectMapper;
    private final ProjectManagerMapper projectManagerMapper;
    private final ProjectAccessMapper projectAccessMapper;
    private final OrganizationMemberMapper memberMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final RuleSetService ruleSetService;

    public ProjectService(ProjectMapper projectMapper,
                          ProjectManagerMapper projectManagerMapper,
                          ProjectAccessMapper projectAccessMapper,
                          OrganizationMemberMapper memberMapper,
                          AuthorizationService authorizationService,
                          AuditService auditService,
                          RuleSetService ruleSetService) {
        this.projectMapper = projectMapper;
        this.projectManagerMapper = projectManagerMapper;
        this.projectAccessMapper = projectAccessMapper;
        this.memberMapper = memberMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.ruleSetService = ruleSetService;
    }

    public PageResult<ProjectVO> list(long page, long pageSize, String status) {
        String organizationId = TenantContext.requireOrganizationId();
        String casId = TenantContext.requireCasId();
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        }
        Page<Project> result = projectMapper.findVisible(new Page<>(page, pageSize), organizationId,
                casId, authorizationService.isClubAdmin(), status);
        List<ProjectVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, page, pageSize, result.getTotal());
    }

    public ProjectVO detail(String projectId) {
        Project project = requireProject(projectId);
        if (!"ALL".equals(project.getVisibility()) && !authorizationService.canViewProject(projectId)) {
            throw new BusinessException(BizCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
        return toVO(project);
    }

    @Transactional
    public ProjectVO create(String actorCasId, CreateProjectRequest request) {
        authorizationService.requirePermission("project:create");
        String organizationId = TenantContext.requireOrganizationId();
        validatePeriod(request.startAt(), request.endAt());
        Set<String> managers = validateManagers(organizationId, request.managerCasIds());
        List<AccessBinding> grants = validateAccessGrants(organizationId, request.accessGrants());
        Project project = Project.builder()
                .id(UlidGenerator.next())
                .organizationId(organizationId)
                .name(request.name().trim())
                .description(request.description())
                .budget(request.budget())
                .fundingSource(request.fundingSource())
                .paperRequired(Boolean.TRUE.equals(request.paperRequired()))
                .ruleSetVersionId(validateRuleVersion(request.ruleSetVersionId()))
                .visibility(request.visibility())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .status("DRAFT")
                .version(0L)
                .createdByCasId(actorCasId)
                .build();
        projectMapper.insert(project);
        replaceManagers(project, actorCasId, managers);
        replaceAccess(project, actorCasId, grants);
        auditService.append(organizationId, actorCasId, "PROJECT_CREATED", "PROJECT",
                project.getId(), "{\"status\":\"DRAFT\",\"managerCount\":" + managers.size() + "}");
        return toVO(project);
    }

    @Transactional
    public ProjectVO update(String projectId, String actorCasId, UpdateProjectRequest request) {
        Project project = requireProject(projectId);
        authorizationService.requireProjectManage(projectId);
        if ("ARCHIVED".equals(project.getStatus())) {
            throw new BusinessException(BizCode.PROJECT_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
            }
            project.setName(request.name().trim());
        }
        if (request.clearDescription()) project.setDescription(null);
        else if (request.description() != null) project.setDescription(request.description());
        if (request.clearBudget()) project.setBudget(null);
        else if (request.budget() != null) project.setBudget(request.budget());
        if (request.clearFundingSource()) project.setFundingSource(null);
        else if (request.fundingSource() != null) project.setFundingSource(request.fundingSource());
        if (request.paperRequired() != null) project.setPaperRequired(request.paperRequired());
        if (request.clearRuleSetVersion()) project.setRuleSetVersionId(null);
        else if (request.ruleSetVersionId() != null) {
            project.setRuleSetVersionId(validateRuleVersion(request.ruleSetVersionId()));
        }
        if (request.visibility() != null) project.setVisibility(request.visibility());
        Instant start = request.clearStartAt() ? null
                : request.startAt() == null ? project.getStartAt() : request.startAt();
        Instant end = request.clearEndAt() ? null
                : request.endAt() == null ? project.getEndAt() : request.endAt();
        validatePeriod(start, end);
        project.setStartAt(start);
        project.setEndAt(end);
        Set<String> managers = request.managerCasIds() == null ? null
                : validateManagers(project.getOrganizationId(), request.managerCasIds());
        List<AccessBinding> grants = request.accessGrants() == null ? null
                : validateAccessGrants(project.getOrganizationId(), request.accessGrants());
        project.setVersion(request.version());
        if (projectMapper.updateById(project) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        project.setVersion(request.version() + 1);
        if (managers != null) replaceManagers(project, actorCasId, managers);
        if (grants != null) replaceAccess(project, actorCasId, grants);
        auditService.append(project.getOrganizationId(), actorCasId, "PROJECT_UPDATED", "PROJECT",
                projectId, "{\"version\":" + project.getVersion() + "}");
        return toVO(project);
    }

    @Transactional
    public ProjectVO open(String projectId, String actorCasId, ChangeStateRequest request) {
        Project project = requireProject(projectId);
        authorizationService.requireProjectManage(projectId);
        if (!Set.of("DRAFT", "COLLECTION_STOPPED").contains(project.getStatus())) {
            throw stateConflict();
        }
        return changeState(project, actorCasId, request.version(), "COLLECTING", "PROJECT_COLLECTION_OPENED");
    }

    @Transactional
    public ProjectVO stopCollection(String projectId, String actorCasId, ChangeStateRequest request) {
        Project project = requireProject(projectId);
        authorizationService.requireProjectManage(projectId);
        if (!"COLLECTING".equals(project.getStatus())) throw stateConflict();
        return changeState(project, actorCasId, request.version(), "COLLECTION_STOPPED",
                "PROJECT_COLLECTION_STOPPED");
    }

    @Transactional
    public ProjectVO startOrganizing(String projectId, String actorCasId, ChangeStateRequest request) {
        Project project = requireProject(projectId);
        authorizationService.requireProjectManage(projectId);
        if (!"COLLECTION_STOPPED".equals(project.getStatus())) throw stateConflict();
        return changeState(project, actorCasId, request.version(), "ORGANIZING",
                "PROJECT_ORGANIZING_STARTED");
    }

    @Transactional
    public ProjectVO archive(String projectId, String actorCasId, ChangeStateRequest request) {
        Project project = requireProject(projectId);
        authorizationService.requireProjectManage(projectId);
        if (!"ORGANIZING".equals(project.getStatus())) throw stateConflict();
        project.setArchivedAt(Instant.now());
        return changeState(project, actorCasId, request.version(), "ARCHIVED", "PROJECT_ARCHIVED");
    }

    private ProjectVO changeState(Project project, String actorCasId, long version,
                                  String nextState, String auditAction) {
        authorizationService.requireProjectManage(project.getId());
        String previous = project.getStatus();
        project.setStatus(nextState);
        project.setVersion(version);
        if (projectMapper.updateById(project) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        project.setVersion(version + 1);
        auditService.append(project.getOrganizationId(), actorCasId, auditAction, "PROJECT",
                project.getId(), "{\"from\":\"" + previous + "\",\"to\":\"" + nextState + "\"}");
        return toVO(project);
    }

    private Project requireProject(String projectId) {
        String organizationId = TenantContext.requireOrganizationId();
        Project project = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                .eq(Project::getId, projectId)
                .eq(Project::getOrganizationId, organizationId));
        if (project == null) {
            throw new BusinessException(BizCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return project;
    }

    private Set<String> validateManagers(String organizationId, List<String> casIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>(casIds);
        if (result.isEmpty() || result.size() != casIds.size()) {
            throw new BusinessException(BizCode.PROJECT_ACCESS_INVALID, HttpStatus.BAD_REQUEST);
        }
        result.forEach(casId -> requireActiveMember(organizationId, casId));
        return result;
    }

    private List<AccessBinding> validateAccessGrants(String organizationId, List<AccessGrant> grants) {
        List<AccessBinding> result = new ArrayList<>();
        Set<String> duplicateGuard = new HashSet<>();
        for (AccessGrant grant : grants) {
            OrganizationMember member = requireActiveMember(organizationId, grant.casId());
            for (String type : grant.accessTypes()) {
                if (!duplicateGuard.add(grant.casId() + ":" + type)) {
                    throw new BusinessException(BizCode.PROJECT_ACCESS_INVALID, HttpStatus.BAD_REQUEST);
                }
                result.add(new AccessBinding(member.getId(), type));
            }
        }
        return result;
    }

    private OrganizationMember requireActiveMember(String organizationId, String casId) {
        OrganizationMember member = memberMapper.findByOrganizationAndCasId(organizationId, casId);
        LocalDate today = LocalDate.now();
        if (member == null || !"ACTIVE".equals(member.getStatus())
                || member.getTermStart() != null && member.getTermStart().isAfter(today)
                || member.getTermEnd() != null && member.getTermEnd().isBefore(today)) {
            throw new BusinessException(BizCode.PROJECT_ACCESS_INVALID, HttpStatus.BAD_REQUEST);
        }
        return member;
    }

    private void replaceManagers(Project project, String actorCasId, Set<String> managerCasIds) {
        projectManagerMapper.deleteForProject(project.getOrganizationId(), project.getId());
        for (String casId : managerCasIds) {
            OrganizationMember member = requireActiveMember(project.getOrganizationId(), casId);
            projectManagerMapper.insert(ProjectManager.builder()
                    .projectId(project.getId()).organizationId(project.getOrganizationId())
                    .memberId(member.getId()).assignedByCasId(actorCasId).build());
        }
    }

    private void replaceAccess(Project project, String actorCasId, List<AccessBinding> grants) {
        projectAccessMapper.deleteForProject(project.getOrganizationId(), project.getId());
        for (AccessBinding grant : grants) {
            projectAccessMapper.insert(ProjectAccess.builder()
                    .projectId(project.getId()).organizationId(project.getOrganizationId())
                    .memberId(grant.memberId()).accessType(grant.accessType())
                    .grantedByCasId(actorCasId).build());
        }
    }

    private ProjectVO toVO(Project project) {
        Map<String, Set<String>> grants = new LinkedHashMap<>();
        if (authorizationService.canManageProject(project.getId())) {
            for (String binding : projectAccessMapper.findAccessBindings(
                    project.getOrganizationId(), project.getId())) {
                String[] parts = binding.split(":", 2);
                grants.computeIfAbsent(parts[0], ignored -> new LinkedHashSet<>()).add(parts[1]);
            }
        }
        List<AccessGrantVO> access = grants.entrySet().stream()
                .map(entry -> new AccessGrantVO(entry.getKey(), entry.getValue())).toList();
        return new ProjectVO(project.getId(), project.getOrganizationId(), project.getName(),
                project.getDescription(), project.getBudget(), project.getFundingSource(),
                Boolean.TRUE.equals(project.getPaperRequired()), project.getRuleSetVersionId(),
                project.getVisibility(),
                project.getStartAt(), project.getEndAt(), project.getStatus(),
                project.getVersion() == null ? 0 : project.getVersion(), project.getCreatedByCasId(),
                project.getCreatedAt(), project.getUpdatedAt(), project.getArchivedAt(),
                projectMapper.findManagerCasIds(project.getOrganizationId(), project.getId()), access);
    }

    private String validateRuleVersion(String versionId) {
        if (versionId == null || versionId.isBlank()) return null;
        return ruleSetService.requireEffectiveVersion(versionId.trim()).getId();
    }

    private void validatePeriod(Instant start, Instant end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
        }
    }

    private BusinessException stateConflict() {
        return new BusinessException(BizCode.PROJECT_STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
    }

    private record AccessBinding(String memberId, String accessType) {
    }
}
