package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.CommonDtos;
import com.quju.dto.UserDto;
import com.quju.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/me")
    public ApiResponse<UserDto> updateMe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @RequestBody CommonDtos.ProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(authorization, request));
    }

    @GetMapping("/nickname-available")
    public ApiResponse<CommonDtos.BooleanResponse> nicknameAvailable(@RequestParam String nickname,
                                                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto current = userService.optionalToken(authorization);
        return ApiResponse.success(new CommonDtos.BooleanResponse(userService.nicknameAvailable(nickname, current == null ? null : current.getId())));
    }

    @PostMapping("/merchant-applications")
    public ApiResponse<Void> merchantApplication(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @RequestBody CommonDtos.MerchantApplicationRequest request) {
        userService.submitMerchantApplication(authorization, request);
        return ApiResponse.success(null);
    }
}
