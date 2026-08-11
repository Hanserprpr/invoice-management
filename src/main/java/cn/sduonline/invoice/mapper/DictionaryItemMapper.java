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
}
