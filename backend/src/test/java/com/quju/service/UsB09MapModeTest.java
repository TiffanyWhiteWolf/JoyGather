package com.quju.service;

import com.quju.dto.ActivityDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UsB09MapModeTest extends TestBase {
    @Test
    void returnsOnlyPublishedActivitiesInsideCurrentBounds() {
        insertActivity("inside", "报名中", "PUBLIC", "2026-06-01 10:00:00", "120.15", "30.27");
        insertActivity("outside", "报名中", "PUBLIC", "2026-06-01 10:00:00", "121.15", "31.27");
        insertActivity("reviewing", "审核中", "PUBLIC", null, "120.16", "30.28");

        List<ActivityDto> rows = activityService.findAll(null, null,
                new BigDecimal("120.10"), new BigDecimal("120.20"),
                new BigDecimal("30.20"), new BigDecimal("30.30"), "latest");

        assertEquals(1, rows.size());
        assertEquals("inside", rows.get(0).getId());
    }

    private void insertActivity(String id, String status, String visibility, String publishedAt, String lng, String lat) {
        jdbc.update("insert into activities (id,title,summary,description,category,cover,date_label,time_label,start_at,end_at,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,min_age,visibility,published_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, id, "summary", "description", "城市探索", "", "2026-07-01", "10:00 - 12:00",
                java.sql.Timestamp.valueOf("2026-07-01 10:00:00"), java.sql.Timestamp.valueOf("2026-07-01 12:00:00"),
                "杭州", "西湖区", BigDecimal.ONE, new BigDecimal(lng), new BigDecimal(lat), BigDecimal.ZERO,
                20, 0, status, USER_ID, false, 0, visibility,
                publishedAt == null ? null : java.sql.Timestamp.valueOf(publishedAt));
    }
}
