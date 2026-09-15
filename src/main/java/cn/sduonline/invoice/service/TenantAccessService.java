package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 当前用户的租户访问校验。
 */
@Service
public class TenantAccessService {

    private final OrganizationMemberMapper organizationMemberMapper;
    private final UserMapper userMapper;

    public TenantAccessService(OrganizationMemberMapper organizationMemberMapper,
                               UserMapper userMapper) {
        this.organizationMemberMapper = organizationMemberMapper;
        this.userMapper = userMapper;
    }

    public boolean hasAccess(String organizationId, String casId) {
        return organizationMemberMapper.hasActiveMembership(organizationId, casId)
                || isActivePlatformAdmin(casId);
    }

    private boolean isActivePlatformAdmin(String casId) {
        User user = userMapper.selectById(casId);
        return user != null && "ACTIVE".equals(user.getStatus())
                && Boolean.TRUE.equals(user.getIsPlatformAdmin());
    }
}
