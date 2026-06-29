package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityDto;
import com.quju.dto.RegistrationResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityServiceTest {
    @Test
    void createsLargeActivityInManualReview() {
        ActivityService service = new ActivityService();
        ActivityCreateRequest request = request(60);

        ActivityDto created = service.create(request);

        assertEquals("审核中", created.getStatus());
        assertEquals(60, created.getCapacity());
    }

    @Test
    void promotesFirstWaitingUserAfterCancellation() {
        ActivityService service = new ActivityService();
        ActivityDto created = service.create(request(2));
        service.register(created.getId(), "user-a");
        service.register(created.getId(), "user-b");

        RegistrationResult waiting = service.register(created.getId(), "user-c");
        RegistrationResult cancelled = service.cancel(created.getId(), "user-a");

        assertEquals("候补中", waiting.getStatus());
        assertEquals(1, waiting.getQueuePosition());
        assertEquals("user-c", cancelled.getPromotedUserId());
        assertEquals(2, created.getJoined());
    }

    private ActivityCreateRequest request(int capacity) {
        ActivityCreateRequest request = new ActivityCreateRequest();
        request.setTitle("测试活动");
        request.setSummary("用于验证报名和候补流程");
        request.setCategory("学习交流");
        request.setDate("2026-07-01");
        request.setTime("19:00 - 21:00");
        request.setLocation("杭州");
        request.setDistrict("西湖区");
        request.setCapacity(capacity);
        request.setTags(Arrays.asList("测试"));
        return request;
    }
}
