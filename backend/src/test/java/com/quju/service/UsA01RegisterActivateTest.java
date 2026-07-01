package com.quju.service;

import com.quju.dto.AuthDtos;
import com.quju.dto.UserDto;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-A01 用户注册与邮箱激活
 *
 * 验收标准：
 * 1. 用户填写邮箱、密码和确认密码后提交注册，系统创建未激活账号并发送激活链接
 * 2. 用户点击有效激活链接后账号激活，可正常登录
 * 3. 邮箱格式不合法、密码长度不足或两次密码不一致时，系统给出明确错误提示
 * 4. 同一邮箱不可重复注册
 * 5. 未激活账号不能登录或使用受限功能
 */
class UsA01RegisterActivateTest extends TestBase {

    @Test
    @DisplayName("US-A01-1: 提交注册后创建未激活账号")
    void shouldCreateInactiveAccountAndSendActivationLink() {
        AuthDtos.RegisterRequest req = registerRequest("newuser@test.com", USER_PASS, USER_PASS, "新用户");
        AuthDtos.ActivationResponse resp = userService.register(req, "http://localhost");
        assertNotNull(resp.getUserId());
        assertEquals("未激活", resp.getStatus());
        String token = jdbc.queryForObject("select activation_token from users where id = ?", String.class, resp.getUserId());
        assertNotNull(token);
    }

    @Test
    @DisplayName("US-A01-2: 有效激活链接激活账号后可正常登录")
    void shouldActivateAccountAndAllowLogin() {
        AuthDtos.RegisterRequest req = registerRequest("activate@test.com", USER_PASS, USER_PASS, "可激活用户");
        AuthDtos.ActivationResponse resp = userService.register(req, "http://localhost");

        String token = jdbc.queryForObject("select activation_token from users where id = ?", String.class, resp.getUserId());
        UserDto activated = userService.activate(token);
        assertNotNull(activated);
        assertEquals("可激活用户", activated.getNickname());

        AuthDtos.AuthResponse auth = userService.login(loginRequest("activate@test.com", USER_PASS));
        assertNotNull(auth.getToken());
    }

    @Test
    @DisplayName("US-A01-3: 邮箱格式、密码长度、两次密码不一致时给出错误提示")
    void shouldRejectInvalidInput() {
        // 邮箱格式不合法
        assertThrows(IllegalStateException.class,
                () -> userService.register(registerRequest("bad-email", USER_PASS, USER_PASS, "昵称A"), "http://localhost"));

        // 密码长度不足
        assertThrows(IllegalStateException.class,
                () -> userService.register(registerRequest("a@b.com", "123", "123", "昵称B"), "http://localhost"));

        // 两次密码不一致
        assertThrows(IllegalStateException.class,
                () -> userService.register(registerRequest("a@b.com", USER_PASS, "different", "昵称C"), "http://localhost"));
    }

    @Test
    @DisplayName("US-A01-4: 同一邮箱不可重复注册")
    void shouldRejectDuplicateEmail() {
        userService.register(registerRequest("dup@test.com", USER_PASS, USER_PASS, "首次注册"), "http://localhost");

        assertThrows(IllegalStateException.class,
                () -> userService.register(registerRequest("dup@test.com", USER_PASS, USER_PASS, "重复注册"), "http://localhost"));
    }

    @Test
    @DisplayName("US-A01-5: 未激活账号不能登录")
    void shouldDenyLoginForInactiveAccount() {
        userService.register(registerRequest("inactive@test.com", USER_PASS, USER_PASS, "未激活"), "http://localhost");

        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.login(loginRequest("inactive@test.com", USER_PASS)));
        assertTrue(ex.getMessage().contains("激活"));
    }
}
