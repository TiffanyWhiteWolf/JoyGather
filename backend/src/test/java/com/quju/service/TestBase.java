package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.AuthDtos;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * 迭代一测试公共基类，提供 H2 内存数据库环境构建、建表和种子数据。
 */
public abstract class TestBase {

    protected JdbcTemplate jdbc;
    protected UserService userService;
    protected ActivityService activityService;
    protected AdminService adminService;
    protected TeamService teamService;

    protected static final String ADMIN_ID = "u-admin";
    protected static final String ADMIN_EMAIL = "admin@quju.cn";
    protected static final String USER_ID = "u-user1";
    protected static final String USER_EMAIL = "user1@quju.cn";
    protected static final String USER_PASS = "12345678";

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:iter1_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        jdbc = new JdbcTemplate(ds);
        createAllTables();
        seedUsers();

        userService = new UserService(jdbc);
        activityService = new ActivityService(jdbc, userService);
        adminService = new AdminService(jdbc, activityService, userService, null);
        teamService = new TeamService(jdbc);
    }

    // -------------------------------------------------------
    // 辅助工厂方法
    // -------------------------------------------------------

    protected String loginAsUser() {
        return userService.login(loginRequest(USER_EMAIL, USER_PASS)).getToken();
    }

    protected String registerAndGetId(String email, String nickname) {
        AuthDtos.RegisterRequest req = registerRequest(email, USER_PASS, USER_PASS, nickname);
        return userService.register(req, "http://localhost").getUserId();
    }

    protected AuthDtos.RegisterRequest registerRequest(String email, String password, String confirm, String nickname) {
        AuthDtos.RegisterRequest req = new AuthDtos.RegisterRequest();
        req.setEmail(email);
        req.setPassword(password);
        req.setConfirmPassword(confirm);
        req.setNickname(nickname);
        req.setRole("个人用户");
        return req;
    }

    protected AuthDtos.LoginRequest loginRequest(String email, String password) {
        AuthDtos.LoginRequest req = new AuthDtos.LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    protected AuthDtos.LoginRequest adminLoginRequest(String email, String password) {
        AuthDtos.LoginRequest req = new AuthDtos.LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        req.setAdminLogin(true);
        return req;
    }

    protected ActivityCreateRequest validActivityRequest(int capacity) {
        ActivityCreateRequest req = new ActivityCreateRequest();
        req.setTitle("测试活动" + System.nanoTime());
        req.setSummary("用于自动化测试的活动");
        req.setCategory("学习交流");
        req.setDate("2026-08-01");
        req.setTime("14:00 - 17:00");
        req.setStartTime("14:00");
        req.setEndTime("17:00");
        req.setDeadline("2026-07-30T20:00:00");
        req.setLocation("杭州市西湖区");
        req.setCity("杭州");
        req.setDistrict("西湖区");
        req.setPrice(BigDecimal.ZERO);
        req.setCapacity(capacity);
        req.setTags(Arrays.asList("测试", "自动化"));
        req.setSafetyNote("请穿舒适的运动鞋");
        req.setMinAge(0);
        req.setOrganizerId(USER_ID);
        return req;
    }

    // -------------------------------------------------------
    // 建表
    // -------------------------------------------------------
    private void createAllTables() {
        jdbc.execute("create table users ("
                + "id varchar(64) primary key, email varchar(160), password_hash varchar(255),"
                + "nickname varchar(80), avatar varchar(500), role varchar(32), city varchar(80),"
                + "gender varchar(20), birthday date, cover varchar(500), bio varchar(1000),"
                + "interests varchar(1000), following_count int, follower_count int, credit int,"
                + "verified boolean, activated boolean, activation_token varchar(128),"
                + "status varchar(32), ban_reason varchar(1000), ban_until date,"
                + "merchant_name varchar(160), merchant_nickname varchar(120), merchant_fields varchar(1000),"
                + "created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");

        jdbc.execute("create table sessions ("
                + "token varchar(128) primary key, user_id varchar(64),"
                + "expires_at timestamp, created_at timestamp default current_timestamp)");

        jdbc.execute("create table merchant_applications ("
                + "id varchar(64) primary key, user_id varchar(64), merchant_name varchar(160),"
                + "license_name varchar(255), license_url varchar(500),"
                + "status varchar(32), reason varchar(1000),"
                + "submitted_at timestamp default current_timestamp, reviewed_at timestamp, reviewer_id varchar(64))");

        jdbc.execute("create table activities ("
                + "id varchar(64) primary key, title varchar(160), summary varchar(1000),"
                + "description varchar(1000), category varchar(80), cover varchar(500),"
                + "date_label varchar(80), time_label varchar(80),"
                + "start_at timestamp, end_at timestamp, registration_deadline timestamp,"
                + "location varchar(255), city varchar(80) default '杭州', district varchar(80), distance decimal(8,2),"
                + "longitude decimal(10,6), latitude decimal(10,6), price decimal(10,2),"
                + "capacity int, joined_count int, status varchar(32),"
                + "organizer_id varchar(64), featured boolean,"
                + "safety_note varchar(1000), min_age int, join_fields varchar(1000),"
                + "offline_reason varchar(1000), published_at timestamp,"
                + "team_id varchar(64), visibility varchar(32),"
                + "ai_review_status varchar(32), ai_risk_labels varchar(1000),"
                + "review_decision varchar(32), review_reason varchar(1000), submit_token varchar(128),"
                + "created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");

        jdbc.execute("create table activity_tags ("
                + "activity_id varchar(64), tag varchar(80), rank_order int,"
                + "primary key(activity_id, tag))");

        jdbc.execute("create table registrations ("
                + "id varchar(64) primary key, activity_id varchar(64), user_id varchar(64),"
                + "status varchar(32), queue_position int,"
                + "form_data varchar(2000), promoted_until timestamp, promotion_sent_at timestamp,"
                + "created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp,"
                + "checked_in_at timestamp)");

        jdbc.execute("create table review_tasks ("
                + "id varchar(64) primary key, type varchar(40), target_id varchar(64),"
                + "title varchar(180), submitter varchar(120), risk varchar(16), reason varchar(1000),"
                + "submitted_at timestamp default current_timestamp, status varchar(32),"
                + "handled_at timestamp, handler_id varchar(64), handler_reason varchar(1000))");

        jdbc.execute("create table audit_logs ("
                + "id varchar(64) primary key, actor_id varchar(64), action varchar(80),"
                + "target_type varchar(80), target_id varchar(64), reason varchar(1000),"
                + "created_at timestamp default current_timestamp)");

        jdbc.execute("create table notifications ("
                + "id varchar(64) primary key, user_id varchar(64), type varchar(60), title varchar(180),"
                + "content varchar(1000), target_type varchar(60), target_id varchar(64),"
                + "read_flag boolean default false, created_at timestamp default current_timestamp)");

        jdbc.execute("create table teams ("
                + "id varchar(64) primary key, name varchar(120), description varchar(1000),"
                + "cover varchar(500), tags varchar(1000), members_count int, capacity int,"
                + "join_mode varchar(32), active_now int, status varchar(32) default '正常',"
                + "stop_reason varchar(1000), owner_id varchar(64),"
                + "created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");

        jdbc.execute("create table team_members ("
                + "team_id varchar(64), user_id varchar(64), role varchar(32),"
                + "status varchar(32) default '正常', joined_at timestamp default current_timestamp,"
                + "primary key(team_id, user_id))");

        jdbc.execute("create table team_join_requests ("
                + "id varchar(64) primary key, team_id varchar(64), user_id varchar(64),"
                + "status varchar(32) default '待审核', reason varchar(1000),"
                + "created_at timestamp default current_timestamp, handled_at timestamp, handler_id varchar(64))");

        jdbc.execute("create table team_announcements ("
                + "id varchar(64) primary key, team_id varchar(64), author_id varchar(64),"
                + "content varchar(1000), mention_all boolean default false, created_at timestamp default current_timestamp)");

        jdbc.execute("create table team_polls ("
                + "id varchar(64) primary key, team_id varchar(64), author_id varchar(64),"
                + "title varchar(180), status varchar(32) default '进行中', created_at timestamp default current_timestamp)");

        jdbc.execute("create table team_poll_options ("
                + "id varchar(64) primary key, poll_id varchar(64), text varchar(180), rank_order int)");

        jdbc.execute("create table files ("
                + "id varchar(64) primary key, owner_id varchar(64), original_name varchar(255),"
                + "content_type varchar(120), size_bytes bigint, url varchar(700), storage_key varchar(500),"
                + "provider varchar(40), created_at timestamp default current_timestamp)");

        jdbc.execute("create table team_files ("
                + "id varchar(64) primary key, team_id varchar(64), file_id varchar(64),"
                + "uploader_id varchar(64), created_at timestamp default current_timestamp)");

        jdbc.execute("create table team_albums ("
                + "id varchar(64) primary key, team_id varchar(64), file_id varchar(64),"
                + "uploader_id varchar(64), url varchar(700), caption varchar(1000),"
                + "created_at timestamp default current_timestamp)");

        jdbc.execute("create table team_points ("
                + "team_id varchar(64), user_id varchar(64), points int,"
                + "updated_at timestamp default current_timestamp, primary key(team_id, user_id))");

        jdbc.execute("create table team_reports ("
                + "id varchar(64) primary key, team_id varchar(64), reporter_id varchar(64),"
                + "reason varchar(1000), status varchar(32), created_at timestamp default current_timestamp)");

        jdbc.execute("create table conversations ("
                + "id varchar(64) primary key, name varchar(120), avatar varchar(500),"
                + "type varchar(32), team_id varchar(64), friend_user_id varchar(64),"
                + "unread int default 0, last_message varchar(1000), last_time varchar(40),"
                + "online boolean default false)");

        jdbc.execute("create table conversation_participants ("
                + "conversation_id varchar(64), user_id varchar(64), last_read_at timestamp,"
                + "muted boolean default false, pinned boolean default false,"
                + "primary key(conversation_id, user_id))");

        jdbc.execute("create table messages ("
                + "id varchar(64) primary key, conversation_id varchar(64), sender_id varchar(64),"
                + "content varchar(1000), message_type varchar(32), media_url varchar(500),"
                + "location_lat double, location_lng double, sent_at timestamp default current_timestamp,"
                + "mine boolean default false, read_flag boolean default false, recalled boolean default false,"
                + "recalled_at timestamp, forwarded_from_id varchar(64))");

        jdbc.execute("create table activity_summaries ("
                + "id varchar(64) primary key, activity_id varchar(64), author_id varchar(64),"
                + "title varchar(180), content varchar(4000), status varchar(32) default '已发布',"
                + "created_at timestamp default current_timestamp, updated_at timestamp default current_timestamp)");

        jdbc.execute("create table summary_images ("
                + "id varchar(64) primary key, summary_id varchar(64), file_id varchar(64), url varchar(700),"
                + "ai_category varchar(80), confirmed_category varchar(80), rank_order int)");

        jdbc.execute("create table activity_reviews_user ("
                + "id varchar(64) primary key, activity_id varchar(64), user_id varchar(64), rating int,"
                + "content varchar(1000), created_at timestamp default current_timestamp,"
                + "unique(activity_id,user_id))");

        jdbc.execute("create table follows ("
                + "follower_id varchar(64), followee_id varchar(64), created_at timestamp default current_timestamp,"
                + "primary key(follower_id, followee_id))");

        jdbc.execute("create table friendships ("
                + "user_id varchar(64), friend_id varchar(64), remark varchar(100), group_name varchar(100),"
                + "created_at timestamp default current_timestamp, primary key(user_id, friend_id))");

        jdbc.execute("create table friend_requests ("
                + "id varchar(64) primary key, requester_id varchar(64), receiver_id varchar(64),"
                + "source varchar(40), message varchar(1000), status varchar(32),"
                + "created_at timestamp default current_timestamp, handled_at timestamp)");

        jdbc.execute("create table user_blocks ("
                + "user_id varchar(64), blocked_user_id varchar(64), reason varchar(1000),"
                + "created_at timestamp default current_timestamp, primary key(user_id, blocked_user_id))");
    }

    // -------------------------------------------------------
    // 种子用户
    // -------------------------------------------------------
    private void seedUsers() {
        jdbc.update("insert into users (id,email,password_hash,nickname,avatar,role,city,"
                + "following_count,follower_count,credit,verified,activated,status) "
                + "values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ADMIN_ID, ADMIN_EMAIL, "plain:" + USER_PASS, "系统管理员", "",
                "管理员", "杭州", 0, 0, 100, true, true, "正常");

        jdbc.update("insert into users (id,email,password_hash,nickname,avatar,role,city,"
                + "following_count,follower_count,credit,verified,activated,status) "
                + "values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                USER_ID, USER_EMAIL, "plain:" + USER_PASS, "小满", "",
                "个人用户", "杭州", 0, 0, 100, true, true, "正常");

        for (String[] u : new String[][]{
                {"user-a", "a@quju.cn", "A"},
                {"user-b", "b@quju.cn", "B"},
                {"user-c", "c@quju.cn", "C"},
                {"user-d", "d@quju.cn", "D"},
        }) {
            jdbc.update("insert into users (id,email,password_hash,nickname,avatar,role,city,"
                    + "following_count,follower_count,credit,verified,activated,status) "
                    + "values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    u[0], u[1], "plain:" + USER_PASS, u[2], "",
                    "个人用户", "杭州", 0, 0, 100, true, true, "正常");
        }
    }
}
