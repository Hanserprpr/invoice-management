package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.Invoice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {
    @Delete("DELETE FROM invoice WHERE organization_id=#{organizationId} AND id=#{invoiceId} AND status='DRAFT' AND version=#{version}")
    int deleteDraft(@Param("organizationId") String organizationId,
                    @Param("invoiceId") String invoiceId,
                    @Param("version") long version);
}
