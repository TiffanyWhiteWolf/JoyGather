package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityDto;
import com.quju.dto.RegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ActivityServiceTest {
    private ActivityService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:activity_service_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        jdbc = new JdbcTemplate(dataSource);
        createSchema(jdbc);
        seedUser(jdbc);
        service = new ActivityService(jdbc, new UserService(jdbc));
    }

    @Test
    void createsLargeActivityInManualReview() {
        ActivityCreateRequest request = request(60);

        ActivityDto created = service.create(request);

        assertEquals("审核中", created.getStatus());
        assertEquals(60, created.getCapacity());
    }

    @Test
    void promotesFirstWaitingUserAfterCancellation() {
        ActivityDto created = service.create(request(2));
        service.register(created.getId(), "user-a");
        service.register(created.getId(), "user-b");

        RegistrationResult waiting = service.register(created.getId(), "user-c");
        RegistrationResult cancelled = service.cancel(created.getId(), "user-a");

        assertEquals("候补中", waiting.getStatus());
        assertEquals(1, waiting.getQueuePosition());
        assertEquals("user-c", cancelled.getPromotedUserId());
        assertEquals(2, service.findById(created.getId()).get().getJoined());
    }

    @Test
    void recommendsActivitiesMatchingCurrentInterests() {
        seedActivity("act-disc", "周末飞盘局", "户外运动", 1, 20, false, "报名中", "PUBLIC", "飞盘");
        seedActivity("act-board", "热门桌游夜", "桌游聚会", 19, 20, true, "报名中", "PUBLIC", "桌游");

        List<ActivityDto> flyingDisc = service.recommendations(Arrays.asList("飞盘"), 10);
        List<ActivityDto> boardGames = service.recommendations(Arrays.asList("桌游"), 10);

        assertEquals("act-disc", flyingDisc.get(0).getId());
        assertEquals("act-board", boardGames.get(0).getId());
    }

    @Test
    void usesPopularActivitiesWhenInterestsAreEmpty() {
        seedActivity("act-new", "新活动", "学习交流", 1, 20, false, "报名中", "PUBLIC", "阅读");
        seedActivity("act-popular", "热门活动", "城市探索", 18, 20, true, "报名中", "PUBLIC", "摄影友好");

        List<ActivityDto> result = service.recommendations(Collections.<String>emptyList(), 10);

        assertEquals("act-popular", result.get(0).getId());
    }

    @Test
    void excludesDraftOfflineAndNonPublicActivitiesFromRecommendations() {
        seedActivity("act-public", "公开飞盘", "户外运动", 1, 20, false, "报名中", "PUBLIC", "飞盘");
        seedActivity("act-draft", "草稿飞盘", "户外运动", 20, 20, true, "草稿", "PUBLIC", "飞盘");
        seedActivity("act-offline", "下架飞盘", "户外运动", 20, 20, true, "已下架", "PUBLIC", "飞盘");
        seedActivity("act-team", "小队飞盘", "户外运动", 20, 20, true, "报名中", "TEAM", "飞盘");

        List<ActivityDto> result = service.recommendations(Arrays.asList("飞盘"), 10);

        assertEquals(1, result.size());
        assertEquals("act-public", result.get(0).getId());
        assertFalse(result.stream().anyMatch(item -> "act-draft".equals(item.getId())));
    }

    private ActivityCreateRequest request(int capacity) {
        ActivityCreateRequest request = new ActivityCreateRequest();
        request.setTitle("测试活动");
        request.setSummary("用于验证报名和候补流程");
        request.setCategory("学习交流");
        request.setDate("2026-07-01");
        request.setTime("19:00 - 21:00");
        request.setStartTime("19:00");
        request.setEndTime("21:00");
        request.setDeadline("2026-07-01T18:00:00");
        request.setLocation("杭州");
        request.setDistrict("西湖区");
        request.setCapacity(capacity);
        request.setTags(Arrays.asList("测试"));
        request.setOrganizerId("u-001");
        return request;
    }

    private void seedActivity(String id, String title, String category, int joined, int capacity,
                              boolean featured, String status, String visibility, String tag) {
        jdbc.update("insert into activities "
                        + "(id,title,summary,description,category,cover,date_label,time_label,location,district,distance,"
                        + "longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,join_fields,"
                        + "published_at,visibility) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,current_timestamp,?)",
                id, title, title + "简介", title + "详情", category, "", "2026-08-01", "19:00 - 21:00",
                "杭州", "西湖区", 1, 120, 30, 0, capacity, joined, status, "u-001", featured, "", visibility);
        jdbc.update("insert into activity_tags (activity_id,tag,rank_order) values (?,?,1)", id, tag);
    }

    private void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("create table users (id varchar(64) primary key, email varchar(160), password_hash varchar(255), nickname varchar(80), avatar varchar(500), role varchar(32), city varchar(80), gender varchar(20), birthday date, cover varchar(500), bio varchar(1000), interests varchar(1000), following_count int, follower_count int, credit int, verified boolean, activated boolean, activation_token varchar(128), status varchar(32), ban_reason varchar(1000), ban_until date, merchant_name varchar(160), merchant_nickname varchar(120), merchant_fields varchar(1000), created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");
        jdbc.execute("create table sessions (token varchar(128) primary key, user_id varchar(64), expires_at timestamp, created_at timestamp default current_timestamp)");
        jdbc.execute("create table merchant_applications (id varchar(64) primary key, user_id varchar(64), merchant_name varchar(160), license_name varchar(255), license_url varchar(500), status varchar(32), reason varchar(1000), submitted_at timestamp default current_timestamp, reviewed_at timestamp, reviewer_id varchar(64))");
        jdbc.execute("create table activities (id varchar(64) primary key, title varchar(160), summary varchar(1000), description varchar(1000), category varchar(80), cover varchar(500), date_label varchar(80), time_label varchar(80), start_at timestamp, end_at timestamp, registration_deadline timestamp, location varchar(255), city varchar(80) default '杭州', district varchar(80), distance decimal(8,2), longitude decimal(10,6), latitude decimal(10,6), price decimal(10,2), capacity int, joined_count int, status varchar(32), organizer_id varchar(64), featured boolean, safety_note varchar(1000), min_age int, join_fields varchar(1000), offline_reason varchar(1000), published_at timestamp, team_id varchar(64), visibility varchar(32), ai_review_status varchar(32), ai_risk_labels varchar(1000), review_decision varchar(32), review_reason varchar(1000), submit_token varchar(128), created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");
        jdbc.execute("create table activity_tags (activity_id varchar(64), tag varchar(80), rank_order int, primary key(activity_id, tag))");
        jdbc.execute("create table registrations (id varchar(64) primary key, activity_id varchar(64), user_id varchar(64), status varchar(32), queue_position int, form_data varchar(2000), promoted_until timestamp, promotion_sent_at timestamp, created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp, checked_in_at timestamp)");
        jdbc.execute("create table review_tasks (id varchar(64) primary key, type varchar(40), target_id varchar(64), title varchar(180), submitter varchar(120), risk varchar(16), reason varchar(1000), submitted_at timestamp default current_timestamp, status varchar(32), handled_at timestamp, handler_id varchar(64), handler_reason varchar(1000))");
        jdbc.execute("create table audit_logs (id varchar(64) primary key, actor_id varchar(64), action varchar(80), target_type varchar(80), target_id varchar(64), reason varchar(1000), created_at timestamp default current_timestamp)");
    }

    private void seedUser(JdbcTemplate jdbc) {
        jdbc.update("insert into users (id,email,password_hash,nickname,avatar,role,city,bio,interests,following_count,follower_count,credit,verified,activated,status) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "u-001", "demo@quju.cn", "plain:12345678", "小满", "", "个人用户", "杭州", "", "", 0, 0, 100, true, true, "正常");
        jdbc.update("insert into users (id,email,password_hash,nickname,avatar,role,city,bio,interests,following_count,follower_count,credit,verified,activated,status) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "user-a", "a@quju.cn", "plain:12345678", "A", "", "个人用户", "杭州", "", "", 0, 0, 100, true, true, "正常");
        jdbc.update("insert into users (id,email,password_hash,nickname,avatar,role,city,bio,interests,following_count,follower_count,credit,verified,activated,status) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "user-b", "b@quju.cn", "plain:12345678", "B", "", "个人用户", "杭州", "", "", 0, 0, 100, true, true, "正常");
        jdbc.update("insert into users (id,email,password_hash,nickname,avatar,role,city,bio,interests,following_count,follower_count,credit,verified,activated,status) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "user-c", "c@quju.cn", "plain:12345678", "C", "", "个人用户", "杭州", "", "", 0, 0, 100, true, true, "正常");
    }
}
