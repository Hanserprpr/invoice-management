package cn.sduonline.invoice;

import cn.sduonline.invoice.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.oidc.client-id=test-client-id",
        "app.security.oidc.client-secret=test-client-secret",
        "app.idempotency.ttl=5m",
        "app.idempotency.cleanup-interval=1h"
})
@AutoConfigureMockMvc
@Import(IdempotencyIntegrationTests.EndpointConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".*_test.*")
class IdempotencyIntegrationTests {
    private static final String ORGANIZATION_ID = "01KAP000000000000000000099";
    private static final String MEMBER_ID = "01KAP000000000000000000199";
    private static final String ACTOR = "idem-user";
    private static final String OTHER_ACTOR = "idem-other";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CountingEndpoint endpoint;
    @Autowired IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)).endsWith("_test");
        jdbcTemplate.update("DELETE FROM idempotency_record WHERE cas_id IN (?,?)", ACTOR, OTHER_ACTOR);
        jdbcTemplate.update("DELETE FROM organization_member WHERE id=?", MEMBER_ID);
        jdbcTemplate.update("DELETE FROM organization WHERE id=?", ORGANIZATION_ID);
        jdbcTemplate.update("DELETE FROM `user` WHERE cas_id IN (?,?)", ACTOR, OTHER_ACTOR);
        jdbcTemplate.update("""
                INSERT INTO `user`(cas_id,name,status,is_platform_admin)
                VALUES (?,?,'ACTIVE',TRUE),(?,?,'ACTIVE',TRUE)
                """, ACTOR, "幂等测试用户", OTHER_ACTOR, "另一个幂等测试用户");
        jdbcTemplate.update("INSERT INTO organization(id,name,type,status) VALUES (?,?,'CLUB','ACTIVE')",
                ORGANIZATION_ID, "幂等测试社团");
        jdbcTemplate.update("""
                INSERT INTO organization_member(id,organization_id,cas_id,status)
                VALUES (?,?,?,'ACTIVE')
                """, MEMBER_ID, ORGANIZATION_ID, ACTOR);
        endpoint.reset();
    }

    @Test
    void sameOrganizationRequestReplaysStatusAndBodyWithoutExecutingAgain() throws Exception {
        MvcResult first = organizationPost("replay-key", "/api/idempotency-test", "{\"value\":\"first\"}", ACTOR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execution").value(1))
                .andReturn();

        MvcResult replay = organizationPost("replay-key", "/api/idempotency-test", "{\"value\":\"first\"}", ACTOR)
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(replay.getResponse().getContentAsByteArray())
                .isEqualTo(first.getResponse().getContentAsByteArray());
        assertThat(endpoint.organizationExecutions()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT scope,organization_id,status,response_status
                FROM idempotency_record WHERE cas_id=? AND idempotency_key=?
                """, ACTOR, "replay-key"))
                .containsEntry("scope", "ORG:" + ORGANIZATION_ID)
                .containsEntry("organization_id", ORGANIZATION_ID)
                .containsEntry("status", "COMPLETED")
                .containsEntry("response_status", 201);
    }

    @Test
    void changedMethodBodyOrPathAndProcessingClaimReturnConflict() throws Exception {
        organizationPost("payload-key", "/api/idempotency-test", "{\"value\":\"one\"}", ACTOR)
                .andExpect(status().isCreated());

        organizationPost("payload-key", "/api/idempotency-test", "{\"value\":\"two\"}", ACTOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(10006));
        organizationPost("payload-key", "/api/idempotency-test/other", "{\"value\":\"one\"}", ACTOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(10006));
        organizationPut("payload-key", "/api/idempotency-test", "{\"value\":\"one\"}", ACTOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(10006));
        assertThat(endpoint.organizationExecutions()).isEqualTo(1);

        jdbcTemplate.update("""
                INSERT INTO idempotency_record(
                  id,organization_id,scope,cas_id,idempotency_key,request_method,
                  request_path,request_hash,status,created_at,expires_at)
                VALUES (?,?,?,?,'processing-key','POST','/api/idempotency-test',
                  SHA2('{"value":"busy"}',256),'PROCESSING',CURRENT_TIMESTAMP(3),
                  DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 1 HOUR))
                """, "01KAP000000000000000000299", ORGANIZATION_ID,
                "ORG:" + ORGANIZATION_ID, ACTOR);

        organizationPost("processing-key", "/api/idempotency-test", "{\"value\":\"busy\"}", ACTOR)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(10006));
        assertThat(endpoint.organizationExecutions()).isEqualTo(1);
    }

    @Test
    void globalScopeIsSeparatedByActorAndMissingKeyBypassesIdempotency() throws Exception {
        MvcResult first = globalPost("global-key", ACTOR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execution").value(1))
                .andReturn();
        MvcResult replay = globalPost("global-key", ACTOR)
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(replay.getResponse().getContentAsByteArray())
                .isEqualTo(first.getResponse().getContentAsByteArray());

        globalPost("global-key", OTHER_ACTOR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execution").value(2));

        mockMvc.perform(post("/api/platform/idempotency-test")
                        .with(user(ACTOR)).with(csrf())
                        .contentType("application/json")
                        .content("{\"value\":\"without-key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execution").value(3));
        mockMvc.perform(post("/api/platform/idempotency-test")
                        .with(user(ACTOR)).with(csrf())
                        .contentType("application/json")
                        .content("{\"value\":\"without-key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execution").value(4));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM idempotency_record
                WHERE scope='GLOBAL' AND organization_id IS NULL AND idempotency_key='global-key'
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void handledServerErrorIsReplayedBecauseItsResponseIsKnown() throws Exception {
        MvcResult first = organizationPost(
                "failure-key", "/api/idempotency-test/failure", "{\"value\":\"retry\"}", ACTOR)
                .andExpect(status().isInternalServerError())
                .andReturn();
        MvcResult replay = organizationPost(
                "failure-key", "/api/idempotency-test/failure", "{\"value\":\"retry\"}", ACTOR)
                .andExpect(status().isInternalServerError())
                .andReturn();

        assertThat(replay.getResponse().getContentAsByteArray())
                .isEqualTo(first.getResponse().getContentAsByteArray());
        assertThat(endpoint.failureExecutions()).isOne();
        assertThat(jdbcTemplate.queryForMap("""
                SELECT status,response_status FROM idempotency_record
                WHERE cas_id=? AND idempotency_key='failure-key'
                """, ACTOR))
                .containsEntry("status", "COMPLETED")
                .containsEntry("response_status", 500);
    }

    @Test
    void expiredClaimsCanBeReclaimedAndCleanupIsBoundedToExpiredRows() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO idempotency_record(
                  id,organization_id,scope,cas_id,idempotency_key,request_method,
                  request_path,request_hash,response_status,response_body,status,created_at,expires_at)
                VALUES
                  ('01KAP000000000000000000399',NULL,'GLOBAL',?,'reclaim-key','POST',
                   '/api/platform/idempotency-test',SHA2('{}',256),201,'{}','COMPLETED',
                   DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 HOUR),
                   DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 HOUR)),
                  ('01KAP000000000000000000499',NULL,'GLOBAL',?,'cleanup-key','POST',
                   '/api/platform/idempotency-test',SHA2('{}',256),201,'{}','COMPLETED',
                   DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 2 HOUR),
                   DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 HOUR)),
                  ('01KAP000000000000000000599',NULL,'GLOBAL',?,'active-key','POST',
                   '/api/platform/idempotency-test',SHA2('{}',256),201,'{}','COMPLETED',
                   CURRENT_TIMESTAMP(3),DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL 1 HOUR))
                """, ACTOR, ACTOR, ACTOR);

        globalPost("reclaim-key", ACTOR)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.execution").value(1));

        assertThat(idempotencyService.cleanupExpired(Instant.now(), 100)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM idempotency_record WHERE cas_id=? AND idempotency_key='active-key'
                """, Integer.class, ACTOR)).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM idempotency_record WHERE cas_id=? AND idempotency_key='reclaim-key'
                """, Integer.class, ACTOR)).isOne();
    }

    private org.springframework.test.web.servlet.ResultActions organizationPost(
            String key, String path, String body, String actor) throws Exception {
        return mockMvc.perform(post(path)
                .header("X-Organization-Id", ORGANIZATION_ID)
                .header("Idempotency-Key", key)
                .with(user(actor)).with(csrf())
                .contentType("application/json")
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions globalPost(String key, String actor)
            throws Exception {
        return mockMvc.perform(post("/api/platform/idempotency-test")
                .header("Idempotency-Key", key)
                .with(user(actor)).with(csrf())
                .contentType("application/json")
                .content("{\"value\":\"global\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions organizationPut(
            String key, String path, String body, String actor) throws Exception {
        return mockMvc.perform(put(path)
                .header("X-Organization-Id", ORGANIZATION_ID)
                .header("Idempotency-Key", key)
                .with(user(actor)).with(csrf())
                .contentType("application/json")
                .content(body));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EndpointConfiguration {
        @Bean
        CountingEndpoint countingEndpoint() {
            return new CountingEndpoint();
        }
    }

    @RestController
    static class CountingEndpoint {
        private final AtomicInteger organizationExecutions = new AtomicInteger();
        private final AtomicInteger globalExecutions = new AtomicInteger();
        private final AtomicInteger failureExecutions = new AtomicInteger();

        @PostMapping({"/api/idempotency-test", "/api/idempotency-test/other"})
        ResponseEntity<Map<String, Object>> organization(@RequestBody Map<String, Object> body) {
            return organizationResponse(body);
        }

        @PutMapping("/api/idempotency-test")
        ResponseEntity<Map<String, Object>> organizationPut(@RequestBody Map<String, Object> body) {
            return organizationResponse(body);
        }

        @PostMapping("/api/idempotency-test/failure")
        ResponseEntity<Void> fail() {
            failureExecutions.incrementAndGet();
            throw new IllegalStateException("test failure");
        }

        private ResponseEntity<Map<String, Object>> organizationResponse(Map<String, Object> body) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "execution", organizationExecutions.incrementAndGet(),
                    "value", body.get("value")));
        }

        @PostMapping("/api/platform/idempotency-test")
        ResponseEntity<Map<String, Object>> global(@RequestBody Map<String, Object> body) {
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "execution", globalExecutions.incrementAndGet(),
                    "value", body.get("value")));
        }

        void reset() {
            organizationExecutions.set(0);
            globalExecutions.set(0);
            failureExecutions.set(0);
        }

        int organizationExecutions() {
            return organizationExecutions.get();
        }

        int failureExecutions() {
            return failureExecutions.get();
        }
    }
}
