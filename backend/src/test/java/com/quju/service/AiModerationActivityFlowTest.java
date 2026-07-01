package com.quju.service;

import com.quju.dto.ActivityDto;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiModerationActivityFlowTest extends TestBase {

    @Test
    void lowRiskResultPublishesActivity() {
        IntegrationService integration = mock(IntegrationService.class);
        when(integration.moderateActivity(anyString(), anyString(), anyString(), nullable(String.class), anyString(),
                any(), anyString(), anyString(), anyInt()))
                .thenReturn(new IntegrationService.ModerationResult(
                        "LOW_RISK", "低", "未发现内容安全风险", Arrays.<String>asList()));
        doNothing().when(integration).saveAiAudit(anyString(), any(IntegrationService.ModerationResult.class));
        ActivityService securedService = new ActivityService(jdbc, userService, integration);

        ActivityDto activity = securedService.create(validActivityRequest(20));

        assertEquals("报名中", activity.getStatus());
        assertEquals("LOW_RISK", activity.getAiReviewStatus());
        assertNotNull(activity.getPublishedAt());
    }

    @Test
    void errorsGoToManualReviewWithoutPublishing() {
        IntegrationService integration = mock(IntegrationService.class);
        when(integration.moderateActivity(anyString(), anyString(), anyString(), nullable(String.class), anyString(),
                any(), anyString(), anyString(), anyInt()))
                .thenReturn(new IntegrationService.ModerationResult(
                        "ERROR", "中", "AI 服务异常，已转人工审核", Arrays.asList("AI_SERVICE_ERROR")));
        doNothing().when(integration).saveAiAudit(anyString(), any(IntegrationService.ModerationResult.class));
        ActivityService securedService = new ActivityService(jdbc, userService, integration);

        ActivityDto activity = securedService.create(validActivityRequest(20));

        assertEquals("审核中", activity.getStatus());
        assertEquals("ERROR", activity.getAiReviewStatus());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from review_tasks where target_id = ?", Integer.class, activity.getId()));
    }
}
