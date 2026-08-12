package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.RuleDtos.RuleDefinition;
import cn.sduonline.invoice.data.po.Invoice;
import cn.sduonline.invoice.data.po.InvoicePrecheckResult;
import cn.sduonline.invoice.data.po.RuleSetVersion;
import cn.sduonline.invoice.mapper.InvoicePrecheckResultMapper;
import cn.sduonline.invoice.mapper.RuleSetVersionMapper;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class RuleExecutionService {
    private final RuleSetVersionMapper versionMapper;
    private final InvoicePrecheckResultMapper resultMapper;
    private final RuleSetService ruleSetService;

    public RuleExecutionService(RuleSetVersionMapper versionMapper,
                                InvoicePrecheckResultMapper resultMapper,
                                RuleSetService ruleSetService) {
        this.versionMapper = versionMapper;
        this.resultMapper = resultMapper;
        this.ruleSetService = ruleSetService;
    }

    public void execute(Invoice invoice, String versionId) {
        if (versionId == null) return;
        RuleSetVersion version = versionMapper.findEffective(invoice.getOrganizationId(), versionId,
                Instant.now());
        if (version == null) return;
        for (RuleDefinition rule : ruleSetService.readRules(version.getRulesJson())) {
            boolean hit = switch (rule.code()) {
                case "MAX_FACE_AMOUNT" -> invoice.getFaceAmount() != null
                        && invoice.getFaceAmount().compareTo(rule.amount()) > 0;
                case "MAX_CLAIMED_AMOUNT" -> invoice.getClaimedAmount() != null
                        && invoice.getClaimedAmount().compareTo(rule.amount()) > 0;
                case "ALLOWED_INVOICE_TYPES" -> invoice.getInvoiceType() == null
                        || !rule.values().contains(invoice.getInvoiceType());
                case "REQUIRE_SELLER_TAX_NO" -> !hasText(invoice.getSellerTaxNo());
                case "REQUIRE_BUYER_TAX_NO" -> !hasText(invoice.getBuyerTaxNo());
                default -> false;
            };
            resultMapper.insert(InvoicePrecheckResult.builder().id(UlidGenerator.next())
                    .organizationId(invoice.getOrganizationId()).invoiceId(invoice.getId())
                    .ruleSetVersionId(versionId).checkType("RULE").ruleCode(rule.code())
                    .severity(rule.severity()).result(hit ? "HIT" : "PASS")
                    .reason(hit ? reason(rule) : "规则检查通过").build());
        }
    }

    private String reason(RuleDefinition rule) {
        return switch (rule.code()) {
            case "MAX_FACE_AMOUNT" -> "票面金额超过规则上限 " + rule.amount();
            case "MAX_CLAIMED_AMOUNT" -> "申请金额超过规则上限 " + rule.amount();
            case "ALLOWED_INVOICE_TYPES" -> "发票类型不在项目允许范围内";
            case "REQUIRE_SELLER_TAX_NO" -> "缺少销售方税号";
            case "REQUIRE_BUYER_TAX_NO" -> "缺少购买方税号";
            default -> "命中规则";
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
