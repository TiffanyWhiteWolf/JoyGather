package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.DashboardDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {
    @GetMapping("/dashboard")
    public ApiResponse<DashboardDto> dashboard() {
        Map<String, Integer> metrics = new LinkedHashMap<String, Integer>();
        metrics.put("users", 28642); metrics.put("monthlyActivities", 1284);
        metrics.put("activeTeams", 386); metrics.put("pendingReviews", 12);
        Map<String, Integer> distribution = new LinkedHashMap<String, Integer>();
        distribution.put("城市探索", 32); distribution.put("户外运动", 26);
        distribution.put("兴趣聚会", 24); distribution.put("其他", 18);
        return ApiResponse.success(new DashboardDto(metrics, distribution));
    }
}
