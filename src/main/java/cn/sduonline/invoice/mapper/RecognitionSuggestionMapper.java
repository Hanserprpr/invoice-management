package cn.sduonline.invoice.mapper;

import cn.sduonline.invoice.data.po.RecognitionSuggestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface RecognitionSuggestionMapper extends BaseMapper<RecognitionSuggestion> {
    @Update("""
            UPDATE recognition_suggestion SET status=#{status},final_value=#{finalValue},
              confirmed_by_cas_id=#{actorCasId},confirmed_at=#{confirmedAt}
            WHERE organization_id=#{organizationId} AND invoice_id=#{invoiceId}
              AND id=#{suggestionId} AND status='PENDING'
            """)
    int confirm(@Param("organizationId") String organizationId,
                @Param("invoiceId") String invoiceId,
                @Param("suggestionId") String suggestionId,
                @Param("status") String status,
                @Param("finalValue") String finalValue,
                @Param("actorCasId") String actorCasId,
                @Param("confirmedAt") Instant confirmedAt);
}
