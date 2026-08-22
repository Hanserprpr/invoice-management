package cn.sduonline.invoice.service;

import cn.sduonline.invoice.config.IdempotencyProperties;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.po.IdempotencyRecord;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.IdempotencyRecordMapper;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class IdempotencyService {
    private final IdempotencyRecordMapper mapper;
    private final IdempotencyProperties properties;

    public IdempotencyService(IdempotencyRecordMapper mapper, IdempotencyProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public sealed interface ClaimOutcome permits Acquired, Replay {
    }

    public record Acquired(String recordId) implements ClaimOutcome {
    }

    public record Replay(int responseStatus, String responseBody) implements ClaimOutcome {
    }

    public record ClaimRequest(
            String scope,
            String organizationId,
            String actorCasId,
            String key,
            String method,
            String path,
            String requestHash
    ) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimOutcome claim(ClaimRequest request) {
        Instant now = Instant.now();
        IdempotencyRecord claim = newClaim(request, now);
        try {
            mapper.insertClaim(claim);
            return new Acquired(claim.getId());
        } catch (DuplicateKeyException exception) {
            IdempotencyRecord existing = mapper.lockByKey(
                    request.scope(), request.actorCasId(), request.key());
            if (existing == null) {
                throw conflict();
            }
            if (!existing.getExpiresAt().isAfter(now)) {
                if (mapper.deleteExpiredClaim(existing.getId(), now) != 1) {
                    throw conflict();
                }
                IdempotencyRecord replacement = newClaim(request, now);
                mapper.insertClaim(replacement);
                return new Acquired(replacement.getId());
            }
            if (!sameRequest(existing, request)) {
                throw conflict();
            }
            if ("COMPLETED".equals(existing.getStatus())
                    && existing.getResponseStatus() != null) {
                return new Replay(existing.getResponseStatus(), existing.getResponseBody());
            }
            throw conflict();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String recordId, int responseStatus, String responseBody) {
        if (mapper.complete(recordId, responseStatus, responseBody,
                Instant.now().plus(properties.getTtl())) != 1) {
            throw new IllegalStateException("IDEMPOTENCY_CLAIM_NOT_PROCESSING");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String recordId) {
        mapper.release(recordId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanupExpired(Instant now, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return mapper.deleteExpired(now, limit);
    }

    private IdempotencyRecord newClaim(ClaimRequest request, Instant now) {
        return IdempotencyRecord.builder()
                .id(UlidGenerator.next())
                .organizationId(request.organizationId())
                .scope(request.scope())
                .casId(request.actorCasId())
                .idempotencyKey(request.key())
                .requestMethod(request.method())
                .requestPath(request.path())
                .requestHash(request.requestHash())
                .status("PROCESSING")
                .createdAt(now)
                .expiresAt(now.plus(properties.getProcessingTtl()))
                .build();
    }

    private boolean sameRequest(IdempotencyRecord existing, ClaimRequest request) {
        return existing.getRequestMethod().equals(request.method())
                && existing.getRequestPath().equals(request.path())
                && existing.getRequestHash().equals(request.requestHash());
    }

    private BusinessException conflict() {
        return new BusinessException(BizCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT);
    }
}
