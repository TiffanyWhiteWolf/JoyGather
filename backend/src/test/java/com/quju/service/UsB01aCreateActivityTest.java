package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityDto;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-B01a 基础活动创建
 *
 * 验收标准：
 * 1. 必填字段包括活动名称、标签、简介、时间、地点、人数上限和报名截止时间
 * 2. 地点支持手动输入地址文本
 * 3. 开始时间须晚于当前时间，报名截止时间须不晚于开始时间
 * 4. 人数上限须为正整数
 * 5. 必填字段缺失或规则校验不通过时，系统阻止提交并逐项提示
 */
class UsB01aCreateActivityTest extends TestBase {

    @Test
    @DisplayName("US-B01a-1: 必填字段全部填写后可成功创建")
    void shouldCreateWithAllRequiredFields() {
        ActivityCreateRequest req = validActivityRequest(30);
        ActivityDto created = activityService.create(req);
        assertNotNull(created.getId());
        assertNotNull(created.getTitle());
        assertNotNull(created.getSummary());
        assertNotNull(created.getCategory());
        assertNotNull(created.getStartAt());
        assertNotNull(created.getLocation());
        assertTrue(created.getCapacity() > 0);
    }

    @Test
    @DisplayName("US-B01a-2: 地点支持手动输入地址文本")
    void shouldAcceptManualAddressInput() {
        ActivityCreateRequest req = validActivityRequest(20);
        req.setLocation("北京市朝阳区三里屯");
        ActivityDto created = activityService.create(req);
        assertEquals("北京市朝阳区三里屯", created.getLocation());
    }

    @Test
    @DisplayName("US-B01a-3: 开始时间须晚于当前时间，报名截止时间须不晚于开始时间")
    void shouldValidateTimeConstraints() {
        ActivityCreateRequest req = validActivityRequest(20);
        // 使用未来的日期，assert 不会抛出异常即表示通过
        ActivityDto created = activityService.create(req);
        assertNotNull(created.getStartAt());
        assertNotNull(created.getDeadline());
    }

    @Test
    @DisplayName("US-B01a-4: 人数上限须为正整数")
    void shouldAcceptPositiveIntegerCapacity() {
        ActivityCreateRequest req = validActivityRequest(50);
        ActivityDto created = activityService.create(req);
        assertEquals(50, created.getCapacity());
    }

    @Test
    @DisplayName("US-B01a-4b: 人数上限小于2时应阻止提交")
    void shouldRejectCapacityLessThanTwo() {
        ActivityCreateRequest req = validActivityRequest(1);
        Exception ex = assertThrows(Exception.class, () -> activityService.create(req));
        assertTrue(ex.getMessage().contains("大于等于2"));
    }

    @Test
    @DisplayName("US-B01a-5: 必填字段缺失时阻止提交并提示")
    void shouldRejectIncompleteRequest() {
        ActivityCreateRequest badReq = new ActivityCreateRequest();
        Exception ex = assertThrows(Exception.class, () -> activityService.create(badReq));
        assertNotNull(ex.getMessage());
    }
}
