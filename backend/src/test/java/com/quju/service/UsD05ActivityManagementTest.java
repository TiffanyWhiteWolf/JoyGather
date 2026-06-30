package com.quju.service;

import com.quju.dto.ActivityDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-D05 活动管理（后台）
 */
class UsD05ActivityManagementTest extends TestBase {

    @Test
    @DisplayName("US-D05-1: 可按活动名称、状态、发起人搜索和筛选活动")
    void shouldSearchAdminActivitiesByNameStatusAndOrganizer() {
        ActivityDto created = activityService.create(validActivityRequest(12), USER_ID);

        assertTrue(adminService.activities(created.getTitle(), "报名中").stream()
                .anyMatch(item -> created.getId().equals(item.getId())));
        assertTrue(adminService.activities("小满", "报名中").stream()
                .anyMatch(item -> created.getId().equals(item.getId())));
        assertTrue(adminService.activities("", "审核中").stream()
                .noneMatch(item -> created.getId().equals(item.getId())));
    }

    @Test
    @DisplayName("US-D05-2/3/4/5: 可查看完整信息，下架需原因，下架后不可展示报名，可恢复")
    void shouldOfflineAndRestoreActivity() {
        ActivityDto created = activityService.create(validActivityRequest(10), USER_ID);

        ActivityDto detail = adminService.activityDetail(created.getId());
        assertEquals(created.getDescription(), detail.getDescription());
        assertNotNull(detail.getOrganizer());
        assertEquals("请穿舒适的运动鞋", detail.getSafetyNote());

        assertThrows(IllegalStateException.class,
                () -> activityService.takeOffline(created.getId(), " ", ADMIN_ID));

        activityService.takeOffline(created.getId(), "包含违规宣传", ADMIN_ID);
        assertEquals("已下架", adminService.activityDetail(created.getId()).getStatus());
        assertEquals("包含违规宣传", adminService.activityDetail(created.getId()).getOfflineReason());
        assertTrue(activityService.findAll("", "").stream().noneMatch(item -> created.getId().equals(item.getId())));
        assertThrows(IllegalStateException.class,
                () -> activityService.register(created.getId(), "user-a"));

        activityService.restore(created.getId(), ADMIN_ID);
        assertEquals("报名中", adminService.activityDetail(created.getId()).getStatus());
        List<ActivityDto> publicActivities = activityService.findAll("", "");
        assertTrue(publicActivities.stream().anyMatch(item -> created.getId().equals(item.getId())));
    }
}
