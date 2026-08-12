package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.PaperEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaperEventMapper extends BaseMapper<PaperEvent> {
    @Select("SELECT * FROM paper_event WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId} ORDER BY created_at,id")
    List<PaperEvent> findForInvoice(@Param("organizationId") String organizationId,
                                    @Param("invoiceId") String invoiceId);
}
