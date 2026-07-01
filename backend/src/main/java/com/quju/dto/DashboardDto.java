package com.quju.dto;

import java.util.List;
import java.util.Map;

public class DashboardDto {
    private final Map<String, Integer> metrics;
    private final Map<String, Integer> categoryDistribution;
    private final List<Integer> trend;
    private final String adminName;

    public DashboardDto(Map<String, Integer> metrics, Map<String, Integer> categoryDistribution,
                        List<Integer> trend, String adminName) {
        this.metrics = metrics;
        this.categoryDistribution = categoryDistribution;
        this.trend = trend;
        this.adminName = adminName;
    }
    public Map<String, Integer> getMetrics() { return metrics; }
    public Map<String, Integer> getCategoryDistribution() { return categoryDistribution; }
    public List<Integer> getTrend() { return trend; }
    public String getAdminName() { return adminName; }
}
