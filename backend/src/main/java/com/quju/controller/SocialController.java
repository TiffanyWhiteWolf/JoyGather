package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.CommonDtos;
import com.quju.dto.SocialDtos;
import com.quju.service.SocialService;
import com.quju.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class SocialController {
    private final SocialService socialService;
    private final UserService userService;

    public SocialController(SocialService socialService, UserService userService) {
        this.socialService = socialService;
        this.userService = userService;
    }

    @GetMapping("/friends")
    public ApiResponse<List<Map<String, Object>>> friends(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(socialService.friends(userService.requireToken(authorization).getId()));
    }

    @GetMapping("/friends/requests")
    public ApiResponse<List<Map<String, Object>>> requests(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(socialService.requests(userService.requireToken(authorization).getId()));
    }

    @PostMapping("/friends/requests")
    public ApiResponse<Void> requestFriend(@RequestBody SocialDtos.FriendRequestInput request,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        socialService.requestFriend(userService.requireToken(authorization).getId(), request);
        return ApiResponse.success(null);
    }

    @PostMapping("/friends/requests/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable String id,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        socialService.handleRequest(id, userService.requireToken(authorization).getId(), true);
        return ApiResponse.success(null);
    }

    @PostMapping("/friends/requests/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable String id,
                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        socialService.handleRequest(id, userService.requireToken(authorization).getId(), false);
        return ApiResponse.success(null);
    }

    @PutMapping("/friends/{id}")
    public ApiResponse<Void> updateMeta(@PathVariable String id,
                                        @RequestBody SocialDtos.FriendMetaRequest request,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        socialService.updateFriendMeta(userService.requireToken(authorization).getId(), id, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/follows/{id}")
    public ApiResponse<Void> follow(@PathVariable String id,
                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        socialService.follow(userService.requireToken(authorization).getId(), id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/follows/{id}")
    public ApiResponse<Void> unfollow(@PathVariable String id,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        socialService.unfollow(userService.requireToken(authorization).getId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/blocks/{id}")
    public ApiResponse<Void> block(@PathVariable String id,
                                   @RequestBody(required = false) CommonDtos.GenericRequest request,
                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        socialService.block(userService.requireToken(authorization).getId(), id, request == null ? "" : request.getReason());
        return ApiResponse.success(null);
    }
}
