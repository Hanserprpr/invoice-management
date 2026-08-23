package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.DictionaryItem;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DictionaryItemMapper extends BaseMapper<DictionaryItem> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM dictionary_item WHERE organization_id IS NULL AND dictionary_version_id=#{versionId} ORDER BY sort_order, id")
    List<DictionaryItem> findTemplateItems(@Param("versionId") String versionId);

    @Select("""
            SELECT di.* FROM dictionary_item di
            JOIN dictionary_version dv ON dv.id=di.dictionary_version_id
              AND dv.organization_id=di.organization_id
            WHERE di.organization_id=#{organizationId} AND di.id=#{itemId}
              AND di.enabled=TRUE AND dv.status='PUBLISHED' AND dv.dictionary_type=#{dictionaryType}
            """)
    DictionaryItem findEnabledByType(@Param("organizationId") String organizationId,
                                     @Param("itemId") String itemId,
                                     @Param("dictionaryType") String dictionaryType);

    @Select("""
            SELECT di.* FROM dictionary_item di
            JOIN dictionary_version dv ON dv.id=di.dictionary_version_id
              AND dv.organization_id=di.organization_id
            WHERE di.organization_id=#{organizationId} AND dv.dictionary_type=#{dictionaryType}
              AND dv.status='PUBLISHED' AND di.enabled=TRUE
              AND dv.version_no=(SELECT MAX(latest.version_no) FROM dictionary_version latest
                WHERE latest.organization_id=#{organizationId}
                  AND latest.dictionary_type=#{dictionaryType} AND latest.status='PUBLISHED')
            ORDER BY di.sort_order,di.id
            """)
    List<DictionaryItem> findPublishedEnabled(@Param("organizationId") String organizationId,
                                               @Param("dictionaryType") String dictionaryType);
}
