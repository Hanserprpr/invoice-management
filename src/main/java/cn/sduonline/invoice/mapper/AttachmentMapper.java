package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.Attachment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
    @Select("SELECT * FROM attachment WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId} ORDER BY created_at")
    List<Attachment> findForInvoice(@Param("organizationId") String organizationId,
                                    @Param("invoiceId") String invoiceId);

    @Delete("DELETE FROM attachment WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId}")
    int deleteForInvoice(@Param("organizationId") String organizationId,
                         @Param("invoiceId") String invoiceId);
}
