package com.quju.service;

import com.quju.dto.TeamDto;
import com.quju.dto.TeamOpsDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class TeamService {
    private final JdbcTemplate jdbc;

    public TeamService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TeamDto> list(String query, boolean includeStopped) {
        String normalized = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        String sql = "select * from teams where (? = true or status = '正常') and (lower(name) like ? or lower(tags) like ?) order by active_now desc, members_count desc";
        return jdbc.query(sql, mapper(), includeStopped, normalized, normalized);
    }

    @Transactional
    public TeamDto create(TeamDto request, String ownerId) {
        String id = DbSupport.id("team");
        jdbc.update("insert into teams (id,name,description,cover,tags,members_count,capacity,join_mode,active_now,owner_id) values (?,?,?,?,?,?,?,?,?,?)",
                id, request.getName(), request.getDescription(), DbSupport.safe(request.getCover(), "https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=900&q=80"),
                DbSupport.join(request.getTags()), 1, request.getCapacity(), DbSupport.safe(request.getJoinMode(), "公开加入"), 1, ownerId);
        jdbc.update("insert into team_members (team_id,user_id,role) values (?,?,?)", id, ownerId, "队长");
        String conversationId = DbSupport.id("cv");
        jdbc.update("insert into conversations (id,name,avatar,type,team_id,unread,last_message,last_time,online) values (?,?,?,?,?,?,?,?,?)",
                conversationId, request.getName(), request.getCover(), "小队", id, 0, "小队已创建，开始打个招呼吧", "刚刚", true);
        jdbc.update("insert into conversation_participants (conversation_id,user_id) values (?,?)", conversationId, ownerId);
        jdbc.update("insert into team_points (team_id,user_id,points) values (?,?,?)", id, ownerId, 20);
        return get(id);
    }

    @Transactional
    public TeamDto join(String teamId, String userId) {
        TeamDto team = get(teamId);
        if (!"正常".equals(team.getStatus())) throw new IllegalStateException("小队已停用，暂不可加入");
        if (team.getMembers() >= team.getCapacity()) throw new IllegalStateException("小队人数已满");
        Integer existing = jdbc.queryForObject("select count(*) from team_members where team_id = ? and user_id = ?", Integer.class, teamId, userId);
        if (existing != null && existing == 0) {
            if ("审核加入".equals(team.getJoinMode())) {
                jdbc.update("insert ignore into team_join_requests (id,team_id,user_id,status) values (?,?,?,'待审核')", DbSupport.id("tjr"), teamId, userId);
                return team;
            }
            jdbc.update("insert into team_members (team_id,user_id,role) values (?,?,?)", teamId, userId, "成员");
            jdbc.update("update teams set members_count = members_count + 1, active_now = active_now + 1 where id = ?", teamId);
            addToTeamConversation(teamId, userId);
            jdbc.update("insert into team_points (team_id,user_id,points) values (?,?,?) on duplicate key update points = points + 5", teamId, userId, 5);
        }
        return get(teamId);
    }

    @Transactional
    public TeamDto approveJoin(String teamId, String requestId, String actorId) {
        requireTeamAdmin(teamId, actorId);
        List<String> users = jdbc.queryForList("select user_id from team_join_requests where id = ? and team_id = ? and status = '待审核'", String.class, requestId, teamId);
        if (users.isEmpty()) throw new IllegalStateException("申请不存在或已处理");
        String userId = users.get(0);
        jdbc.update("update team_join_requests set status = '已通过', handled_at = now(), handler_id = ? where id = ?", actorId, requestId);
        jdbc.update("insert ignore into team_members (team_id,user_id,role) values (?,?,?)", teamId, userId, "成员");
        jdbc.update("update teams set members_count = members_count + 1 where id = ?", teamId);
        addToTeamConversation(teamId, userId);
        return get(teamId);
    }

    @Transactional
    public void rejectJoin(String teamId, String requestId, String actorId, String reason) {
        requireTeamAdmin(teamId, actorId);
        jdbc.update("update team_join_requests set status = '已驳回', reason = ?, handled_at = now(), handler_id = ? where id = ? and team_id = ?",
                reason, actorId, requestId, teamId);
    }

    @Transactional
    public void dissolve(String teamId, String actorId, TeamOpsDtos.DissolveRequest request) {
        if (request == null || !request.isConfirmed() || !"解散小队".equals(DbSupport.safe(request.getConfirmationText(), "").trim())) {
            throw new IllegalStateException("请二次确认解散小队");
        }
        TeamDto team = get(teamId);
        if (!"正常".equals(team.getStatus())) throw new IllegalStateException("小队已停用");
        if (!"队长".equals(roleOf(teamId, actorId))) throw new IllegalStateException("仅队长可解散小队");
        jdbc.update("update teams set status = '已停用', stop_reason = '队长解散', members_count = 0, active_now = 0 where id = ?", teamId);
        jdbc.update("delete from team_members where team_id = ?", teamId);
        jdbc.update("delete from conversation_participants where conversation_id in (select id from conversations where team_id = ?)", teamId);
        jdbc.update("update conversations set online = 0, last_message = '小队已解散' where team_id = ?", teamId);
        jdbc.update("update activities set status = '已下架', offline_reason = '所属小队已解散' where team_id = ? and status not in ('草稿','已结束','已下架')", teamId);
        log(actorId, "DISSOLVE_TEAM", "TEAM", teamId, "队长解散");
    }

    @Transactional
    public void announcement(String teamId, String actorId, TeamOpsDtos.AnnouncementRequest request) {
        requireTeamAdmin(teamId, actorId);
        if (request.getContent() == null || request.getContent().trim().isEmpty()) throw new IllegalStateException("公告内容不能为空");
        jdbc.update("insert into team_announcements (id,team_id,author_id,content,mention_all) values (?,?,?,?,?)",
                DbSupport.id("ann"), teamId, actorId, request.getContent().trim(), request.isMentionAll());
    }

    @Transactional
    public void poll(String teamId, String actorId, TeamOpsDtos.PollRequest request) {
        requireMember(teamId, actorId);
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) throw new IllegalStateException("投票标题不能为空");
        if (request.getOptions() == null || request.getOptions().size() < 2) throw new IllegalStateException("投票至少需要两个选项");
        String pollId = DbSupport.id("poll");
        jdbc.update("insert into team_polls (id,team_id,author_id,title) values (?,?,?,?)", pollId, teamId, actorId, request.getTitle().trim());
        int order = 1;
        for (String option : request.getOptions()) {
            if (option != null && !option.trim().isEmpty()) jdbc.update("insert into team_poll_options (id,poll_id,text,rank_order) values (?,?,?,?)", DbSupport.id("opt"), pollId, option.trim(), order++);
        }
    }

    @Transactional
    public void addFile(String teamId, String actorId, TeamOpsDtos.TeamContentRequest request) {
        requireMember(teamId, actorId);
        if (request.getFileId() == null || request.getFileId().trim().isEmpty()) throw new IllegalStateException("文件不能为空");
        jdbc.update("insert into team_files (id,team_id,file_id,uploader_id) values (?,?,?,?)", DbSupport.id("tf"), teamId, request.getFileId(), actorId);
        jdbc.update("insert into team_points (team_id,user_id,points) values (?,?,?) on duplicate key update points = points + 2", teamId, actorId, 2);
    }

    @Transactional
    public void addAlbumPhoto(String teamId, String actorId, TeamOpsDtos.TeamContentRequest request) {
        requireMember(teamId, actorId);
        if (request.getUrl() == null || request.getUrl().trim().isEmpty()) throw new IllegalStateException("照片不能为空");
        jdbc.update("insert into team_albums (id,team_id,file_id,uploader_id,url,caption) values (?,?,?,?,?,?)",
                DbSupport.id("ta"), teamId, request.getFileId(), actorId, request.getUrl(), request.getCaption());
        jdbc.update("insert into team_points (team_id,user_id,points) values (?,?,?) on duplicate key update points = points + 3", teamId, actorId, 3);
    }

    public List<Map<String, Object>> leaderboard(String teamId) {
        return jdbc.queryForList("select u.id,u.nickname,u.avatar,p.points from team_points p join users u on u.id = p.user_id where p.team_id = ? order by p.points desc limit 20", teamId);
    }

    public List<Map<String, Object>> joinRequests(String teamId, String actorId) {
        requireTeamAdmin(teamId, actorId);
        return jdbc.queryForList("select r.*, u.nickname, u.avatar from team_join_requests r join users u on u.id = r.user_id where r.team_id = ? order by r.created_at desc", teamId);
    }

    public List<String> myTeamIds(String userId) {
        return jdbc.queryForList("select team_id from team_members where user_id = ?", String.class, userId);
    }

    @Transactional
    public void stop(String teamId, String reason, String actorId) {
        if (reason == null || reason.trim().isEmpty()) throw new IllegalStateException("停用原因不能为空");
        jdbc.update("update teams set status = '已停用', stop_reason = ? where id = ?", reason.trim(), teamId);
        log(actorId, "STOP_TEAM", "TEAM", teamId, reason);
    }

    @Transactional
    public void restore(String teamId, String actorId) {
        jdbc.update("update teams set status = '正常', stop_reason = null where id = ?", teamId);
        log(actorId, "RESTORE_TEAM", "TEAM", teamId, "");
    }

    public TeamDto get(String id) {
        List<TeamDto> rows = jdbc.query("select * from teams where id = ?", mapper(), id);
        if (rows.isEmpty()) throw new java.util.NoSuchElementException("小队不存在");
        return rows.get(0);
    }

    private RowMapper<TeamDto> mapper() {
        return new RowMapper<TeamDto>() {
            public TeamDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                TeamDto team = new TeamDto();
                team.setId(rs.getString("id"));
                team.setName(rs.getString("name"));
                team.setDescription(rs.getString("description"));
                team.setCover(rs.getString("cover"));
                team.setTags(DbSupport.split(rs.getString("tags")));
                team.setMembers(rs.getInt("members_count"));
                team.setCapacity(rs.getInt("capacity"));
                team.setJoinMode(rs.getString("join_mode"));
                team.setActiveNow(rs.getInt("active_now"));
                team.setStatus(rs.getString("status"));
                team.setStopReason(rs.getString("stop_reason"));
                team.setOwnerId(rs.getString("owner_id"));
                return team;
            }
        };
    }

    private void addToTeamConversation(String teamId, String userId) {
        List<String> conversations = jdbc.queryForList("select id from conversations where team_id = ? limit 1", String.class, teamId);
        if (!conversations.isEmpty()) jdbc.update("insert ignore into conversation_participants (conversation_id,user_id) values (?,?)", conversations.get(0), userId);
    }

    private void requireTeamAdmin(String teamId, String userId) {
        String role = roleOf(teamId, userId);
        if (!"队长".equals(role) && !"管理员".equals(role)) throw new IllegalStateException("需要小队管理员权限");
    }

    private void requireMember(String teamId, String userId) {
        if (roleOf(teamId, userId) == null) throw new IllegalStateException("需要先加入小队");
    }

    private String roleOf(String teamId, String userId) {
        List<String> roles = jdbc.queryForList("select role from team_members where team_id = ? and user_id = ?", String.class, teamId, userId);
        return roles.isEmpty() ? null : roles.get(0);
    }

    private void log(String actorId, String action, String targetType, String targetId, String reason) {
        jdbc.update("insert into audit_logs (id,actor_id,action,target_type,target_id,reason) values (?,?,?,?,?,?)",
                DbSupport.id("log"), actorId, action, targetType, targetId, reason);
    }
}
