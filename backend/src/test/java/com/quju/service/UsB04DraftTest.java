package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityDto;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-B04 草稿保存与提交
 *
 * 验收标准：
 * 1. 创建过程中支持随时保存草稿，保存时不强制校验必填字段
 * 2. 草稿可在"我的草稿"列表中查看、继续编辑或删除
 * 3. 草稿不会出现在首页、搜索或地图发现中
 * 4. 提交时系统校验必填字段和业务规则，通过后进入审核流程
 * 5. 重复点击提交不会创建重复审核任务
 */
class UsB04DraftTest extends TestBase {

    @Test
    @DisplayName("US-B04-1: 保存草稿时不强制校验必填字段")
    void shouldSaveDraftWithoutFullValidation() {
        ActivityCreateRequest draftReq = new ActivityCreateRequest();
        draftReq.setTitle("周末徒步计划");
        draftReq.setSummary("待补充详细信息");
        draftReq.setCategory("户外徒步");
        draftReq.setCapacity(20);

        ActivityDto draft = activityService.saveDraft(draftReq, USER_ID);
        assertEquals("草稿", draft.getStatus());
        assertEquals(USER_ID, draft.getOrganizer().getId());
    }

    @Test
    @DisplayName("US-B04-2: 草稿可在列表中查看、编辑和删除")
    void shouldListEditAndDeleteDrafts() {
        ActivityCreateRequest draftReq = new ActivityCreateRequest();
        draftReq.setTitle("徒步计划");
        draftReq.setSummary("待补充");
        draftReq.setCategory("户外徒步");
        draftReq.setCapacity(20);
        ActivityDto draft = activityService.saveDraft(draftReq, USER_ID);

        // 查看
        List<ActivityDto> drafts = activityService.findDrafts(USER_ID);
        assertEquals(1, drafts.size());
        assertEquals(draft.getId(), drafts.get(0).getId());

        // 编辑
        ActivityCreateRequest updateReq = filledDraftUpdate();
        ActivityDto updated = activityService.updateDraft(draft.getId(), updateReq, USER_ID);
        assertEquals("周末徒步计划（修改）", updated.getTitle());

        // 删除
        activityService.deleteDraft(draft.getId(), USER_ID);
        drafts = activityService.findDrafts(USER_ID);
        assertTrue(drafts.isEmpty());
    }

    @Test
    @DisplayName("US-B04-3: 草稿不展示在信息流中")
    void shouldNotShowDraftInFeed() {
        ActivityCreateRequest draftReq = new ActivityCreateRequest();
        draftReq.setTitle("隐藏草稿");
        draftReq.setCategory("城市探索");
        draftReq.setCapacity(10);
        ActivityDto draft = activityService.saveDraft(draftReq, USER_ID);

        List<ActivityDto> feed = activityService.findAll(null, null);
        boolean draftVisible = feed.stream().anyMatch(a -> a.getId().equals(draft.getId()));
        assertFalse(draftVisible);
    }

    @Test
    @DisplayName("US-B04-4: 提交时校验必填字段，通过后进入审核流程")
    void shouldValidateOnSubmitAndEnterReviewFlow() {
        ActivityCreateRequest draftReq = new ActivityCreateRequest();
        draftReq.setTitle("待提交草稿");
        draftReq.setSummary("草稿详情");
        draftReq.setCategory("户外徒步");
        draftReq.setDate("2026-07-15");
        draftReq.setTime("08:00 - 16:00");
        draftReq.setStartTime("08:00");
        draftReq.setEndTime("16:00");
        draftReq.setDeadline("2026-07-14T20:00:00");
        draftReq.setLocation("西湖群山");
        draftReq.setDistrict("西湖区");
        draftReq.setTags(Arrays.asList("徒步"));
        draftReq.setCapacity(25);
        ActivityDto draft = activityService.saveDraft(draftReq, USER_ID);

        ActivityDto submitted = activityService.submitDraft(draft.getId(), USER_ID);
        assertNotNull(submitted.getStatus());
        assertNotEquals("草稿", submitted.getStatus());
    }

    @Test
    @DisplayName("US-B04-5: 重复提交不会创建重复审核任务")
    void shouldNotDuplicateReviewOnResubmit() {
        ActivityCreateRequest draftReq = filledDraftRequest();
        ActivityDto draft = activityService.saveDraft(draftReq, USER_ID);

        ActivityDto first = activityService.submitDraft(draft.getId(), USER_ID);
        ActivityDto second = activityService.submitDraft(draft.getId(), USER_ID);
        assertEquals(first.getStatus(), second.getStatus());
    }

    private ActivityCreateRequest filledDraftRequest() {
        ActivityCreateRequest req = new ActivityCreateRequest();
        req.setTitle("重复提交测试");
        req.setSummary("测试详情");
        req.setCategory("学习交流");
        req.setDate("2026-07-20");
        req.setTime("10:00 - 12:00");
        req.setStartTime("10:00");
        req.setEndTime("12:00");
        req.setDeadline("2026-07-19T20:00:00");
        req.setLocation("测试地点");
        req.setDistrict("测试区");
        req.setTags(Arrays.asList("测试"));
        req.setCapacity(20);
        return req;
    }

    private ActivityCreateRequest filledDraftUpdate() {
        ActivityCreateRequest req = filledDraftRequest();
        req.setTitle("周末徒步计划（修改）");
        return req;
    }
}
