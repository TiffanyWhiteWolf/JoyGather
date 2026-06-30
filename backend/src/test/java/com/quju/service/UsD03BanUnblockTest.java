package com.quju.service;

import com.quju.dto.AuthDtos;
import com.quju.dto.UserDto;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-D03 用户封禁与解封
 *
 * 验收标准：
 * 1. 封禁用户时须填写原因并选择封禁期限
 * 2. 封禁后用户无法登录平台
 * 3. 解封或期限到达后用户恢复正常使用
 * 4. 原有账号数据不受影响
 * 5. 所有操作保留操作人、时间、原因记录
 */
class UsD03BanUnblockTest extends TestBase {

    @Test
    @DisplayName("US-D03-1: 封禁用户时须填写原因并选择封禁期限")
    void shouldRequireReasonAndDurationForBan() {
        // 缺少原因或期限
        assertThrows(IllegalStateException.class,
                () -> userService.ban(USER_ID, "", "2026-12-31", ADMIN_ID));
        assertThrows(IllegalStateException.class,
                () -> userService.ban(USER_ID, "违规", "", ADMIN_ID));

        // 正常封禁
        userService.ban(USER_ID, "发布违规内容", "2026-12-31", ADMIN_ID);
        UserDto banned = userService.findById(USER_ID);
        assertEquals("已封禁", banned.getStatus());
        assertEquals("发布违规内容", banned.getBanReason());
        assertEquals("2026-12-31", banned.getBanUntil());
    }

    @Test
    @DisplayName("US-D03-2: 封禁后用户无法登录平台")
    void shouldDenyLoginAfterBan() {
        userService.ban(USER_ID, "违规", "2026-12-31", ADMIN_ID);

        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.login(loginRequest(USER_EMAIL, USER_PASS)));
        assertTrue(ex.getMessage().contains("封禁"));
    }

    @Test
    @DisplayName("US-D03-3: 解封或期限到达后用户恢复正常使用")
    void shouldRestoreAfterUnblock() {
        userService.ban(USER_ID, "违规", "2026-12-31", ADMIN_ID);
        userService.unblock(USER_ID, ADMIN_ID);

        UserDto unblocked = userService.findById(USER_ID);
        assertEquals("正常", unblocked.getStatus());
        assertNull(unblocked.getBanReason());
        assertNull(unblocked.getBanUntil());

        // 解封后可以登录
        AuthDtos.AuthResponse auth = userService.login(loginRequest(USER_EMAIL, USER_PASS));
        assertNotNull(auth.getToken());
    }

    @Test
    @DisplayName("US-D03-4: 原有账号数据不受影响")
    void shouldPreserveAccountData() {
        userService.ban(USER_ID, "违规", "2026-12-31", ADMIN_ID);
        userService.unblock(USER_ID, ADMIN_ID);

        AuthDtos.AuthResponse auth = userService.login(loginRequest(USER_EMAIL, USER_PASS));
        assertEquals(USER_ID, auth.getUser().getId());
        assertEquals(USER_EMAIL, auth.getUser().getEmail());
        assertEquals("小满", auth.getUser().getNickname());
    }

    @Test
    @DisplayName("US-D03-5: 所有操作保留操作人、时间、原因记录")
    void shouldRetainAuditLogs() {
        userService.ban(USER_ID, "原因A", "2026-12-31", ADMIN_ID);
        userService.unblock(USER_ID, ADMIN_ID);

        Integer logCount = jdbc.queryForObject(
                "select count(*) from audit_logs where target_id = ?", Integer.class, USER_ID);
        assertTrue(logCount >= 2); // BAN_USER + UNBLOCK_USER
    }
}
