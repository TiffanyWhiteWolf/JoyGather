package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.ActivityDto;
import com.quju.dto.AdminDtos;
import com.quju.dto.DashboardDto;
import com.quju.dto.ReviewTaskDto;
import com.quju.dto.ReviewDetailDto;
import com.quju.dto.TeamDto;
import com.quju.dto.UserDto;
import com.quju.service.ActivityService;
import com.quju.service.AdminService;
import com.quju.service.TeamService;
import com.quju.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;
    private final ActivityService activityService;
    private final TeamService teamService;
    private final UserService userService;

    public AdminController(AdminService adminService, ActivityService activityService, TeamService teamService, UserService userService) {
        this.adminService = adminService;
        this.activityService = activityService;
        this.teamService = teamService;
        this.userService = userService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardDto> dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        return ApiResponse.success(adminService.dashboard(admin.getNickname()));
    }

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewTaskDto>> reviews(@RequestParam(required = false) String query,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(required = false) String status,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return ApiResponse.success(adminService.reviews(query, type, status));
    }

    @GetMapping("/reviews/{id}")
    public ApiResponse<ReviewDetailDto> reviewDetail(@PathVariable String id,
                                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return ApiResponse.success(adminService.reviewDetail(id));
    }

    @PostMapping("/reviews/{id}/approve")
    public ApiResponse<Void> approveReview(@PathVariable String id, @RequestBody(required = false) AdminDtos.ReasonRequest request,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        adminService.review(id, "已通过", request == null ? "" : request.getReason(), admin.getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/reviews/{id}/reject")
    public ApiResponse<Void> rejectReview(@PathVariable String id, @RequestBody AdminDtos.ReasonRequest request,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        adminService.review(id, "已驳回", request.getReason(), admin.getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/reviews/{id}/changes")
    public ApiResponse<Void> requireChanges(@PathVariable String id, @RequestBody AdminDtos.ReasonRequest request,
                                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        adminService.review(id, "要求修改", request.getReason(), admin.getId());
        return ApiResponse.success(null);
    }

    @GetMapping("/users")
    public ApiResponse<List<UserDto>> users(@RequestParam(required = false) String query,
                                            @RequestParam(required = false) String role,
                                            @RequestParam(required = false) String status,
                                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return ApiResponse.success(adminService.users(query, role, status));
    }

    @GetMapping("/merchant-applications")
    public ApiResponse<List<Map<String, Object>>> merchantApplications(@RequestParam(required = false) String status,
                                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return ApiResponse.success(adminService.merchantApplications(status));
    }

    @PostMapping("/users/{id}/ban")
    public ApiResponse<Void> ban(@PathVariable String id, @RequestBody AdminDtos.ReasonRequest request,
                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        adminService.ban(id, request.getReason(), request.getUntil(), admin.getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/users/{id}/unblock")
    public ApiResponse<Void> unblock(@PathVariable String id, @RequestBody(required = false) AdminDtos.ReasonRequest request,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        adminService.unblock(id, admin.getId());
        return ApiResponse.success(null);
    }

    @GetMapping("/activities")
    public ApiResponse<List<ActivityDto>> activities(@RequestParam(required = false) String query,
                                                     @RequestParam(required = false) String status,
                                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return ApiResponse.success(adminService.activities(query, status));
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<ActivityDto> activityDetail(@PathVariable String id,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return ApiResponse.success(adminService.activityDetail(id));
    }

    @PostMapping("/activities/{id}/offline")
    public ApiResponse<Void> offlineActivity(@PathVariable String id, @RequestBody AdminDtos.ReasonRequest request,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        activityService.takeOffline(id, request.getReason(), admin.getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/activities/{id}/restore")
    public ApiResponse<Void> restoreActivity(@PathVariable String id, @RequestBody(required = false) AdminDtos.ReasonRequest request,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        activityService.restore(id, admin.getId());
        return ApiResponse.success(null);
    }

    @GetMapping("/teams")
    public ApiResponse<List<TeamDto>> teams(@RequestParam(required = false) String query,
                                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return ApiResponse.success(teamService.list(query, true));
    }

    @GetMapping("/teams/{id}")
    public ApiResponse<TeamDto> teamDetail(@PathVariable String id,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        userService.requireAdmin(authorization);
        return ApiResponse.success(teamService.adminDetail(id));
    }

    @PostMapping("/teams/{id}/stop")
    public ApiResponse<Void> stopTeam(@PathVariable String id, @RequestBody AdminDtos.ReasonRequest request,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        teamService.stop(id, request.getReason(), admin.getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/teams/{id}/restore")
    public ApiResponse<Void> restoreTeam(@PathVariable String id, @RequestBody(required = false) AdminDtos.ReasonRequest request,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        teamService.restore(id, admin.getId());
        return ApiResponse.success(null);
    }

    @GetMapping("/notifications")
    public ApiResponse<List<Map<String, Object>>> notifications(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        return ApiResponse.success(adminService.publishedNotifications(admin.getId()));
    }

    @PostMapping("/notifications")
    public ApiResponse<Void> publishNotification(@RequestBody AdminDtos.NotificationRequest request,
                                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserDto admin = userService.requireAdmin(authorization);
        adminService.publishNotification(admin.getId(), request);
        return ApiResponse.success(null);
    }
}
