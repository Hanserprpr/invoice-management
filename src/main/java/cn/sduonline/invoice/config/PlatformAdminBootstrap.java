package cn.sduonline.invoice.config;

import cn.sduonline.invoice.data.po.User;
import cn.sduonline.invoice.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Optional, environment-controlled bootstrap for the first platform administrator. */
@Component
public class PlatformAdminBootstrap implements ApplicationRunner {

    private final UserMapper userMapper;
    private final String casId;
    private final String name;

    public PlatformAdminBootstrap(
            UserMapper userMapper,
            @Value("${app.bootstrap.platform-admin.cas-id:}") String casId,
            @Value("${app.bootstrap.platform-admin.name:}") String name
    ) {
        this.userMapper = userMapper;
        this.casId = casId == null ? "" : casId.trim();
        this.name = name == null ? "" : name.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(casId)) {
            return;
        }
        if (!casId.matches("^[A-Za-z0-9_-]{1,20}$") || !StringUtils.hasText(name)) {
            throw new IllegalStateException("平台管理员引导变量格式非法或缺少姓名");
        }
        User user = userMapper.selectById(casId);
        if (user == null) {
            userMapper.insert(User.builder()
                    .casId(casId)
                    .name(name)
                    .status("ACTIVE")
                    .isPlatformAdmin(true)
                    .version(0L)
                    .build());
            return;
        }
        user.setName(name);
        user.setStatus("ACTIVE");
        user.setIsPlatformAdmin(true);
        userMapper.updateById(user);
    }
}
