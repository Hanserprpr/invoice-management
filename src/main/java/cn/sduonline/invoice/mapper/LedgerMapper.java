package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.dto.LedgerDtos.LedgerFilter;
import cn.sduonline.invoice.data.vo.LedgerInvoiceVO;
import cn.sduonline.invoice.data.vo.LedgerTotalsVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LedgerMapper {
    IPage<LedgerInvoiceVO> findInvoices(Page<?> page,
                                        @Param("organizationId") String organizationId,
                                        @Param("actorCasId") String actorCasId,
                                        @Param("unrestricted") boolean unrestricted,
                                        @Param("filter") LedgerFilter filter,
                                        @Param("sortColumn") String sortColumn,
                                        @Param("sortDirection") String sortDirection);

    LedgerTotalsVO calculateTotals(@Param("organizationId") String organizationId,
                                   @Param("actorCasId") String actorCasId,
                                   @Param("unrestricted") boolean unrestricted,
                                   @Param("filter") LedgerFilter filter);

    boolean canAccessInvoice(@Param("organizationId") String organizationId,
                             @Param("actorCasId") String actorCasId,
                             @Param("unrestricted") boolean unrestricted,
                             @Param("invoiceId") String invoiceId);
}
