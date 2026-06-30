package com.quju.service;

import com.quju.dto.CommonDtos;
import com.quju.dto.UserDto;
import org.junit.jupiter.api.*;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-A03 个人资料管理
 *
 * 验收标准：
 * 1. 可上传或更换头像，支持常见图片格式
 * 2. 昵称全平台唯一，修改时系统实时校验是否被占用
 * 3. 兴趣标签可从平台预设标签中多选
 * 4. 所有资料修改成功后即时生效并在个人主页展示
 * 5. 必填项缺失或格式不合法时拒绝保存并提示
 */
class UsA03ProfileTest extends TestBase {

    @Test
    @DisplayName("US-A03-1: 可上传或更换头像")
    void shouldUpdateAvatar() {
        String token = loginAsUser();
        CommonDtos.ProfileRequest req = profileWith("unique_av", "https://example.com/avatar.jpg", "男", "2000-01-15", "北京");
        UserDto updated = userService.updateProfile("Bearer " + token, req);
        assertEquals("https://example.com/avatar.jpg", updated.getAvatar());
    }

    @Test
    @DisplayName("US-A03-2: 昵称全平台唯一，修改时校验是否被占用")
    void shouldValidateNicknameUniqueness() {
        String token = loginAsUser();
        registerAndGetId("other@test.com", "taken_nick");

        // 当前用户试图改成已被占用的昵称
        CommonDtos.ProfileRequest dup = profileWith("taken_nick", null, null, null, null);
        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.updateProfile("Bearer " + token, dup));
        assertTrue(ex.getMessage().contains("被占用"));

        // 当前用户自己的昵称可用
        assertTrue(userService.nicknameAvailable("unique_nick", USER_ID));
    }

    @Test
    @DisplayName("US-A03-3: 兴趣标签可从平台预设标签中多选")
    void shouldSupportMultiSelectInterests() {
        String token = loginAsUser();
        CommonDtos.ProfileRequest req = profileWith("tags_user", null, null, null, null);
        req.setInterests(Arrays.asList("运动健身", "户外徒步", "桌游聚会"));

        UserDto updated = userService.updateProfile("Bearer " + token, req);
        assertEquals(3, updated.getInterests().size());
        assertTrue(updated.getInterests().contains("桌游聚会"));
    }

    @Test
    @DisplayName("US-A03-4: 所有资料修改成功后即时生效")
    void shouldApplyProfileChangesImmediately() {
        String token = loginAsUser();
        CommonDtos.ProfileRequest req = profileWith("instant_user", null, "女", "1998-06-20", "上海");
        req.setBio("热爱户外运动");

        userService.updateProfile("Bearer " + token, req);
        UserDto current = userService.findById(USER_ID);

        assertEquals("上海", current.getCity());
        assertEquals("热爱户外运动", current.getBio());
        assertEquals("女", current.getGender());
    }

    @Test
    @DisplayName("US-A03-5: 必填项缺失或格式不合法时拒绝保存并提示")
    void shouldRejectMissingRequiredFields() {
        String token = loginAsUser();
        CommonDtos.ProfileRequest emptyNick = new CommonDtos.ProfileRequest();
        emptyNick.setNickname("");
        emptyNick.setAvatar("https://example.com/avatar.jpg");

        Exception ex = assertThrows(IllegalStateException.class,
                () -> userService.updateProfile("Bearer " + token, emptyNick));
        assertTrue(ex.getMessage().contains("昵称"));
    }

    private CommonDtos.ProfileRequest profileWith(String nickname, String avatar, String gender, String birthday, String city) {
        CommonDtos.ProfileRequest req = new CommonDtos.ProfileRequest();
        req.setNickname(nickname);
        if (avatar != null) req.setAvatar(avatar);
        if (gender != null) req.setGender(gender);
        if (birthday != null) req.setBirthday(birthday);
        if (city != null) req.setCity(city);
        return req;
    }
}
