package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.InvoicePrecheckResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InvoicePrecheckResultMapper extends BaseMapper<InvoicePrecheckResult> {
    @Select("""
            SELECT COUNT(*) FROM invoice_precheck_result
            WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId}
              AND severity='BLOCK' AND result='HIT'
              AND (resolution IS NULL OR resolution NOT IN ('FALSE_POSITIVE','ACCEPTED_RISK'))
            """)
    int countUnresolvedBlocks(@Param("organizationId") String organizationId,
                              @Param("invoiceId") String invoiceId);
}
