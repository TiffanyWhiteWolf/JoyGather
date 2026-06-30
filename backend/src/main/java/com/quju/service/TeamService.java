package com.quju.service;

import com.quju.dto.TeamDto;
import com.quju.dto.TeamOpsDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeamService {
    private final JdbcTemplate jdbc;

    public TeamService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TeamDto> list(String query, boolean includeStopped) {
        return list(query, includeStopped, null);
    }

    public List<TeamDto> list(String query, boolean includeStopped, String userId) {
        String normalized = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        String sql = "select t.* from teams t left join users owner on owner.id = t.owner_id "
                + "where (? = true or t.status = '正常') "
                + "and (lower(t.name) like ? or lower(t.tags) like ? or lower(owner.nickname) like ?) "
                + "order by t.active_now desc, t.members_count desc";
        List<TeamDto> teams = jdbc.query(sql, mapper(), includeStopped, normalized, normalized, normalized);
        if (userId != null) populateMyRoles(teams, userId);
        return teams;
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
        return get(id, ownerId);
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
                return get(teamId, userId);
            }
            jdbc.update("insert into team_members (team_id,user_id,role) values (?,?,?)", teamId, userId, "成员");
            jdbc.update("update teams set members_count = members_count + 1, active_now = active_now + 1 where id = ?", teamId);
            addToTeamConversation(teamId, userId);
            jdbc.update("insert into team_points (team_id,user_id,points) values (?,?,?) on duplicate key update points = points + 5", teamId, userId, 5);
        }
        return get(teamId, userId);
    }

    @Transactional
    public TeamDto approveJoin(String teamId, String requestId, String actorId) {
        requireActive(teamId, "小队已停用，暂不可新增成员");
        requireTeamAdmin(teamId, actorId);
        List<String> users = jdbc.queryForList("select user_id from team_join_requests where id = ? and team_id = ? and status = '待审核'", String.class, requestId, teamId);
        if (users.isEmpty()) throw new IllegalStateException("申请不存在或已处理");
        String userId = users.get(0);
        jdbc.update("update team_join_requests set status = '已通过', handled_at = now(), handler_id = ? where id = ?", actorId, requestId);
        jdbc.update("insert ignore into team_members (team_id,user_id,role) values (?,?,?)", teamId, userId, "成员");
        jdbc.update("update teams set members_count = members_count + 1 where id = ?", teamId);
        addToTeamConversation(teamId, userId);
        return get(teamId, actorId);
    }

    @Transactional
    public void rejectJoin(String teamId, String requestId, String actorId, String reason) {
        requireTeamAdmin(teamId, actorId);
        jdbc.update("update team_join_requests set status = '已驳回', reason = ?, handled_at = now(), handler_id = ? where id = ? and team_id = ?",
                reason, actorId, requestId, teamId);
    }

    @Transactional
    public void dissolve(String teamId, String actorId) {
        if (!"队长".equals(roleOf(teamId, actorId))) throw new IllegalStateException("仅队长可解散小队");
        jdbc.update("update teams set status = '已停用', stop_reason = '队长解散' where id = ?", teamId);
        jdbc.update("delete from team_members where team_id = ?", teamId);
        jdbc.update("update conversations set online = 0, last_message = '小队已解散' where team_id = ?", teamId);
        log(actorId, "DISSOLVE_TEAM", "TEAM", teamId, "队长解散");
    }

    @Transactional
    public void announcement(String teamId, String actorId, TeamOpsDtos.AnnouncementRequest request) {
        requireActive(teamId, "小队已停用，暂不可新增内容");
        requireTeamAdmin(teamId, actorId);
        if (request.getContent() == null || request.getContent().trim().isEmpty()) throw new IllegalStateException("公告内容不能为空");
        jdbc.update("insert into team_announcements (id,team_id,author_id,content,mention_all) values (?,?,?,?,?)",
                DbSupport.id("ann"), teamId, actorId, request.getContent().trim(), request.isMentionAll());
    }

    @Transactional
    public void poll(String teamId, String actorId, TeamOpsDtos.PollRequest request) {
        requireActive(teamId, "小队已停用，暂不可新增内容");
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
        requireActive(teamId, "小队已停用，暂不可新增内容");
        requireMember(teamId, actorId);
        if (request.getFileId() == null || request.getFileId().trim().isEmpty()) throw new IllegalStateException("文件不能为空");
        jdbc.update("insert into team_files (id,team_id,file_id,uploader_id) values (?,?,?,?)", DbSupport.id("tf"), teamId, request.getFileId(), actorId);
        jdbc.update("insert into team_points (team_id,user_id,points) values (?,?,?) on duplicate key update points = points + 2", teamId, actorId, 2);
    }

    @Transactional
    public void addAlbumPhoto(String teamId, String actorId, TeamOpsDtos.TeamContentRequest request) {
        requireActive(teamId, "小队已停用，暂不可新增内容");
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

    public Map<String, String> myTeamRoles(String userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select team_id, role from team_members where user_id = ?", userId);
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("team_id"), (String) row.get("role"));
        }
        return result;
    }

    // ── US-C08 新增：成员管理 ──

    public List<Map<String, Object>> members(String teamId, String actorId) {
        requireMember(teamId, actorId);
        return jdbc.queryForList(
            "select tm.user_id as userId, tm.role, tm.joined_at as joinedAt, u.nickname, u.avatar " +
            "from team_members tm join users u on u.id = tm.user_id " +
            "where tm.team_id = ? order by field(tm.role,'队长','管理员','成员'), tm.joined_at asc", teamId);
    }

    @Transactional
    public void setRole(String teamId, String actorId, String targetUserId, String newRole) {
        requireTeamOwner(teamId, actorId);
        if (targetUserId == null || targetUserId.equals(actorId))
            throw new IllegalStateException("不能修改自己的角色");
        if (!"管理员".equals(newRole) && !"成员".equals(newRole))
            throw new IllegalStateException("角色仅可设为管理员或成员");
        String targetRole = roleOf(teamId, targetUserId);
        if (targetRole == null) throw new IllegalStateException("目标用户不是小队成员");
        if ("队长".equals(targetRole)) throw new IllegalStateException("不能修改队长的角色，如需更换请使用转让队长功能");
        jdbc.update("update team_members set role = ? where team_id = ? and user_id = ?", newRole, teamId, targetUserId);
        log(actorId, "CHANGE_ROLE", "TEAM_MEMBER", teamId + "/" + targetUserId, newRole);
    }

    @Transactional
    public void removeMember(String teamId, String actorId, String targetUserId) {
        requireTeamAdmin(teamId, actorId);
        String targetRole = roleOf(teamId, targetUserId);
        if (targetRole == null) throw new IllegalStateException("目标用户不是小队成员");
        if ("队长".equals(targetRole)) throw new IllegalStateException("不能移除队长");
        if ("管理员".equals(targetRole) && !"队长".equals(roleOf(teamId, actorId)))
            throw new IllegalStateException("仅队长可移除管理员");
        jdbc.update("delete from team_members where team_id = ? and user_id = ?", teamId, targetUserId);
        jdbc.update("update teams set members_count = greatest(members_count - 1, 0) where id = ?", teamId);
        jdbc.update("delete cp from conversation_participants cp join conversations c on c.id = cp.conversation_id where c.team_id = ? and cp.user_id = ?", teamId, targetUserId);
        jdbc.update("delete from team_points where team_id = ? and user_id = ?", teamId, targetUserId);
        log(actorId, "REMOVE_MEMBER", "TEAM_MEMBER", teamId + "/" + targetUserId, "");
    }

    @Transactional
    public TeamDto updateInfo(String teamId, String actorId, TeamOpsDtos.UpdateTeamRequest request) {
        requireTeamOwner(teamId, actorId);
        if (request.getName() != null && !request.getName().trim().isEmpty())
            jdbc.update("update teams set name = ? where id = ?", request.getName().trim(), teamId);
        if (request.getDescription() != null)
            jdbc.update("update teams set description = ? where id = ?", request.getDescription().trim(), teamId);
        if (request.getCover() != null && !request.getCover().trim().isEmpty())
            jdbc.update("update teams set cover = ? where id = ?", request.getCover().trim(), teamId);
        if (request.getTags() != null)
            jdbc.update("update teams set tags = ? where id = ?", DbSupport.join(request.getTags()), teamId);
        if (request.getCapacity() != null && request.getCapacity() > 0)
            jdbc.update("update teams set capacity = ? where id = ?", request.getCapacity(), teamId);
        if (request.getJoinMode() != null && !request.getJoinMode().trim().isEmpty())
            jdbc.update("update teams set join_mode = ? where id = ?", request.getJoinMode().trim(), teamId);
        log(actorId, "UPDATE_TEAM", "TEAM", teamId, "");
        return get(teamId, actorId);
    }

    @Transactional
    public TeamDto transferOwnership(String teamId, String actorId, String targetUserId) {
        requireTeamOwner(teamId, actorId);
        String targetRole = roleOf(teamId, targetUserId);
        if (targetRole == null) throw new IllegalStateException("目标用户不是小队成员");
        if (targetUserId.equals(actorId)) throw new IllegalStateException("不能转让给自己");
        jdbc.update("update team_members set role = '成员' where team_id = ? and user_id = ?", teamId, actorId);
        jdbc.update("update team_members set role = '队长' where team_id = ? and user_id = ?", teamId, targetUserId);
        jdbc.update("update teams set owner_id = ? where id = ?", targetUserId, teamId);
        log(actorId, "TRANSFER_OWNER", "TEAM", teamId, targetUserId);
        return get(teamId, actorId);
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

    public TeamDto adminDetail(String id) {
        TeamDto team = get(id);
        team.setMemberRecords(jdbc.queryForList("select tm.user_id, u.nickname, u.avatar, tm.role, tm.status, tm.joined_at from team_members tm join users u on u.id = tm.user_id where tm.team_id = ? order by case tm.role when '队长' then 0 when '管理员' then 1 else 2 end, tm.joined_at asc", id));
        team.setActivityRecords(jdbc.queryForList("select id,title,status,category,joined_count,capacity,updated_at from activities where team_id = ? order by updated_at desc", id));
        team.setReportRecords(jdbc.queryForList("select tr.id, tr.reason, tr.status, tr.created_at, u.nickname reporter from team_reports tr left join users u on u.id = tr.reporter_id where tr.team_id = ? order by tr.created_at desc", id));
        return team;
    }

    public TeamDto get(String id, String userId) {
        TeamDto team = get(id);
        if (userId != null) {
            String role = roleOf(id, userId);
            team.setMyRole(role);
        }
        return team;
    }

    // ── Private helpers ──

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
                List<String> owners = jdbc.queryForList("select nickname from users where id = ?", String.class, team.getOwnerId());
                team.setOwnerNickname(owners.isEmpty() ? "" : owners.get(0));
                return team;
            }
        };
    }

    private void requireActive(String teamId, String message) {
        TeamDto team = get(teamId);
        if (!"正常".equals(team.getStatus())) throw new IllegalStateException(message);
}
    private void populateMyRoles(List<TeamDto> teams, String userId) {
        if (teams.isEmpty()) return;
        List<String> ids = new ArrayList<>();
        for (TeamDto t : teams) ids.add(t.getId());
        String placeholders = String.join(",", ids.stream().map(id -> "?").toArray(String[]::new));
        Object[] params = new Object[ids.size() + 1];
        params[0] = userId;
        for (int i = 0; i < ids.size(); i++) params[i + 1] = ids.get(i);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select team_id, role from team_members where user_id = ? and team_id in (" + placeholders + ")",
            params);
        Map<String, String> roleMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            roleMap.put((String) row.get("team_id"), (String) row.get("role"));
        }
        for (TeamDto t : teams) {
            t.setMyRole(roleMap.get(t.getId()));
        }
    }

    private void addToTeamConversation(String teamId, String userId) {
        List<String> conversations = jdbc.queryForList("select id from conversations where team_id = ? limit 1", String.class, teamId);
        if (!conversations.isEmpty()) jdbc.update("insert ignore into conversation_participants (conversation_id,user_id) values (?,?)", conversations.get(0), userId);
    }

    private void requireTeamOwner(String teamId, String userId) {
        if (!"队长".equals(roleOf(teamId, userId))) throw new IllegalStateException("仅队长可执行此操作");
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
