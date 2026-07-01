package com.quju.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IntegrationServiceModerationTest {
    private JdbcTemplate jdbc;
    private IntegrationService service;
    private MockRestServiceServer server;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:ai_moderation_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table third_party_events (id varchar(64), provider varchar(60), operation varchar(80),"
                + "status varchar(32), request_summary varchar(1000), response_summary varchar(1000), error varchar(1000),"
                + "duration_ms int, created_at timestamp default current_timestamp)");
        jdbc.execute("create table ai_audit_logs (id varchar(64), activity_id varchar(64), result varchar(32),"
                + "risk_level varchar(16), risk_labels varchar(1000), reason varchar(1000), confidence decimal(5,4),"
                + "provider varchar(60), model varchar(120), provider_status varchar(32), request_snapshot varchar(4000),"
                + "raw_response varchar(4000), error_message varchar(1000), duration_ms int,"
                + "created_at timestamp default current_timestamp)");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new IntegrationService(jdbc, (ObjectProvider<JavaMailSender>) mock(ObjectProvider.class),
                restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "aiBaseUrl", "https://ai.example/v1");
        ReflectionTestUtils.setField(service, "aiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "aiModel", "test-model");
        ReflectionTestUtils.setField(service, "aiResponseFormat", "auto");
    }

    @Test
    void publishesOnlyAValidExplicitLowRiskResult() {
        server.expect(once(), requestTo("https://ai.example/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\\\"title\\\":\\\"周末读书会\\\"")))
                .andExpect(content().string(containsString("\\\"safetyNote\\\":\\\"现场禁止吸烟\\\"")))
                .andRespond(withSuccess(response("LOW_RISK", "LOW", "未发现内容安全风险", 0.96),
                        MediaType.APPLICATION_JSON));

        IntegrationService.ModerationResult result = moderate("周末读书会");

        assertEquals("LOW_RISK", result.result);
        assertEquals(0.96D, result.confidence, 0.001D);
        assertTrue(result.labels.isEmpty());
        server.verify();
    }

    @Test
    void sendsRiskyResultsToManualReviewAndRetainsAudit() {
        server.expect(requestTo("https://ai.example/v1/chat/completions"))
                .andRespond(withSuccess(response("REVIEW_REQUIRED", "HIGH", "包含危险挑战内容", 0.94),
                        MediaType.APPLICATION_JSON));

        IntegrationService.ModerationResult result = moderate("危险挑战活动");
        service.saveAiAudit("act-risk", result);

        assertEquals("REVIEW_REQUIRED", result.result);
        assertEquals("高", result.risk);
        assertEquals("REVIEW_REQUIRED", jdbc.queryForObject(
                "select result from ai_audit_logs where activity_id = 'act-risk'", String.class));
        assertEquals("test-model", jdbc.queryForObject(
                "select model from ai_audit_logs where activity_id = 'act-risk'", String.class));
    }

    @Test
    void malformedResponsesNeverPassAutomatically() {
        server.expect(requestTo("https://ai.example/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}",
                        MediaType.APPLICATION_JSON));

        IntegrationService.ModerationResult result = moderate("普通活动");

        assertEquals("INDETERMINATE", result.result);
        assertTrue(result.labels.contains("AI_INVALID_RESPONSE"));
    }

    @Test
    void providerErrorsNeverPassAutomatically() {
        server.expect(requestTo("https://ai.example/v1/chat/completions")).andRespond(withServerError());

        IntegrationService.ModerationResult result = moderate("普通活动");

        assertEquals("ERROR", result.result);
        assertTrue(result.labels.contains("AI_SERVICE_ERROR"));
    }

    @Test
    void missingConfigurationNeverPassesAutomatically() {
        ReflectionTestUtils.setField(service, "aiApiKey", "");

        IntegrationService.ModerationResult result = moderate("普通活动");

        assertEquals("INDETERMINATE", result.result);
        assertTrue(result.labels.contains("AI_NOT_CONFIGURED"));
    }

    @Test
    void usesJsonObjectForDeepSeekCompatibleApi() {
        ReflectionTestUtils.setField(service, "aiBaseUrl", "https://api.deepseek.com");
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(content().string(containsString("\"type\":\"json_object\"")))
                .andRespond(withSuccess(response("LOW_RISK", "LOW", "未发现内容安全风险", 0.96),
                        MediaType.APPLICATION_JSON));

        IntegrationService.ModerationResult result = moderate("普通活动");

        assertEquals("LOW_RISK", result.result);
    }

    private IntegrationService.ModerationResult moderate(String title) {
        return service.moderateActivity("act-test", title, "活动简介", "活动详细描述", "学习交流",
                Arrays.asList("阅读", "交流"), "现场禁止吸烟", "杭州", 20);
    }

    private String response(String decision, String risk, String reason, double confidence) {
        String content = String.format(
                "{\\\"decision\\\":\\\"%s\\\",\\\"riskLevel\\\":\\\"%s\\\",\\\"labels\\\":[],"
                        + "\\\"reason\\\":\\\"%s\\\",\\\"confidence\\\":%.2f}",
                decision, risk, reason, confidence);
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
    }
}
