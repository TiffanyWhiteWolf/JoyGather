package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityDto;
import com.quju.dto.RegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityServiceTest {
    private ActivityService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:activity_service_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
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

    private void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("create table users (id varchar(64) primary key, email varchar(160), password_hash varchar(255), nickname varchar(80), avatar varchar(500), role varchar(32), city varchar(80), gender varchar(20), birthday date, cover varchar(500), bio varchar(1000), interests varchar(1000), following_count int, follower_count int, credit int, verified boolean, activated boolean, activation_token varchar(128), status varchar(32), ban_reason varchar(1000), ban_until date, merchant_name varchar(160), merchant_nickname varchar(120), merchant_fields varchar(1000), created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");
        jdbc.execute("create table sessions (token varchar(128) primary key, user_id varchar(64), expires_at timestamp, created_at timestamp default current_timestamp)");
        jdbc.execute("create table merchant_applications (id varchar(64) primary key, user_id varchar(64), merchant_name varchar(160), license_name varchar(255), license_url varchar(500), status varchar(32), reason varchar(1000), submitted_at timestamp default current_timestamp, reviewed_at timestamp, reviewer_id varchar(64))");
        jdbc.execute("create table activities (id varchar(64) primary key, title varchar(160), summary varchar(1000), description varchar(1000), category varchar(80), cover varchar(500), date_label varchar(80), time_label varchar(80), start_at timestamp, end_at timestamp, registration_deadline timestamp, location varchar(255), district varchar(80), distance decimal(8,2), longitude decimal(10,6), latitude decimal(10,6), price decimal(10,2), capacity int, joined_count int, status varchar(32), organizer_id varchar(64), featured boolean, safety_note varchar(1000), min_age int, join_fields varchar(1000), offline_reason varchar(1000), published_at timestamp, team_id varchar(64), visibility varchar(32), ai_review_status varchar(32), ai_risk_labels varchar(1000), review_decision varchar(32), review_reason varchar(1000), submit_token varchar(128), created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");
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
