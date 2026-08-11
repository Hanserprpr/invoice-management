package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.AuthorizationMapper;
import cn.sduonline.invoice.mapper.UserMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
    private final UserMapper userMapper;
    private final AuthorizationMapper authorizationMapper;

    public AuthorizationService(UserMapper userMapper, AuthorizationMapper authorizationMapper) {
        this.userMapper = userMapper;
        this.authorizationMapper = authorizationMapper;
    }

    public void requirePlatformAdmin(String casId) {
        User user = userMapper.selectById(casId);
        if (user == null || !"ACTIVE".equals(user.getStatus())
                || !Boolean.TRUE.equals(user.getIsPlatformAdmin())) {
            throw new BusinessException(BizCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    public void requirePermission(String permission) {
        String organizationId = TenantContext.requireOrganizationId();
        String casId = TenantContext.requireCasId();
        if (!authorizationMapper.hasPermission(organizationId, casId, permission)) {
            throw new BusinessException(BizCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    public void requireProjectAccess(String projectId, String accessType) {
        String organizationId = TenantContext.requireOrganizationId();
        String casId = TenantContext.requireCasId();
        if (!authorizationMapper.hasProjectAccess(organizationId, projectId, casId, accessType)) {
            throw new BusinessException(BizCode.NO_PERMISSION, HttpStatus.FORBIDDEN);
        }
    }

    public void requireTenantPath(String organizationId) {
        if (!organizationId.equals(TenantContext.requireOrganizationId())) {
            throw new BusinessException(BizCode.CROSS_CLUB_FORBIDDEN, HttpStatus.FORBIDDEN);
        }
    }
}
