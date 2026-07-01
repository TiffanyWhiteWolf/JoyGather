package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.ActivityDto;
import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityOpsDtos;
import com.quju.dto.RegistrationResult;
import com.quju.service.ActivityService;
import com.quju.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.math.BigDecimal;
import java.util.Map;
import javax.validation.Valid;

@RestController
@RequestMapping("/activities")
public class ActivityController {
    private final ActivityService activityService;
    private final UserService userService;
    public ActivityController(ActivityService activityService, UserService userService) {
        this.activityService = activityService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<ActivityDto>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) String categories,
                                                @RequestParam(required = false) String city,
                                                @RequestParam(required = false) String fee,
                                                @RequestParam(required = false) String time,
                                                @RequestParam(required = false) BigDecimal distance,
                                                @RequestParam(required = false) BigDecimal lat,
                                                @RequestParam(required = false) BigDecimal lng,
                                                @RequestParam(required = false) BigDecimal minLng,
                                                @RequestParam(required = false) BigDecimal maxLng,
                                                @RequestParam(required = false) BigDecimal minLat,
                                                @RequestParam(required = false) BigDecimal maxLat,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return ApiResponse.success(activityService.findAll(keyword, category, categories, city, fee, time, distance, lat, lng, minLng, maxLng, minLat, maxLat, sort, page, size));
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<ActivityDto>> recommendations(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Integer limit) {
        com.quju.dto.UserDto user = userService.optionalToken(authorization);
        return ApiResponse.success(activityService.recommendations(
                user == null ? null : user.getInterests(), limit));
    }

    @GetMapping("/my")
    public ApiResponse<List<ActivityDto>> myActivities(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.findMyActivities(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ActivityDto>> detail(@PathVariable String id) {
        return activityService.findById(id)
                .map(item -> ResponseEntity.ok(ApiResponse.success(item)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ActivityDto>> create(@Valid @RequestBody ActivityCreateRequest request,
                                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(activityService.create(request, userId)));
    }

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<ActivityDto>> saveDraft(@RequestBody ActivityCreateRequest request,
                                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(activityService.saveDraft(request, userId)));
    }

    @PutMapping("/drafts/{id}")
    public ApiResponse<ActivityDto> updateDraft(@PathVariable String id,
                                                @RequestBody ActivityCreateRequest request,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.updateDraft(id, request, userId));
    }

    @GetMapping("/drafts")
    public ApiResponse<List<ActivityDto>> drafts(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.findDrafts(userId));
    }

    @DeleteMapping("/drafts/{id}")
    public ApiResponse<Void> deleteDraft(@PathVariable String id,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        activityService.deleteDraft(id, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ActivityDto> submitDraft(@PathVariable String id,
                                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.submitDraft(id, userId));
    }

    @PostMapping("/{id}/clone")
    public ApiResponse<ActivityDto> clone(@PathVariable String id,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.cloneAsDraft(id, userId));
    }

    @PostMapping("/{id}/registrations")
    public ApiResponse<RegistrationResult> register(@PathVariable String id,
                                                    @RequestBody(required = false) ActivityOpsDtos.RegistrationRequest request,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        String actualUserId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.register(id, actualUserId, request == null ? null : request.getFields()));
    }

    @DeleteMapping("/{id}/registrations/me")
    public ApiResponse<RegistrationResult> cancel(@PathVariable String id,
                                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.cancel(id, userId));
    }

    @GetMapping("/{id}/participants")
    public ApiResponse<List<Map<String, Object>>> participants(@PathVariable String id) {
        return ApiResponse.success(activityService.participants(id));
    }

    @GetMapping("/registrations/me")
    public ApiResponse<Map<String, String>> myRegistrations(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.myRegistrationStatus(userId));
    }

    @GetMapping("/{id}/registrations")
    public ApiResponse<List<ActivityOpsDtos.RegistrationManagementDto>> registrations(@PathVariable String id,
                                                                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.registrationManagement(id, userId));
    }

    @PostMapping("/{id}/waitlist/confirm")
    public ApiResponse<RegistrationResult> confirmWaitlist(@PathVariable String id,
                                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.confirmWaitlist(id, userId));
    }

    @PostMapping("/{id}/checkins/qr")
    public ApiResponse<ActivityOpsDtos.CheckinCodeResponse> checkinCode(@PathVariable String id,
                                                                        @RequestBody(required = false) ActivityOpsDtos.CheckinScanRequest request,
                                                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.createCheckinCode(id, userId, request != null && Boolean.TRUE.equals(request.getLocationRequired())));
    }

    @PostMapping("/{id}/summaries")
    public ApiResponse<ActivityOpsDtos.SummaryDto> summary(@PathVariable String id,
                                                           @RequestBody ActivityOpsDtos.SummaryRequest request,
                                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.publishSummary(id, userId, request));
    }

    @PostMapping("/{id}/summaries/classify")
    public ApiResponse<ActivityOpsDtos.SummaryClassificationDto> classifySummaryImages(
            @PathVariable String id,
            @RequestBody ActivityOpsDtos.SummaryClassifyRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.classifySummaryImages(id, userId, request));
    }

    @GetMapping("/{id}/after-event")
    public ApiResponse<ActivityOpsDtos.AfterEventDto> afterEvent(
            @PathVariable String id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = authorization == null || authorization.trim().isEmpty()
                ? null : userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.afterEvent(id, userId));
    }

    @PostMapping("/{id}/reviews")
    public ApiResponse<ActivityOpsDtos.ReviewDto> review(@PathVariable String id,
                                                         @RequestBody ActivityOpsDtos.ReviewRequest request,
                                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(activityService.reviewActivity(id, userId, request));
    }
}
