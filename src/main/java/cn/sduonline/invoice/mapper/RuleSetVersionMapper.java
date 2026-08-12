package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.RuleSetVersion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface RuleSetVersionMapper extends BaseMapper<RuleSetVersion> {
    @Select("SELECT COALESCE(MAX(version_no),0) FROM rule_set_version WHERE organization_id=#{organizationId} AND rule_set_id=#{ruleSetId}")
    int maxVersion(@Param("organizationId") String organizationId,
                   @Param("ruleSetId") String ruleSetId);

    @Select("SELECT * FROM rule_set_version WHERE organization_id=#{organizationId} AND rule_set_id=#{ruleSetId} ORDER BY version_no DESC")
    List<RuleSetVersion> findForRuleSet(@Param("organizationId") String organizationId,
                                       @Param("ruleSetId") String ruleSetId);

    @Select("SELECT * FROM rule_set_version WHERE organization_id=#{organizationId} AND id=#{versionId} AND effective_at<=#{now}")
    RuleSetVersion findEffective(@Param("organizationId") String organizationId,
                                 @Param("versionId") String versionId,
                                 @Param("now") Instant now);
}
