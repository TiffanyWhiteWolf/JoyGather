package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.MessageDtos;
import com.quju.dto.TeamDto;
import com.quju.dto.TeamOpsDtos;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * US-C09 小队解散
 *
 * 验收标准：
 * 1. 仅队长可执行解散操作，需二次确认
 * 2. 解散后所有成员自动退出
 * 3. 群聊和队内活动停止使用
 * 4. 小队不再出现在发现列表中
 */
class UsC09TeamDissolveTest extends TestBase {
    private TeamService teamService;
    private MessageService messageService;

    @org.junit.jupiter.api.BeforeEach
    void initServices() {
        teamService = new TeamService(jdbc);
        messageService = new MessageService(jdbc);
    }

    @Test
    void shouldRequireLeaderAndSecondConfirmation() {
        TeamDto team = createTeamWithMember();

        IllegalStateException noConfirm = assertThrows(IllegalStateException.class,
                () -> teamService.dissolve(team.getId(), USER_ID, null));
        assertEquals("请二次确认解散小队", noConfirm.getMessage());

        IllegalStateException notLeader = assertThrows(IllegalStateException.class,
                () -> teamService.dissolve(team.getId(), "user-a", confirmation()));
        assertEquals("仅队长可解散小队", notLeader.getMessage());

        assertEquals("正常", teamService.get(team.getId()).getStatus());
        assertEquals(2, jdbc.queryForObject("select count(*) from team_members where team_id = ?", Integer.class, team.getId()));
    }

    @Test
    void shouldDissolveTeamAndRemoveItFromDiscovery() {
        TeamDto team = createTeamWithMember();
        ActivityCreateRequest request = validActivityRequest(10);
        request.setTeamId(team.getId());
        request.setVisibility("TEAM");
        String activityId = activityService.create(request, USER_ID).getId();

        teamService.dissolve(team.getId(), USER_ID, confirmation());

        TeamDto dissolved = teamService.get(team.getId());
        assertEquals("已停用", dissolved.getStatus());
        assertEquals("队长解散", dissolved.getStopReason());
        assertEquals(0, dissolved.getMembers());
        assertEquals(0, dissolved.getActiveNow());
        assertEquals(0, jdbc.queryForObject("select count(*) from team_members where team_id = ?", Integer.class, team.getId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from conversation_participants where conversation_id = ?", Integer.class, "cv-c09"));
        assertEquals(0, teamService.list("C09", false).size());
        assertEquals(1, teamService.list("C09", true).size());
        assertEquals("已下架", jdbc.queryForObject("select status from activities where id = ?", String.class, activityId));
        assertEquals("所属小队已解散", jdbc.queryForObject("select offline_reason from activities where id = ?", String.class, activityId));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where action = 'DISSOLVE_TEAM' and target_id = ?", Integer.class, team.getId()));
    }

    @Test
    void shouldStopDissolvedTeamConversationEvenIfParticipantRowRemains() {
        TeamDto team = createTeamWithMember();
        teamService.dissolve(team.getId(), USER_ID, confirmation());
        jdbc.update("insert into conversation_participants (conversation_id,user_id) values (?,?)", "cv-c09", USER_ID);

        MessageDtos.SendMessageRequest request = new MessageDtos.SendMessageRequest();
        request.setType("TEXT");
        request.setContent("还有人吗");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> messageService.send("cv-c09", USER_ID, request));
        assertEquals("小队已解散，群聊停止使用", ex.getMessage());
    }

    @Test
    void shouldRejectJoiningDissolvedTeam() {
        TeamDto team = createTeamWithMember();
        teamService.dissolve(team.getId(), USER_ID, confirmation());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> teamService.join(team.getId(), "user-b"));
        assertEquals("小队已停用，暂不可加入", ex.getMessage());
    }

    private TeamDto createTeamWithMember() {
        TeamDto request = new TeamDto();
        request.setName("C09解散测试小队");
        request.setDescription("用于测试小队解散");
        request.setTags(Collections.singletonList("C09"));
        request.setCapacity(10);
        request.setJoinMode("公开加入");
        request.setCover("cover.png");
        TeamDto team = teamService.create(request, USER_ID);
        teamService.join(team.getId(), "user-a");
        jdbc.update("update conversations set id = ? where team_id = ?", "cv-c09", team.getId());
        assertTrue(teamService.myTeamIds("user-a").contains(team.getId()));
        return teamService.get(team.getId());
    }

    private TeamOpsDtos.DissolveRequest confirmation() {
        TeamOpsDtos.DissolveRequest request = new TeamOpsDtos.DissolveRequest();
        request.setConfirmed(true);
        request.setConfirmationText("解散小队");
        return request;
    }
}
