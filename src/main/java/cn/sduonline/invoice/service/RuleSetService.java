package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.RuleDtos.*;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.RuleSet;
import cn.sduonline.invoice.data.po.RuleSetVersion;
import cn.sduonline.invoice.data.vo.RuleSetVO;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.RuleSetMapper;
import cn.sduonline.invoice.mapper.RuleSetVersionMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import cn.sduonline.invoice.util.UlidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RuleSetService {
    private final RuleSetMapper ruleSetMapper;
    private final RuleSetVersionMapper versionMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RuleSetService(RuleSetMapper ruleSetMapper, RuleSetVersionMapper versionMapper,
                          AuthorizationService authorizationService, AuditService auditService,
                          ObjectMapper objectMapper) {
        this.ruleSetMapper = ruleSetMapper;
        this.versionMapper = versionMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<RuleSetVO> list() {
        authorizationService.requirePermission("role:manage");
        return ruleSetMapper.findForOrganization(TenantContext.requireOrganizationId())
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public RuleSetVO create(String actorCasId, CreateRuleSetRequest request) {
        authorizationService.requirePermission("role:manage");
        String organizationId = TenantContext.requireOrganizationId();
        if (request.isDefault()) clearDefault(organizationId, null);
        RuleSet ruleSet = RuleSet.builder().id(UlidGenerator.next()).organizationId(organizationId)
                .name(request.name().trim()).isDefault(request.isDefault()).status("ACTIVE")
                .version(0L).createdByCasId(actorCasId).build();
        ruleSetMapper.insert(ruleSet);
        auditService.append(organizationId, actorCasId, "RULE_SET_CREATED", "RULE_SET",
                ruleSet.getId(), "{\"default\":" + request.isDefault() + "}");
        return toVO(ruleSet);
    }

    @Transactional
    public RuleSetVO update(String id, String actorCasId, UpdateRuleSetRequest request) {
        authorizationService.requirePermission("role:manage");
        RuleSet ruleSet = require(id);
        if (request.isDefault() != null && request.isDefault()) clearDefault(ruleSet.getOrganizationId(), id);
        if (request.name() != null) ruleSet.setName(request.name().trim());
        if (request.isDefault() != null) ruleSet.setIsDefault(request.isDefault());
        if (request.status() != null) ruleSet.setStatus(request.status());
        ruleSet.setVersion(request.version());
        if (ruleSetMapper.updateById(ruleSet) != 1) conflict();
        ruleSet.setVersion(request.version() + 1);
        auditService.append(ruleSet.getOrganizationId(), actorCasId, "RULE_SET_UPDATED", "RULE_SET",
                id, "{\"version\":" + ruleSet.getVersion() + "}");
        return toVO(ruleSet);
    }

    @Transactional
    public RuleSetVO.VersionVO publish(String id, String actorCasId,
                                       PublishRuleVersionRequest request) {
        authorizationService.requirePermission("role:manage");
        RuleSet ruleSet = require(id);
        if (!"ACTIVE".equals(ruleSet.getStatus())) {
            throw new BusinessException(BizCode.STATE_NOT_ALLOWED, HttpStatus.CONFLICT);
        }
        validateRules(request.rules());
        int next = versionMapper.maxVersion(ruleSet.getOrganizationId(), id) + 1;
        RuleSetVersion version = RuleSetVersion.builder().id(UlidGenerator.next())
                .organizationId(ruleSet.getOrganizationId()).ruleSetId(id).versionNo(next)
                .rulesJson(writeRules(request.rules())).effectiveAt(request.effectiveAt())
                .publishedByCasId(actorCasId).build();
        versionMapper.insert(version);
        auditService.append(ruleSet.getOrganizationId(), actorCasId, "RULE_SET_VERSION_PUBLISHED",
                "RULE_SET_VERSION", version.getId(), "{\"versionNo\":" + next + "}");
        return toVersionVO(version);
    }

    public RuleSetVersion requireEffectiveVersion(String versionId) {
        if (versionId == null) return null;
        RuleSetVersion version = versionMapper.findEffective(TenantContext.requireOrganizationId(),
                versionId, Instant.now());
        if (version == null) {
            throw new BusinessException(BizCode.RULE_SET_NOT_FOUND, HttpStatus.BAD_REQUEST);
        }
        return version;
    }

    private RuleSet require(String id) {
        RuleSet ruleSet = ruleSetMapper.selectOne(new LambdaQueryWrapper<RuleSet>()
                .eq(RuleSet::getOrganizationId, TenantContext.requireOrganizationId())
                .eq(RuleSet::getId, id));
        if (ruleSet == null) {
            throw new BusinessException(BizCode.RULE_SET_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return ruleSet;
    }

    private void clearDefault(String organizationId, String exceptId) {
        for (RuleSet row : ruleSetMapper.lockOrganizationRuleSets(organizationId)) {
            if (Boolean.TRUE.equals(row.getIsDefault()) && !row.getId().equals(exceptId)) {
                row.setIsDefault(false);
                ruleSetMapper.updateById(row);
            }
        }
    }

    private void validateRules(List<RuleDefinition> rules) {
        Set<String> codes = new HashSet<>();
        for (RuleDefinition rule : rules) {
            if (!codes.add(rule.code())) invalid();
            boolean amountRule = Set.of("MAX_FACE_AMOUNT", "MAX_CLAIMED_AMOUNT").contains(rule.code());
            if (amountRule != (rule.amount() != null)) invalid();
            boolean valuesRule = "ALLOWED_INVOICE_TYPES".equals(rule.code());
            if (valuesRule != (rule.values() != null && !rule.values().isEmpty())) invalid();
        }
    }

    private RuleSetVO toVO(RuleSet row) {
        List<RuleSetVO.VersionVO> versions = versionMapper
                .findForRuleSet(row.getOrganizationId(), row.getId())
                .stream().map(this::toVersionVO).toList();
        return new RuleSetVO(row.getId(), row.getName(), Boolean.TRUE.equals(row.getIsDefault()),
                row.getStatus(), row.getVersion(), row.getCreatedByCasId(), row.getCreatedAt(),
                row.getUpdatedAt(), versions);
    }

    private RuleSetVO.VersionVO toVersionVO(RuleSetVersion row) {
        return new RuleSetVO.VersionVO(row.getId(), row.getVersionNo(), readRules(row.getRulesJson()),
                row.getEffectiveAt(), row.getPublishedByCasId(), row.getPublishedAt());
    }

    List<RuleDefinition> readRules(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<RuleDefinition>>() {});
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeRules(List<RuleDefinition> rules) {
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JacksonException exception) {
            throw new BusinessException(BizCode.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void invalid() {
        throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
    }

    private void conflict() {
        throw new BusinessException(BizCode.VERSION_CONFLICT, HttpStatus.CONFLICT);
    }
}
