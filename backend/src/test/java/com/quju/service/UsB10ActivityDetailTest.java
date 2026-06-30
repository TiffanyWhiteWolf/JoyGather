package com.quju.service;

import com.quju.dto.ActivityDto;
import com.quju.dto.RegistrationResult;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-B10 活动详情页
 *
 * 验收标准：
 * 1. 详情页展示活动名称、标签、简介、时间、地点、人数上限/已报名人数、发起人信息和安全须知
 * 2. 活动状态正确显示：未开始、报名中、截止、活动中、已结束
 * 3. 状态随时间和业务动作自动变化
 * 4. 不同状态下展示对应的操作入口
 * 5. 已下架或不存在的活动提示不可访问
 */
class UsB10ActivityDetailTest extends TestBase {

    @Test
    @DisplayName("US-B10-1: 详情页展示完整活动信息")
    void shouldShowFullActivityDetail() {
        ActivityDto created = activityService.create(validActivityRequest(30));
        Optional<ActivityDto> detail = activityService.findById(created.getId());
        assertTrue(detail.isPresent());

        ActivityDto d = detail.get();
        assertNotNull(d.getTitle());
        assertNotNull(d.getTags());
        assertNotNull(d.getSummary());
        assertNotNull(d.getStartAt());
        assertNotNull(d.getLocation());
        assertTrue(d.getCapacity() > 0);
        assertTrue(d.getJoined() >= 0);
        assertNotNull(d.getOrganizer());
        assertNotNull(d.getSafetyNote());
    }

    @Test
    @DisplayName("US-B10-2: 活动状态正确显示")
    void shouldShowCorrectStatus() {
        ActivityDto created = activityService.create(validActivityRequest(30));
        String status = activityService.findById(created.getId()).get().getStatus();
        assertTrue(Arrays.asList("报名中", "审核中").contains(status));
    }

    @Test
    @DisplayName("US-B10-4: 不同状态下展示对应操作入口")
    void shouldShowAppropriateActionsByStatus() {
        // 报名中状态 -> 可报名
        ActivityDto open = activityService.create(validActivityRequest(30));
        if ("报名中".equals(open.getStatus())) {
            RegistrationResult reg = activityService.register(open.getId(), "user-a");
            assertEquals("已报名", reg.getStatus());
        }

        // 已下架状态 -> 不可报名
        ActivityDto offline = activityService.create(validActivityRequest(30));
        activityService.takeOffline(offline.getId(), "违规", ADMIN_ID);
        assertEquals("已下架", activityService.findById(offline.getId()).get().getStatus());
    }

    @Test
    @DisplayName("US-B10-5: 已下架或不存在的活动提示不可访问")
    void shouldShowInaccessibleForOfflineOrNonexistent() {
        // 已下架活动仍可通过 id 查到但状态为已下架
        ActivityDto created = activityService.create(validActivityRequest(30));
        activityService.takeOffline(created.getId(), "违规", ADMIN_ID);
        Optional<ActivityDto> after = activityService.findById(created.getId());
        assertTrue(after.isPresent());
        assertEquals("已下架", after.get().getStatus());

        // 不存在的活动
        Optional<ActivityDto> nonexistent = activityService.findById("nonexistent-id");
        assertFalse(nonexistent.isPresent());
    }
}
