package com.quju.service;

import com.quju.dto.AuthDtos;
import com.quju.dto.UserDto;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-D01 管理员登录
 *
 * 验收标准：
 * 1. 管理员账号由系统预创建，不提供注册入口
 * 2. 管理员使用账号密码登录后台
 * 3. 管理员可修改本人登录密码
 * 4. 登录失败时给出通用提示，不泄露账号状态
 */
class UsD01AdminLoginTest extends TestBase {

    @Test
    @DisplayName("US-D01-1: 管理员账号由系统预创建")
    void shouldHavePrebuiltAdminAccount() {
        UserDto admin = userService.findById(ADMIN_ID);
        assertEquals("管理员", admin.getRole());
        assertTrue(admin.getVerified());
    }

    @Test
    @DisplayName("US-D01-2: 管理员使用账号密码登录后台")
    void shouldLoginWithAdminCredentials() {
        AuthDtos.AuthResponse auth = userService.login(adminLoginRequest(ADMIN_EMAIL, USER_PASS));
        assertNotNull(auth.getToken());

        // requireAdmin 校验通过
        UserDto admin = userService.requireAdmin("Bearer " + auth.getToken());
        assertEquals(ADMIN_ID, admin.getId());

        // 普通用户无法通过 requireAdmin
        String userToken = loginAsUser();
        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.requireAdmin("Bearer " + userToken));
        assertTrue(ex.getMessage().contains("管理员权限"));
    }

    @Test
    @DisplayName("US-D01-3: 管理员可修改本人登录密码")
    void shouldAllowAdminToChangePassword() {
        AuthDtos.AuthResponse auth = userService.login(adminLoginRequest(ADMIN_EMAIL, USER_PASS));

        AuthDtos.ChangePasswordRequest pwReq = new AuthDtos.ChangePasswordRequest();
        pwReq.setOldPassword(USER_PASS);
        pwReq.setNewPassword("newAdminPass123");
        userService.changePassword("Bearer " + auth.getToken(), pwReq);

        // 新密码可登录
        AuthDtos.AuthResponse newLogin = userService.login(adminLoginRequest(ADMIN_EMAIL, "newAdminPass123"));
        assertNotNull(newLogin.getToken());

        // 旧密码失效
        assertThrows(IllegalStateException.class,
                () -> userService.login(adminLoginRequest(ADMIN_EMAIL, USER_PASS)));
    }

    @Test
    @DisplayName("US-D01-4: 登录失败时给出通用提示，不泄露账号状态")
    void shouldGiveGenericErrorMessage() {
        Exception ex1 = assertThrows(IllegalStateException.class,
                () -> userService.login(loginRequest("nonexistent@admin.com", "anypass")));
        assertTrue(ex1.getMessage().contains("密码错误"));

        Exception ex2 = assertThrows(IllegalStateException.class,
                () -> userService.login(adminLoginRequest(ADMIN_EMAIL, "wrongpass")));
        assertTrue(ex2.getMessage().contains("密码错误"));
    }
}
