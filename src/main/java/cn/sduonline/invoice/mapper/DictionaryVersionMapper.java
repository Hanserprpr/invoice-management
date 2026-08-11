package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.DictionaryVersion;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DictionaryVersionMapper extends BaseMapper<DictionaryVersion> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM dictionary_version WHERE organization_id IS NULL AND status='PUBLISHED' ORDER BY dictionary_type, version_no")
    List<DictionaryVersion> findPublishedTemplates();
}
