package com.quju.service;

import com.quju.dto.AuthDtos;
import com.quju.dto.CommonDtos;
import com.quju.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-A04 账号注销
 *
 * 验收标准：
 * 1. 用户输入当前密码和确认文案后可以注销账号
 * 2. 注销后所有会话失效，原邮箱和昵称可重新注册
 * 3. 密码错误或管理员账号不允许注销
 */
class UsA04AccountCancellationTest extends TestBase {

    @Test
    @DisplayName("US-A04-1: 注销账号后匿名化资料并失效会话")
    void shouldCancelAccountAndInvalidateSessions() {
        String token = loginAsUser();
        seedCancelableContent();

        userService.cancelAccount("Bearer " + token, cancelRequest(USER_PASS));

        UserDto deleted = userService.findById(USER_ID);
        assertEquals("已注销", deleted.getStatus());
        assertTrue(deleted.getEmail().startsWith("deleted-" + USER_ID));
        assertTrue(deleted.getNickname().startsWith("已注销用户-"));
        assertEquals(0, deleted.getCredit());

        Exception tokenError = assertThrows(IllegalStateException.class,
                () -> userService.requireToken("Bearer " + token));
        assertTrue(tokenError.getMessage().contains("登录已过期"));

        Integer cancelled = jdbc.queryForObject(
                "select count(*) from registrations where user_id = ? and status = '已取消'",
                Integer.class, USER_ID);
        assertEquals(1, cancelled);
        Integer joined = jdbc.queryForObject("select joined_count from activities where id = 'cancel-act'", Integer.class);
        assertEquals(0, joined);
        String activityStatus = jdbc.queryForObject("select status from activities where id = 'own-act'", String.class);
        assertEquals("已下架", activityStatus);
    }

    @Test
    @DisplayName("US-A04-2: 注销后原邮箱和昵称可重新注册")
    void shouldReleaseEmailAndNicknameAfterCancellation() {
        String token = loginAsUser();
        userService.cancelAccount("Bearer " + token, cancelRequest(USER_PASS));

        AuthDtos.ActivationResponse response = userService.register(registerRequest(USER_EMAIL, USER_PASS, USER_PASS, "小满"));
        assertNotNull(response.getUserId());
        assertNotEquals(USER_ID, response.getUserId());
    }

    @Test
    @DisplayName("US-A04-3: 当前密码错误时拒绝注销")
    void shouldRejectWrongPassword() {
        String token = loginAsUser();

        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.cancelAccount("Bearer " + token, cancelRequest("wrongpass")));
        assertTrue(ex.getMessage().contains("密码"));
        assertEquals("正常", userService.findById(USER_ID).getStatus());
    }

    @Test
    @DisplayName("US-A04-4: 管理员账号不允许自助注销")
    void shouldRejectAdminCancellation() {
        String token = userService.login(adminLoginRequest(ADMIN_EMAIL, USER_PASS)).getToken();

        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.cancelAccount("Bearer " + token, cancelRequest(USER_PASS)));
        assertTrue(ex.getMessage().contains("管理员"));
        assertEquals("正常", userService.findById(ADMIN_ID).getStatus());
    }

    private CommonDtos.AccountCancellationRequest cancelRequest(String password) {
        CommonDtos.AccountCancellationRequest request = new CommonDtos.AccountCancellationRequest();
        request.setPassword(password);
        request.setConfirmText("注销账号");
        request.setReason("不再使用");
        return request;
    }

    private void seedCancelableContent() {
        jdbc.update("insert into activities (id,title,summary,description,category,cover,date_label,time_label,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,safety_note,min_age,join_fields) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "cancel-act", "报名活动", "测试", "测试", "学习交流", "", "2026-08-01", "14:00 - 16:00", "杭州", "西湖区", 0, 120.1, 30.1, 0, 10, 1, "报名中", "user-a", false, "", 0, "");
        jdbc.update("insert into registrations (id,activity_id,user_id,status,queue_position) values (?,?,?,?,?)",
                "cancel-reg", "cancel-act", USER_ID, "已报名", 0);
        jdbc.update("insert into activities (id,title,summary,description,category,cover,date_label,time_label,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,safety_note,min_age,join_fields) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "own-act", "我的活动", "测试", "测试", "学习交流", "", "2026-08-02", "14:00 - 16:00", "杭州", "西湖区", 0, 120.1, 30.1, 0, 10, 0, "报名中", USER_ID, false, "", 0, "");
    }
}
