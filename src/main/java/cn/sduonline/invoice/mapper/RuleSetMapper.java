package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.RuleSet;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RuleSetMapper extends BaseMapper<RuleSet> {
    @Select("SELECT * FROM rule_set WHERE organization_id=#{organizationId} ORDER BY is_default DESC,created_at,id")
    List<RuleSet> findForOrganization(@Param("organizationId") String organizationId);

    @Select("SELECT * FROM rule_set WHERE organization_id=#{organizationId} FOR UPDATE")
    List<RuleSet> lockOrganizationRuleSets(@Param("organizationId") String organizationId);
}
