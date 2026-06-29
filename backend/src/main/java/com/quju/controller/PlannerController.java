package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.PlannerRequest;
import com.quju.dto.PlannerResponse;
import com.quju.service.PlannerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/ai")
public class PlannerController {
    private final PlannerService plannerService;
    public PlannerController(PlannerService plannerService) { this.plannerService = plannerService; }

    @PostMapping("/plans")
    public ApiResponse<PlannerResponse> generate(@Valid @RequestBody PlannerRequest request) {
        return ApiResponse.success(plannerService.generate(request));
    }
}
