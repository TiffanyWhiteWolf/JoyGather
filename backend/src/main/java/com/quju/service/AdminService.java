package com.quju.service;

import com.quju.dto.ActivityDto;
import com.quju.dto.DashboardDto;
import com.quju.dto.ReviewTaskDto;
import com.quju.dto.UserDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final ActivityService activityService;
    private final UserService userService;

    public AdminService(JdbcTemplate jdbc, ActivityService activityService, UserService userService) {
        this.jdbc = jdbc;
        this.activityService = activityService;
        this.userService = userService;
    }

    public DashboardDto dashboard() {
        Map<String, Integer> metrics = new LinkedHashMap<String, Integer>();
        metrics.put("users", count("select count(*) from users"));
        metrics.put("monthlyActivities", count("select count(*) from activities where status <> '草稿'"));
        metrics.put("activeTeams", count("select count(*) from teams where status = '正常'"));
        metrics.put("pendingReviews", count("select count(*) from review_tasks where status = '待审核'"));
        Map<String, Integer> distribution = new LinkedHashMap<String, Integer>();
        List<Map<String, Object>> rows = jdbc.queryForList("select category, count(*) amount from activities where status <> '草稿' group by category order by amount desc");
        for (Map<String, Object> row : rows) distribution.put(String.valueOf(row.get("category")), ((Number) row.get("amount")).intValue());
        return new DashboardDto(metrics, distribution);
    }

    public List<ReviewTaskDto> reviews(String query, String type) {
        String normalized = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        String filterType = type == null || type.startsWith("全部") ? "" : type;
        return jdbc.query("select * from review_tasks where status = '待审核' and (? = '' or type = ?) and (lower(title) like ? or lower(submitter) like ?) order by submitted_at desc",
                reviewMapper(), filterType, filterType, normalized, normalized);
    }

    @Transactional
    public void review(String id, String result, String reason, String handlerId) {
        if ("已驳回".equals(result) && (reason == null || reason.trim().isEmpty())) {
            throw new IllegalStateException("驳回时必须填写原因");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("select * from review_tasks where id = ?", id);
        if (rows.isEmpty()) throw new java.util.NoSuchElementException("审核任务不存在");
        String type = String.valueOf(rows.get(0).get("type"));
        String targetId = String.valueOf(rows.get(0).get("target_id"));
        jdbc.update("update review_tasks set status = ?, handled_at = now(), handler_id = ?, handler_reason = ? where id = ?", result, handlerId, reason, id);
        if ("活动审核".equals(type)) {
            if ("已通过".equals(result)) jdbc.update("update activities set status = '报名中', published_at = now() where id = ?", targetId);
            if ("已驳回".equals(result)) jdbc.update("update activities set status = '已下架', offline_reason = ? where id = ?", reason, targetId);
            if ("要求修改".equals(result)) jdbc.update("update activities set status = '审核中', offline_reason = ? where id = ?", reason, targetId);
        }
        if ("商家认证".equals(type)) {
            jdbc.update("update merchant_applications set status = ?, reason = ?, reviewed_at = now(), reviewer_id = ? where id = ?", result, reason, handlerId, targetId);
            if ("已通过".equals(result)) {
                List<Map<String, Object>> merchants = jdbc.queryForList("select user_id, merchant_name from merchant_applications where id = ?", targetId);
                if (!merchants.isEmpty()) {
                    jdbc.update("update users set verified = 1, role = '商家用户', merchant_name = ? where id = ?",
                            merchants.get(0).get("merchant_name"), merchants.get(0).get("user_id"));
                }
            }
        }
        log(handlerId, "HANDLE_REVIEW", "REVIEW", id, result + ":" + DbSupport.safe(reason, ""));
    }

    public List<UserDto> users(String query, String role, String status) {
        return userService.search(query, role, status);
    }

    public List<Map<String, Object>> merchantApplications(String status) {
        String filter = status == null || status.trim().isEmpty() || status.startsWith("全部") ? "" : status;
        return jdbc.queryForList("select m.*, u.email, u.nickname from merchant_applications m join users u on u.id = m.user_id where (? = '' or m.status = ?) order by m.submitted_at desc", filter, filter);
    }

    public List<ActivityDto> activities(String query, String status) {
        return activityService.findAllForAdmin(query, status);
    }

    public ActivityDto activityDetail(String id) {
        return activityService.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("活动不存在"));
    }

    public void ban(String userId, String reason, String until, String actorId) {
        userService.ban(userId, reason, until, actorId);
    }

    public void unblock(String userId, String actorId) {
        userService.unblock(userId, actorId);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private RowMapper<ReviewTaskDto> reviewMapper() {
        return new RowMapper<ReviewTaskDto>() {
            public ReviewTaskDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                ReviewTaskDto task = new ReviewTaskDto();
                task.setId(rs.getString("id"));
                task.setType(rs.getString("type"));
                task.setTitle(rs.getString("title"));
                task.setSubmitter(rs.getString("submitter"));
                task.setRisk(rs.getString("risk"));
                task.setReason(rs.getString("reason"));
                task.setSubmittedAt(DbSupport.formatTime(rs.getTimestamp("submitted_at")));
                task.setStatus(rs.getString("status"));
                task.setTargetId(rs.getString("target_id"));
                return task;
            }
        };
    }

    private void log(String actorId, String action, String targetType, String targetId, String reason) {
        jdbc.update("insert into audit_logs (id,actor_id,action,target_type,target_id,reason) values (?,?,?,?,?,?)",
                DbSupport.id("log"), actorId, action, targetType, targetId, reason);
    }
}
