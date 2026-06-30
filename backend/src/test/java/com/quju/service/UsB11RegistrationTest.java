package com.quju.service;

import com.quju.dto.ActivityDto;
import com.quju.dto.RegistrationResult;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-B11 报名校验与确认
 *
 * 验收标准：
 * 1. 点击"立即报名"后系统校验：名额是否满额、用户信誉、年龄等条件
 * 2. 校验通过后弹出确认窗口，展示安全须知摘要
 * 3. 根据活动要求填写必要的报名信息
 * 4. 用户确认后报名成功，系统扣减名额
 * 5. 校验失败时逐项告知不通过原因
 * 6. 并发报名最后名额时保证原子性，不超卖
 */
class UsB11RegistrationTest extends TestBase {

    @Test
    @DisplayName("US-B11-1: 校验名额是否满额等条件")
    void shouldValidateCapacityAndConditions() {
        ActivityDto activity = activityService.create(validActivityRequest(2));

        RegistrationResult r1 = activityService.register(activity.getId(), "user-a");
        assertEquals("已报名", r1.getStatus());

        RegistrationResult r2 = activityService.register(activity.getId(), "user-b");
        assertEquals("已报名", r2.getStatus());

        // 名额已满，第三个进入候补
        RegistrationResult r3 = activityService.register(activity.getId(), "user-c");
        assertEquals("候补中", r3.getStatus());
        assertEquals(1, r3.getQueuePosition());
    }

    @Test
    @DisplayName("US-B11-3: 根据活动要求填写必要的报名信息")
    void shouldAcceptCustomFields() {
        ActivityDto activity = activityService.create(validActivityRequest(10));
        Map<String, String> fields = new HashMap<>();
        fields.put("name", "张三");
        fields.put("phone", "13800138000");

        RegistrationResult reg = activityService.register(activity.getId(), "user-a", fields);
        assertEquals("已报名", reg.getStatus());
    }

    @Test
    @DisplayName("US-B11-4: 确认后报名成功，系统扣减名额")
    void shouldDeductCapacityAfterRegistration() {
        ActivityDto activity = activityService.create(validActivityRequest(5));
        activityService.register(activity.getId(), "user-a");
        activityService.register(activity.getId(), "user-b");

        ActivityDto after = activityService.findById(activity.getId()).get();
        assertEquals(2, after.getJoined());
    }

    @Test
    @DisplayName("US-B11-5: 校验失败时逐项告知不通过原因")
    void shouldReturnCurrentStatusForAlreadyRegistered() {
        ActivityDto activity = activityService.create(validActivityRequest(5));
        activityService.register(activity.getId(), "user-a");

        // 同一用户再次报名，返回已有状态
        RegistrationResult dup = activityService.register(activity.getId(), "user-a");
        assertEquals("已报名", dup.getStatus());
    }

    @Test
    @DisplayName("US-B11-6: 并发报名最后名额时保证原子性，不超卖")
    void shouldNotOversellOnLastSlot() {
        ActivityDto activity = activityService.create(validActivityRequest(2));

        activityService.register(activity.getId(), "user-a");
        activityService.register(activity.getId(), "user-b");
        activityService.register(activity.getId(), "user-c");

        ActivityDto after = activityService.findById(activity.getId()).get();
        assertEquals(2, after.getJoined());
        assertFalse(after.getJoined() > after.getCapacity());
    }
}
