package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.SavedInvoiceFilter;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;

@Mapper
public interface SavedInvoiceFilterMapper extends BaseMapper<SavedInvoiceFilter> {
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT cas_id FROM `user` WHERE cas_id=#{ownerCasId} FOR UPDATE")
    String lockOwner(@Param("ownerCasId") String ownerCasId);

    @Update("""
            UPDATE saved_invoice_filter SET is_default=FALSE
            WHERE organization_id=#{organizationId} AND owner_cas_id=#{ownerCasId}
              AND is_default=TRUE AND (#{exceptId} IS NULL OR id!=#{exceptId})
            """)
    int clearOtherDefaults(@Param("organizationId") String organizationId,
                           @Param("ownerCasId") String ownerCasId,
                           @Param("exceptId") String exceptId);

    @Delete("""
            DELETE FROM saved_invoice_filter
            WHERE organization_id=#{organizationId} AND owner_cas_id=#{ownerCasId}
              AND id=#{filterId} AND version=#{version}
            """)
    int deleteOwnedVersion(@Param("organizationId") String organizationId,
                           @Param("ownerCasId") String ownerCasId,
                           @Param("filterId") String filterId,
                           @Param("version") long version);
}
