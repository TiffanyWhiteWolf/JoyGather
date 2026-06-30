package com.quju.service;

import com.quju.dto.ActivityOpsDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsB15ActivitySummaryTest extends TestBase {
    @Test
    void organizerConfirmsCategoriesBeforePublishingAndCanUpdateSummary() {
        insertEndedActivity("ended-summary", USER_ID, LocalDateTime.now().minusDays(1));
        ActivityOpsDtos.SummaryClassifyRequest classify = new ActivityOpsDtos.SummaryClassifyRequest();
        classify.setImageUrls(Arrays.asList("/one.jpg", "/two.jpg"));
        ActivityOpsDtos.SummaryClassificationDto classified = activityService.classifySummaryImages("ended-summary", USER_ID, classify);
        assertEquals(2, classified.getCategories().size());
        assertFalse(classified.isAiAvailable());

        ActivityOpsDtos.SummaryRequest request = new ActivityOpsDtos.SummaryRequest();
        request.setTitle("我们的夏日回忆");
        request.setContent("这是一次非常难忘的活动。");
        request.setImageUrls(classify.getImageUrls());
        request.setConfirmedCategories(Arrays.asList("合影", "成果展示"));
        ActivityOpsDtos.SummaryDto summary = activityService.publishSummary("ended-summary", USER_ID, request);

        assertEquals(2, summary.getImages().size());
        assertEquals("成果展示", summary.getImages().get(1).getConfirmedCategory());
        request.setTitle("更新后的总结");
        activityService.publishSummary("ended-summary", USER_ID, request);
        assertEquals(1, jdbc.queryForObject("select count(*) from activity_summaries where activity_id='ended-summary'", Integer.class));
    }

    @Test
    void rejectsUnconfirmedImagesAndUnfinishedActivity() {
        insertEndedActivity("ended-unconfirmed", USER_ID, LocalDateTime.now().minusDays(1));
        ActivityOpsDtos.SummaryRequest request = new ActivityOpsDtos.SummaryRequest();
        request.setTitle("标题"); request.setContent("正文"); request.setImageUrls(Arrays.asList("/one.jpg"));
        assertThrows(IllegalStateException.class, () -> activityService.publishSummary("ended-unconfirmed", USER_ID, request));

        insertEndedActivity("not-ended", USER_ID, LocalDateTime.now().plusDays(1));
        request.setConfirmedCategories(Arrays.asList("合影"));
        assertThrows(IllegalStateException.class, () -> activityService.publishSummary("not-ended", USER_ID, request));
    }

    private void insertEndedActivity(String id, String organizerId, LocalDateTime endAt) {
        jdbc.update("insert into activities (id,title,summary,description,category,cover,date_label,time_label,start_at,end_at,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,min_age,visibility,published_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, "活动", "summary", "description", "学习交流", "", "2026-06-01", "10:00 - 12:00",
                Timestamp.valueOf(endAt.minusHours(2)), Timestamp.valueOf(endAt), "杭州", "西湖区", BigDecimal.ONE,
                new BigDecimal("120.15"), new BigDecimal("30.27"), BigDecimal.ZERO, 20, 1, "报名中", organizerId,
                false, 0, "PUBLIC", Timestamp.valueOf(endAt.minusDays(5)));
    }
}
