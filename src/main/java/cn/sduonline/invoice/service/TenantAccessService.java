package cn.sduonline.invoice.service;

import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import org.springframework.stereotype.Service;

/**
 * 当前用户的租户访问校验。
 */
@Service
public class TenantAccessService {

    private final OrganizationMemberMapper organizationMemberMapper;

    public TenantAccessService(OrganizationMemberMapper organizationMemberMapper) {
        this.organizationMemberMapper = organizationMemberMapper;
    }

    public boolean hasAccess(String organizationId, String casId) {
        return organizationMemberMapper.hasActiveMembership(organizationId, casId);
    }
}
