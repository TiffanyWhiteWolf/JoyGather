package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.ActivityOpsDtos;
import com.quju.dto.RegistrationResult;
import com.quju.service.ActivityService;
import com.quju.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkins")
public class CheckinController {
    private final ActivityService activityService;
    private final UserService userService;

    public CheckinController(ActivityService activityService, UserService userService) {
        this.activityService = activityService;
        this.userService = userService;
    }

    @PostMapping("/scan")
    public ApiResponse<RegistrationResult> scan(@RequestBody ActivityOpsDtos.CheckinScanRequest request,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.scanCheckin(request.getCode(), userId, request.getLatitude(), request.getLongitude()));
    }
}
