package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.OrganizationDtos.CreateOrganizationRequest;
import cn.sduonline.invoice.data.dto.OrganizationDtos.UpdateOrganizationRequest;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.Organization;
import cn.sduonline.invoice.data.po.OrganizationMember;
import cn.sduonline.invoice.data.po.OrganizationMemberRole;
import cn.sduonline.invoice.data.po.Role;
import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.data.vo.OrganizationVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.OrganizationMapper;
import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.mapper.OrganizationMemberRoleMapper;
import cn.sduonline.invoice.mapper.RoleMapper;
import cn.sduonline.invoice.mapper.UserMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OrganizationService {
    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper memberMapper;
    private final OrganizationMemberRoleMapper memberRoleMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final AuthorizationService authorizationService;
    private final DictionaryTemplateService dictionaryTemplateService;
    private final AuditService auditService;

    public OrganizationService(OrganizationMapper organizationMapper,
                               OrganizationMemberMapper memberMapper,
                               OrganizationMemberRoleMapper memberRoleMapper,
                               UserMapper userMapper, RoleMapper roleMapper,
                               AuthorizationService authorizationService,
                               DictionaryTemplateService dictionaryTemplateService,
                               AuditService auditService) {
        this.organizationMapper = organizationMapper;
        this.memberMapper = memberMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.authorizationService = authorizationService;
        this.dictionaryTemplateService = dictionaryTemplateService;
        this.auditService = auditService;
    }

    public List<OrganizationVO> listForUser(String casId) {
        return memberMapper.findOrganizationsForUser(casId).stream().map(this::toVO).toList();
    }

    public OrganizationVO get(String organizationId) {
        authorizationService.requireTenantPath(organizationId);
        Organization organization = organizationMapper.selectById(organizationId);
        if (organization == null) {
            throw new BusinessException(BizCode.CLUB_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return toVO(organization);
    }

    @Transactional
    public OrganizationVO create(String actorCasId, CreateOrganizationRequest request) {
        authorizationService.requirePlatformAdmin(actorCasId);
        validateTerm(request.initialAdmin().termStart(), request.initialAdmin().termEnd());
        Long sameName = organizationMapper.selectCount(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getName, request.name()));
        if (sameName > 0) {
            throw new BusinessException(BizCode.CLUB_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }
        String organizationId = UlidGenerator.next();
        Organization organization = Organization.builder()
                .id(organizationId).name(request.name().trim())
                .type(request.type() == null || request.type().isBlank() ? "CLUB" : request.type())
                .status("ACTIVE").version(0L).build();
        organizationMapper.insert(organization);

        try (TenantContext.Scope ignored = TenantContext.open(organizationId, actorCasId)) {
            User initialUser = userMapper.selectById(request.initialAdmin().casId());
            if (initialUser == null) {
                userMapper.insert(User.builder().casId(request.initialAdmin().casId())
                        .name(request.initialAdmin().name()).status("ACTIVE")
                        .isPlatformAdmin(false).version(0L).build());
            } else if (!"ACTIVE".equals(initialUser.getStatus())) {
                throw new BusinessException(BizCode.USER_DISABLED, HttpStatus.CONFLICT);
            }
            OrganizationMember member = OrganizationMember.builder()
                    .id(UlidGenerator.next()).organizationId(organizationId)
                    .casId(request.initialAdmin().casId()).status("ACTIVE")
                    .termStart(request.initialAdmin().termStart())
                    .termEnd(request.initialAdmin().termEnd()).version(0L).build();
            memberMapper.insert(member);
            Role clubAdmin = roleMapper.findByCode("CLUB_ADMIN");
            memberRoleMapper.insert(OrganizationMemberRole.builder()
                    .memberId(member.getId()).organizationId(organizationId)
                    .roleId(clubAdmin.getId()).effectiveFrom(Instant.now())
                    .assignedByCasId(actorCasId).build());
            dictionaryTemplateService.clonePublishedTemplates(organizationId, actorCasId);
            auditService.append(organizationId, actorCasId, "ORGANIZATION_CREATED",
                    "ORGANIZATION", organizationId, "{\"initialAdmin\":\""
                            + request.initialAdmin().casId() + "\"}");
        }
        return toVO(organization);
    }

    @Transactional
    public OrganizationVO update(String actorCasId, String organizationId,
                                 UpdateOrganizationRequest request) {
        authorizationService.requirePlatformAdmin(actorCasId);
        Organization organization = organizationMapper.selectById(organizationId);
        if (organization == null) {
            throw new BusinessException(BizCode.CLUB_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        organization.setVersion(request.version());
        if (request.name() != null) organization.setName(request.name().trim());
        if (request.status() != null) organization.setStatus(request.status());
        if (organizationMapper.updateById(organization) != 1) {
            throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
        }
        try (TenantContext.Scope ignored = TenantContext.open(organizationId, actorCasId)) {
            auditService.append(organizationId, actorCasId, "ORGANIZATION_UPDATED",
                    "ORGANIZATION", organizationId, "{\"status\":\"" + organization.getStatus() + "\"}");
        }
        return toVO(organization);
    }

    private void validateTerm(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(BizCode.MEMBER_TERM_INVALID, HttpStatus.BAD_REQUEST);
        }
    }

    private OrganizationVO toVO(Organization organization) {
        return new OrganizationVO(organization.getId(), organization.getName(), organization.getType(),
                organization.getStatus(), organization.getVersion() == null ? 0 : organization.getVersion());
    }
}
