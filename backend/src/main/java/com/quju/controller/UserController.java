package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.CommonDtos;
import com.quju.dto.UserDto;
import com.quju.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @GetMapping("/merchant-applications/me")
    public ApiResponse<List<Map<String, Object>>> myMerchantApplications(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(userService.myMerchantApplications(authorization));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> cancelMe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestBody CommonDtos.AccountCancellationRequest request) {
        userService.cancelAccount(authorization, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/search")
    public ApiResponse<List<UserDto>> search(@RequestParam String q,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto current = userService.optionalToken(authorization);
        String currentId = current == null ? null : current.getId();
        return ApiResponse.success(userService.search(q, "", "正常").stream()
                .filter(u -> !u.getId().equals(currentId))
                .collect(Collectors.toList()));
    }
}
