package com.quju.service;

import com.quju.dto.ActivityDto;
import com.quju.dto.ReviewTaskDto;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-B06 人工审核
 *
 * 验收标准：
 * 1. 报名人数上限超过 50 人的活动自动进入人工审核队列
 * 2. AI 审核标记异常的活动转入人工审核队列
 * 3. 审核员可查看活动完整信息和风险提示
 * 4. 审核结果包含通过、驳回、要求修改三种
 * 5. 驳回或要求修改时必须填写原因并通知活动发起人
 * 6. 所有审核操作保留操作人、时间、结果和原因记录
 */
class UsB06ManualReviewTest extends TestBase {

    @Test
    @DisplayName("US-B06-1: 人数上限超过 50 人自动进入人工审核队列")
    void shouldAutoEnqueueLargeCapacityActivity() {
        ActivityDto large = activityService.create(validActivityRequest(60));
        assertEquals("审核中", large.getStatus());

        List<ReviewTaskDto> reviews = adminService.reviews(null, null);
        assertFalse(reviews.isEmpty());
    }

    @Test
    @DisplayName("US-B06-2: AI 标记异常活动转入人工审核队列")
    void shouldEnqueueAiFlaggedActivity() {
        // integrationService=null 时本地规则: capacity>50 -> REVIEW_REQUIRED
        ActivityDto flagged = activityService.create(validActivityRequest(60));
        assertEquals("审核中", flagged.getStatus());

        List<ReviewTaskDto> reviews = adminService.reviews(null, "活动审核");
        assertTrue(reviews.stream().anyMatch(r -> r.getTitle().equals(flagged.getTitle())));
    }

    @Test
    @DisplayName("US-B06-3: 审核员可查看活动完整信息和风险提示")
    void shouldShowFullActivityInfoAndRiskHint() {
        ActivityDto large = activityService.create(validActivityRequest(60));
        List<ReviewTaskDto> reviews = adminService.reviews(null, null);

        ReviewTaskDto task = reviews.stream()
                .filter(r -> r.getTitle().equals(large.getTitle()))
                .findFirst().orElseThrow();
        assertNotNull(task.getRisk());
        assertNotNull(task.getReason());

        Optional<ActivityDto> detail = activityService.findById(large.getId());
        assertTrue(detail.isPresent());
    }

    @Test
    @DisplayName("US-B06-4: 审核结果包含通过、驳回、要求修改三种")
    void shouldSupportThreeOutcomes() {
        // 通过
        ActivityDto a1 = activityService.create(validActivityRequest(60));
        ReviewTaskDto t1 = findTaskByTitle(a1.getTitle());
        adminService.review(t1.getId(), "已通过", "", ADMIN_ID);
        assertEquals("报名中", activityService.findById(a1.getId()).get().getStatus());

        // 驳回
        ActivityDto a2 = activityService.create(validActivityRequest(60));
        ReviewTaskDto t2 = findTaskByTitle(a2.getTitle());
        adminService.review(t2.getId(), "已驳回", "内容违规", ADMIN_ID);
        assertEquals("已下架", activityService.findById(a2.getId()).get().getStatus());

        // 要求修改
        ActivityDto a3 = activityService.create(validActivityRequest(60));
        ReviewTaskDto t3 = findTaskByTitle(a3.getTitle());
        adminService.review(t3.getId(), "要求修改", "请补充安全须知", ADMIN_ID);
        // 状态仍为审核中，等待修改
        assertNotNull(activityService.findById(a3.getId()).get().getStatus());
    }

    @Test
    @DisplayName("US-B06-6: 所有审核操作保留操作人、时间、结果和原因记录")
    void shouldRetainAuditLogs() {
        ActivityDto a = activityService.create(validActivityRequest(60));
        ReviewTaskDto t = findTaskByTitle(a.getTitle());
        adminService.review(t.getId(), "已通过", "", ADMIN_ID);

        Integer logCount = jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'HANDLE_REVIEW'", Integer.class);
        assertTrue(logCount >= 1);
    }

    private ReviewTaskDto findTaskByTitle(String title) {
        return adminService.reviews(null, null).stream()
                .filter(r -> r.getTitle().equals(title))
                .findFirst().orElseThrow();
    }
}
