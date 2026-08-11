package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.InvoiceTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InvoiceTagMapper {
    @Select("SELECT tag_item_id FROM invoice_tag WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId} ORDER BY tag_item_id")
    List<String> findTagIds(@Param("organizationId") String organizationId,
                            @Param("invoiceId") String invoiceId);

    @Delete("DELETE FROM invoice_tag WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId}")
    int deleteForInvoice(@Param("organizationId") String organizationId,
                         @Param("invoiceId") String invoiceId);

    @Insert("""
            INSERT INTO invoice_tag(invoice_id,organization_id,tag_item_id,added_by_cas_id)
            VALUES(#{invoiceId},#{organizationId},#{tagItemId},#{addedByCasId})
            """)
    int insert(InvoiceTag tag);
}
