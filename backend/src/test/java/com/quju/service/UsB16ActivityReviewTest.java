package com.quju.service;

import com.quju.dto.ActivityOpsDtos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsB16ActivityReviewTest extends TestBase {
    @Test
    void participantCanCreateAndUpdateOneReviewWithinFourteenDays() {
        insertEndedActivity("review-open", LocalDateTime.now().minusDays(2));
        jdbc.update("insert into registrations (id,activity_id,user_id,status,queue_position) values ('reg-review','review-open',?,'已签到',0)", USER_ID);
        ActivityOpsDtos.ReviewRequest request = new ActivityOpsDtos.ReviewRequest();
        request.setRating(4); request.setContent("活动组织得很好");
        activityService.reviewActivity("review-open", USER_ID, request);
        request.setRating(5); request.setContent("补充：非常喜欢");
        activityService.reviewActivity("review-open", USER_ID, request);

        assertEquals(1, jdbc.queryForObject("select count(*) from activity_reviews_user where activity_id='review-open' and user_id=?", Integer.class, USER_ID));
        assertEquals(5, jdbc.queryForObject("select rating from activity_reviews_user where activity_id='review-open' and user_id=?", Integer.class, USER_ID));
        ActivityOpsDtos.AfterEventDto feedback = activityService.afterEvent("review-open", USER_ID);
        assertTrue(feedback.isCanReview());
        assertEquals(5.0, feedback.getAverageRating());
    }

    @Test
    void hidesEntryAfterDeadlineAndRejectsNonParticipant() {
        insertEndedActivity("review-expired", LocalDateTime.now().minusDays(20));
        jdbc.update("insert into registrations (id,activity_id,user_id,status,queue_position) values ('reg-expired','review-expired',?,'已签到',0)", USER_ID);
        ActivityOpsDtos.ReviewRequest request = new ActivityOpsDtos.ReviewRequest();
        request.setRating(5); request.setContent("超期评价");
        assertFalse(activityService.afterEvent("review-expired", USER_ID).isCanReview());
        assertThrows(IllegalStateException.class, () -> activityService.reviewActivity("review-expired", USER_ID, request));

        insertEndedActivity("review-outsider", LocalDateTime.now().minusDays(1));
        assertThrows(IllegalStateException.class, () -> activityService.reviewActivity("review-outsider", "user-a", request));
    }

    private void insertEndedActivity(String id, LocalDateTime endAt) {
        jdbc.update("insert into activities (id,title,summary,description,category,cover,date_label,time_label,start_at,end_at,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,min_age,visibility,published_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, "活动", "summary", "description", "学习交流", "", "2026-06-01", "10:00 - 12:00",
                Timestamp.valueOf(endAt.minusHours(2)), Timestamp.valueOf(endAt), "杭州", "西湖区", BigDecimal.ONE,
                new BigDecimal("120.15"), new BigDecimal("30.27"), BigDecimal.ZERO, 20, 1, "报名中", "user-a",
                false, 0, "PUBLIC", Timestamp.valueOf(endAt.minusDays(5)));
    }
}
