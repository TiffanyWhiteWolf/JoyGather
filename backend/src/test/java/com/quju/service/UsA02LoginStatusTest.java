package com.quju.service;

import com.quju.dto.AuthDtos;
import com.quju.dto.UserDto;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-A02 用户登录与状态校验
 *
 * 验收标准：
 * 1. 输入正确邮箱和密码后登录成功，跳转至首页
 * 2. 输入错误时提示"邮箱或密码错误"
 * 3. 账号未激活时提示"请先激活账号"
 * 4. 账号被封禁时提示"账号已被封禁"并显示封禁原因和期限
 * 5. 登录成功后 Token/Session 有效期内无需重复登录
 */
class UsA02LoginStatusTest extends TestBase {

    @Test
    @DisplayName("US-A02-1: 正确邮箱和密码登录成功")
    void shouldLoginWithCorrectCredentials() {
        AuthDtos.AuthResponse resp = userService.login(loginRequest(USER_EMAIL, USER_PASS));
        assertNotNull(resp.getToken());
        assertEquals(USER_ID, resp.getUser().getId());
    }

    @Test
    @DisplayName("US-A02-2: 错误密码或不存在邮箱提示'邮箱或密码错误'")
    void shouldShowGenericErrorForWrongCredentials() {
        Exception ex1 = assertThrows(IllegalStateException.class,
                () -> userService.login(loginRequest(USER_EMAIL, "wrongpass")));
        assertTrue(ex1.getMessage().contains("密码错误"));

        Exception ex2 = assertThrows(IllegalStateException.class,
                () -> userService.login(loginRequest("nobody@x.com", USER_PASS)));
        assertTrue(ex2.getMessage().contains("密码错误"));
    }

    @Test
    @DisplayName("US-A02-3: 未激活账号提示'请先激活账号'")
    void shouldPromptActivationForInactiveAccount() {
        registerAndGetId("notyet@test.com", "未激活用户");

        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.login(loginRequest("notyet@test.com", USER_PASS)));
        assertTrue(ex.getMessage().contains("激活"));
    }

    @Test
    @DisplayName("US-A02-4: 封禁账号提示'账号已被封禁'")
    void shouldShowBanInfoForBannedAccount() {
        jdbc.update("update users set status = '已封禁', ban_reason = '违规行为', ban_until = ? where id = ?",
                java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(30)), USER_ID);

        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.login(loginRequest(USER_EMAIL, USER_PASS)));
        assertTrue(ex.getMessage().contains("封禁"));
        assertTrue(ex.getMessage().contains("违规行为"));
    }

    @Test
    @DisplayName("US-A02-5: Token 有效期内无需重复登录")
    void shouldKeepSessionValidWithinExpiry() {
        AuthDtos.AuthResponse resp = userService.login(loginRequest(USER_EMAIL, USER_PASS));
        UserDto fromToken = userService.requireToken("Bearer " + resp.getToken());
        assertEquals(USER_ID, fromToken.getId());
    }
}
