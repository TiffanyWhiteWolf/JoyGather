package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.AuthDtos;
import com.quju.dto.UserDto;
import com.quju.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.ActivationResponse> register(@RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.success(userService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(@RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.logout(authorization);
        return ApiResponse.success(null);
    }

    @GetMapping("/activate")
    public ApiResponse<UserDto> activate(@RequestParam String token) {
        return ApiResponse.success(userService.activate(token));
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(userService.requireToken(authorization));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestBody AuthDtos.ChangePasswordRequest request) {
        userService.changePassword(authorization, request);
        return ApiResponse.success(null);
    }
}
