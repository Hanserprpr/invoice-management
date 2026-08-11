package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.InvoiceFileRevision;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvoiceFileRevisionMapper extends BaseMapper<InvoiceFileRevision> {
    @Select("SELECT COALESCE(MAX(revision_no),0) FROM invoice_file_revision WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId}")
    int maxRevision(@Param("organizationId") String organizationId,
                    @Param("invoiceId") String invoiceId);

    @Select("SELECT * FROM invoice_file_revision WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId} ORDER BY revision_no")
    List<InvoiceFileRevision> findForInvoice(@Param("organizationId") String organizationId,
                                             @Param("invoiceId") String invoiceId);

    @Delete("DELETE FROM invoice_file_revision WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId}")
    int deleteForInvoice(@Param("organizationId") String organizationId,
                         @Param("invoiceId") String invoiceId);
}
