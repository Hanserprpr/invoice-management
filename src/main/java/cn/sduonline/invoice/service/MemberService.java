package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateMemberRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.ProjectGrant;
import cn.sduonline.invoice.data.dto.OrganizationDtos.ReplaceRolesRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.RoleAssignment;
import cn.sduonline.invoice.data.dto.OrganizationDtos.UpdateMemberRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.OrganizationMember;
import cn.sduonline.invoice.data.po.OrganizationMemberRole;
import cn.sduonline.invoice.data.po.Project;
import cn.sduonline.invoice.data.po.ProjectAccess;
import cn.sduonline.invoice.data.po.Role;
import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.data.vo.MemberVO;
import cn.sduonline.invoice.data.vo.PageResult;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.MemberRoleQueryMapper;
import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.mapper.OrganizationMemberRoleMapper;
import cn.sduonline.invoice.mapper.ProjectAccessMapper;
import cn.sduonline.invoice.mapper.ProjectMapper;
import cn.sduonline.invoice.mapper.RoleMapper;
import cn.sduonline.invoice.mapper.UserMapper;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MemberService {
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "MEMBER", "PROJECT_MANAGER", "REVIEWER", "CLUB_ADMIN", "AUDITOR");

    private final OrganizationMemberMapper memberMapper;
    private final OrganizationMemberRoleMapper memberRoleMapper;
    private final MemberRoleQueryMapper memberRoleQueryMapper;
    private final ProjectAccessMapper projectAccessMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public MemberService(OrganizationMemberMapper memberMapper,
                         OrganizationMemberRoleMapper memberRoleMapper,
                         MemberRoleQueryMapper memberRoleQueryMapper,
                         ProjectAccessMapper projectAccessMapper,
                         ProjectMapper projectMapper, UserMapper userMapper,
                         RoleMapper roleMapper, AuthorizationService authorizationService,
                         AuditService auditService) {
        this.memberMapper = memberMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.memberRoleQueryMapper = memberRoleQueryMapper;
        this.projectAccessMapper = projectAccessMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public PageResult<MemberVO> list(String organizationId, long page, long pageSize) {
        authorizationService.requireTenantPath(organizationId);
        authorizationService.requirePermission("member:manage");
        Page<OrganizationMember> result = memberMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<OrganizationMember>()
                        .eq(OrganizationMember::getOrganizationId, organizationId)
                        .orderByAsc(OrganizationMember::getCasId));
        List<MemberVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, page, pageSize, result.getTotal());
    }

    @Transactional
    public MemberVO create(String organizationId, String actorCasId, CreateMemberRequest request) {
        authorizationService.requireTenantPath(organizationId);
        authorizationService.requirePermission("member:manage");
        validateTerm(request.termStart(), request.termEnd());
        if (memberMapper.findByOrganizationAndCasId(organizationId, request.casId()) != null) {
            throw new BusinessException(BizCode.MEMBERSHIP_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
        User user = userMapper.selectById(request.casId());
        if (user == null) {
            userMapper.insert(User.builder().casId(request.casId()).name(request.name())
                    .status("ACTIVE").isPlatformAdmin(false).version(0L).build());
        } else if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(BizCode.USER_DISABLED, HttpStatus.CONFLICT);
        }
        OrganizationMember member = OrganizationMember.builder()
                .id(UlidGenerator.next()).organizationId(organizationId).casId(request.casId())
                .status("ACTIVE").termStart(request.termStart()).termEnd(request.termEnd())
                .version(0L).build();
        memberMapper.insert(member);
        Role memberRole = roleMapper.findByCode("MEMBER");
        memberRoleMapper.insert(OrganizationMemberRole.builder()
                .memberId(member.getId()).organizationId(organizationId)
                .roleId(memberRole.getId()).effectiveFrom(Instant.now())
                .assignedByCasId(actorCasId).build());
        auditService.append(organizationId, actorCasId, "MEMBER_CREATED",
                "ORGANIZATION_MEMBER", member.getId(), "{\"casId\":\"" + request.casId() + "\"}");
        return toVO(member);
    }

    @Transactional
    public MemberVO update(String organizationId, String casId, String actorCasId,
                           UpdateMemberRequest request) {
        authorizationService.requireTenantPath(organizationId);
        authorizationService.requirePermission("member:manage");
        OrganizationMember member = requireMember(organizationId, casId);
        if ("LEFT".equals(member.getStatus()) && "ACTIVE".equals(request.status())) {
            throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        LocalDate start = request.clearTermStart() ? null
                : request.termStart() == null ? member.getTermStart() : request.termStart();
        LocalDate end = request.clearTermEnd() ? null
                : request.termEnd() == null ? member.getTermEnd() : request.termEnd();
        validateTerm(start, end);
        member.setTermStart(start);
        member.setTermEnd(end);
        member.setVersion(request.version());
        if (request.status() != null) {
            member.setStatus(request.status());
            member.setEndedAt("ACTIVE".equals(request.status()) ? null : Instant.now());
        }
        if (memberMapper.updateById(member) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        auditService.append(organizationId, actorCasId, "MEMBER_UPDATED",
                "ORGANIZATION_MEMBER", member.getId(), "{\"status\":\"" + member.getStatus() + "\"}");
        return toVO(member);
    }

    @Transactional
    public MemberVO replaceRoles(String organizationId, String casId, String actorCasId,
                                 ReplaceRolesRequest request) {
        authorizationService.requireTenantPath(organizationId);
        authorizationService.requirePermission("role:manage");
        OrganizationMember member = requireMember(organizationId, casId);
        List<RoleBinding> roles = validateRoles(request.roles());
        List<ProjectAccess> grants = validateGrants(organizationId, member.getId(), actorCasId,
                request.projectGrants());
        if (memberMapper.bumpVersion(organizationId, casId, request.version()) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        member.setVersion(request.version() + 1);
        memberRoleMapper.deleteForMember(organizationId, member.getId());
        projectAccessMapper.deleteForMember(organizationId, member.getId());
        for (RoleBinding binding : roles) {
            memberRoleMapper.insert(OrganizationMemberRole.builder()
                    .memberId(member.getId()).organizationId(organizationId)
                    .roleId(binding.role().getId()).effectiveFrom(binding.from())
                    .effectiveUntil(binding.until()).assignedByCasId(actorCasId).build());
        }
        grants.forEach(projectAccessMapper::insert);
        auditService.append(organizationId, actorCasId, "MEMBER_ROLES_REPLACED",
                "ORGANIZATION_MEMBER", member.getId(), "{\"roleCount\":" + roles.size()
                        + ",\"projectGrantCount\":" + grants.size() + "}");
        return toVO(member);
    }

    private List<RoleBinding> validateRoles(List<RoleAssignment> assignments) {
        Set<String> duplicateGuard = new HashSet<>();
        return assignments.stream().map(assignment -> {
            if (!ALLOWED_ROLES.contains(assignment.code()) || !duplicateGuard.add(assignment.code())) {
                throw new BusinessException(BizCode.ROLE_NOT_FOUND, HttpStatus.BAD_REQUEST);
            }
            Instant from = assignment.effectiveFrom() == null ? Instant.now() : assignment.effectiveFrom();
            if (assignment.effectiveUntil() != null && assignment.effectiveUntil().isBefore(from)) {
                throw new BusinessException(BizCode.MEMBER_TERM_INVALID, HttpStatus.BAD_REQUEST);
            }
            Role role = roleMapper.findByCode(assignment.code());
            if (role == null) throw new BusinessException(BizCode.ROLE_NOT_FOUND, HttpStatus.BAD_REQUEST);
            return new RoleBinding(role, from, assignment.effectiveUntil());
        }).toList();
    }

    private List<ProjectAccess> validateGrants(String organizationId, String memberId,
                                               String actorCasId, List<ProjectGrant> projectGrants) {
        Set<String> duplicateGuard = new HashSet<>();
        return projectGrants.stream().flatMap(grant -> {
            Project project = projectMapper.selectById(grant.projectId());
            if (project == null || !organizationId.equals(project.getOrganizationId())) {
                throw new BusinessException(BizCode.PROJECT_ACCESS_INVALID, HttpStatus.BAD_REQUEST);
            }
            return grant.accessTypes().stream().map(type -> {
                if (!duplicateGuard.add(grant.projectId() + ":" + type)) {
                    throw new BusinessException(BizCode.PROJECT_ACCESS_INVALID, HttpStatus.BAD_REQUEST);
                }
                return ProjectAccess.builder().projectId(grant.projectId())
                        .organizationId(organizationId).memberId(memberId).accessType(type)
                        .grantedByCasId(actorCasId).build();
            });
        }).toList();
    }

    private OrganizationMember requireMember(String organizationId, String casId) {
        OrganizationMember member = memberMapper.findByOrganizationAndCasId(organizationId, casId);
        if (member == null) {
            throw new BusinessException(BizCode.MEMBERSHIP_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return member;
    }

    private MemberVO toVO(OrganizationMember member) {
        User user = userMapper.selectById(member.getCasId());
        return new MemberVO(member.getId(), member.getOrganizationId(), member.getCasId(),
                user == null ? null : user.getName(), member.getStatus(), member.getTermStart(),
                member.getTermEnd(), member.getVersion() == null ? 0 : member.getVersion(),
                memberRoleQueryMapper.findActiveRoleCodes(member.getOrganizationId(), member.getId()));
    }

    private void validateTerm(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(BizCode.MEMBER_TERM_INVALID, HttpStatus.BAD_REQUEST);
        }
    }

    private record RoleBinding(Role role, Instant from, Instant until) {
    }
}
