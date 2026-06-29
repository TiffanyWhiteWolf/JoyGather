package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.ActivityDto;
import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.RegistrationResult;
import com.quju.service.ActivityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import javax.validation.Valid;

@RestController
@RequestMapping("/activities")
public class ActivityController {
    private final ActivityService activityService;
    public ActivityController(ActivityService activityService) { this.activityService = activityService; }

    @GetMapping
    public ApiResponse<List<ActivityDto>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String category) {
        return ApiResponse.success(activityService.findAll(keyword, category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ActivityDto>> detail(@PathVariable String id) {
        return activityService.findById(id)
                .map(item -> ResponseEntity.ok(ApiResponse.success(item)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ActivityDto>> create(@Valid @RequestBody ActivityCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(activityService.create(request)));
    }

    @PostMapping("/{id}/registrations")
    public ApiResponse<RegistrationResult> register(@PathVariable String id, @RequestParam String userId) {
        return ApiResponse.success(activityService.register(id, userId));
    }

    @DeleteMapping("/{id}/registrations/{userId}")
    public ApiResponse<RegistrationResult> cancel(@PathVariable String id, @PathVariable String userId) {
        return ApiResponse.success(activityService.cancel(id, userId));
    }
}
