package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.TeamDto;
import com.quju.dto.TeamOpsDtos;
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
@RequestMapping("/teams")
public class TeamController {
    private final TeamService teamService;
    private final UserService userService;

    public TeamController(TeamService teamService, UserService userService) {
        this.teamService = teamService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<TeamDto>> list(@RequestParam(required = false) String query) {
        return ApiResponse.success(teamService.list(query, false));
    }

    @PostMapping
    public ApiResponse<TeamDto> create(@RequestBody TeamDto request,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(teamService.create(request, userService.requireToken(authorization).getId()));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<TeamDto> join(@PathVariable String id,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        String actualUserId = userService.requireToken(authorization).getId();
        return ApiResponse.success(teamService.join(id, actualUserId));
    }

    @GetMapping("/memberships/me")
    public ApiResponse<List<String>> mine(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(teamService.myTeamIds(userId));
    }

    @GetMapping("/{id}/join-requests")
    public ApiResponse<List<Map<String, Object>>> joinRequests(@PathVariable String id,
                                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(teamService.joinRequests(id, userId));
    }

    @PostMapping("/{id}/join-requests/{requestId}/approve")
    public ApiResponse<TeamDto> approveJoin(@PathVariable String id,
                                            @PathVariable String requestId,
                                            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(teamService.approveJoin(id, requestId, userId));
    }

    @PostMapping("/{id}/join-requests/{requestId}/reject")
    public ApiResponse<Void> rejectJoin(@PathVariable String id,
                                        @PathVariable String requestId,
                                        @RequestBody(required = false) TeamOpsDtos.TeamContentRequest request,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        teamService.rejectJoin(id, requestId, userId, request == null ? "" : request.getReason());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/announcements")
    public ApiResponse<Void> announcement(@PathVariable String id,
                                          @RequestBody TeamOpsDtos.AnnouncementRequest request,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        teamService.announcement(id, userId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/polls")
    public ApiResponse<Void> poll(@PathVariable String id,
                                  @RequestBody TeamOpsDtos.PollRequest request,
                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        teamService.poll(id, userId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/files")
    public ApiResponse<Void> addFile(@PathVariable String id,
                                     @RequestBody TeamOpsDtos.TeamContentRequest request,
                                     @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        teamService.addFile(id, userId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/albums")
    public ApiResponse<Void> addAlbum(@PathVariable String id,
                                      @RequestBody TeamOpsDtos.TeamContentRequest request,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        teamService.addAlbumPhoto(id, userId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/leaderboard")
    public ApiResponse<List<Map<String, Object>>> leaderboard(@PathVariable String id) {
        return ApiResponse.success(teamService.leaderboard(id));
    }

    @PostMapping("/{id}/dissolve")
    public ApiResponse<Void> dissolve(@PathVariable String id,
                                      @RequestBody(required = false) TeamOpsDtos.DissolveRequest request,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        teamService.dissolve(id, userId, request);
        return ApiResponse.success(null);
    }
}
