package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.InvoiceExportReservation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InvoiceExportReservationMapper extends BaseMapper<InvoiceExportReservation> {
    @Delete("DELETE FROM invoice_export_reservation WHERE organization_id=#{organizationId} AND batch_id=#{batchId}")
    int deleteForBatch(@Param("organizationId") String organizationId,
                       @Param("batchId") String batchId);

    @Update("UPDATE invoice_export_reservation SET batch_id=#{newBatchId} WHERE organization_id=#{organizationId} AND batch_id=#{oldBatchId}")
    int transferBatch(@Param("organizationId") String organizationId,
                      @Param("oldBatchId") String oldBatchId,
                      @Param("newBatchId") String newBatchId);
}
