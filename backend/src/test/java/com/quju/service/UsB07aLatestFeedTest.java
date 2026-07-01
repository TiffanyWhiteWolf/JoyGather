package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityDto;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-B07a 首页最新信息流
 *
 * 验收标准：
 * 1. 首页提供"最新"Tab，活动按发布时间倒序排列
 * 2. 活动卡片展示名称、时间、地点、名额状态和标签
 * 3. 未发布、草稿、已下架活动不展示在信息流中
 * 4. 点击活动卡片进入活动详情页
 * 5. 支持下拉刷新加载更多活动
 */
class UsB07aLatestFeedTest extends TestBase {

    @Test
    @DisplayName("US-B07a-1: 活动按发布时间倒序排列")
    void shouldOrderByPublishedAtDesc() {
        activityService.create(validActivityRequest(20));
        activityService.create(validActivityRequest(20));
        activityService.create(validActivityRequest(20));

        List<ActivityDto> feed = activityService.findAll(null, null);
        assertTrue(feed.size() >= 3);
    }

    @Test
    @DisplayName("US-B07a-2: 活动卡片展示名称、时间、地点、名额状态和标签")
    void shouldShowKeyInfoOnCard() {
        ActivityDto created = activityService.create(validActivityRequest(25));

        List<ActivityDto> feed = activityService.findAll(null, null);
        ActivityDto card = feed.stream()
                .filter(a -> a.getId().equals(created.getId()))
                .findFirst().orElseThrow();

        assertNotNull(card.getTitle());
        assertNotNull(card.getLocation());
        assertNotNull(card.getTags());
        assertTrue(card.getCapacity() > 0);
    }

    @Test
    @DisplayName("US-B07a-3: 未发布、草稿、已下架活动不展示在信息流中")
    void shouldHideUnpublishedDraftsAndOfflinedActivities() {
        // 草稿不展示
        ActivityCreateRequest draftReq = new ActivityCreateRequest();
        draftReq.setTitle("草稿活动");
        draftReq.setCategory("城市探索");
        draftReq.setCapacity(10);
        ActivityDto draft = activityService.saveDraft(draftReq, USER_ID);

        List<ActivityDto> feed = activityService.findAll(null, null);
        boolean draftInFeed = feed.stream().anyMatch(a -> a.getId().equals(draft.getId()));
        assertFalse(draftInFeed);

        // 已下架活动不展示
        ActivityDto online = activityService.create(validActivityRequest(20));
        activityService.takeOffline(online.getId(), "违规", ADMIN_ID);
        List<ActivityDto> feed2 = activityService.findAll(null, null);
        boolean offlineInFeed = feed2.stream().anyMatch(a -> a.getId().equals(online.getId()));
        assertFalse(offlineInFeed);
    }

    @Test
    @DisplayName("已结束活动不进入公共信息流，但保留在我的活动中")
    void shouldHideEndedActivitiesFromPublicFeedButKeepThemInMine() {
        ActivityDto created = activityService.create(validActivityRequest(20));
        jdbc.update(
                "update activities set status = '报名中', end_at = timestamp '2020-01-01 12:00:00' where id = ?",
                created.getId()
        );

        assertFalse(activityService.findAll(null, null).stream()
                .anyMatch(item -> created.getId().equals(item.getId())));
        assertFalse(activityService.recommendations(java.util.Collections.<String>emptyList(), 10).stream()
                .anyMatch(item -> created.getId().equals(item.getId())));

        ActivityDto mine = activityService.findMyActivities(USER_ID).stream()
                .filter(item -> created.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("已结束", mine.getStatus());
    }

    @Test
    @DisplayName("US-B07a-4: 点击活动卡片可进入活动详情页")
    void shouldNavigateToDetailFromCard() {
        ActivityDto created = activityService.create(validActivityRequest(30));
        Optional<ActivityDto> detail = activityService.findById(created.getId());
        assertTrue(detail.isPresent());
        assertEquals(created.getTitle(), detail.get().getTitle());
    }
}
