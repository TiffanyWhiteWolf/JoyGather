package com.quju.service;

import com.quju.dto.TeamDto;
import com.quju.dto.TeamOpsDtos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-D06 小队管理（后台）
 */
class UsD06TeamManagementTest extends TestBase {

    @Test
    @DisplayName("US-D06-1: 可按小队名称或队长搜索小队")
    void shouldSearchTeamsByNameOrLeader() {
        createTeamFixture("team-d06-a", "晨跑小队", USER_ID);

        assertTrue(teamService.list("晨跑", true).stream().anyMatch(team -> "team-d06-a".equals(team.getId())));
        assertTrue(teamService.list("小满", true).stream().anyMatch(team -> "team-d06-a".equals(team.getId())));
    }

    @Test
    @DisplayName("US-D06-2/3/5: 可查看基本信息、成员、活动、举报记录且管理员只读治理")
    void shouldExposeAdminTeamDetailSnapshot() {
        createTeamFixture("team-d06-b", "摄影小队", USER_ID);
        jdbc.update("insert into team_members (team_id,user_id,role) values (?,?,?)", "team-d06-b", "user-a", "成员");
        jdbc.update("insert into team_reports (id,team_id,reporter_id,reason,status) values (?,?,?,?,?)",
                "report-d06-b", "team-d06-b", "user-a", "公告含广告", "待处理");
        jdbc.update("update activities set team_id = ? where id = ?",
                "team-d06-b", activityService.create(validActivityRequest(8), USER_ID).getId());

        TeamDto detail = teamService.adminDetail("team-d06-b");

        assertEquals("摄影小队", detail.getName());
        assertEquals("小满", detail.getOwnerNickname());
        assertEquals(2, detail.getMemberRecords().size());
        assertEquals(1, detail.getActivityRecords().size());
        assertEquals(1, detail.getReportRecords().size());
    }

    @Test
    @DisplayName("US-D06-4/6: 停用需原因，停用后不可新增成员、活动或内容，恢复后可继续")
    void shouldStopBlockNewTeamOperationsAndRestore() {
        createTeamFixture("team-d06-c", "山野小队", USER_ID);

        assertThrows(IllegalStateException.class,
                () -> teamService.stop("team-d06-c", " ", ADMIN_ID));

        teamService.stop("team-d06-c", "多次违规", ADMIN_ID);
        assertEquals("已停用", teamService.get("team-d06-c").getStatus());
        assertThrows(IllegalStateException.class, () -> teamService.join("team-d06-c", "user-a"));

        TeamOpsDtos.AnnouncementRequest announcement = new TeamOpsDtos.AnnouncementRequest();
        announcement.setContent("新公告");
        assertThrows(IllegalStateException.class,
                () -> teamService.announcement("team-d06-c", USER_ID, announcement));

        assertThrows(IllegalStateException.class, () -> {
            com.quju.dto.ActivityCreateRequest request = validActivityRequest(6);
            request.setTeamId("team-d06-c");
            request.setVisibility("TEAM");
            activityService.create(request, USER_ID);
        });

        teamService.restore("team-d06-c", ADMIN_ID);
        assertEquals("正常", teamService.get("team-d06-c").getStatus());
        TeamDto joined = teamService.join("team-d06-c", "user-a");
        assertEquals(2, joined.getMembers());
    }

    private void createTeamFixture(String teamId, String name, String ownerId) {
        jdbc.update("insert into teams (id,name,description,cover,tags,members_count,capacity,join_mode,active_now,status,owner_id) values (?,?,?,?,?,?,?,?,?,?,?)",
                teamId, name, "测试小队", "", "测试,运动", 1, 20, "公开加入", 1, "正常", ownerId);
        jdbc.update("insert into team_members (team_id,user_id,role) values (?,?,?)", teamId, ownerId, "队长");
        jdbc.update("insert into conversations (id,name,avatar,type,team_id,unread,last_message,last_time,online) values (?,?,?,?,?,?,?,?,?)",
                "cv-" + teamId, name, "", "小队", teamId, 0, "小队已创建", "刚刚", true);
    }
}
