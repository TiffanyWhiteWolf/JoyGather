package com.quju.dto;

import java.util.Map;

public class DashboardDto {
    private final Map<String, Integer> metrics;
    private final Map<String, Integer> categoryDistribution;

    public DashboardDto(Map<String, Integer> metrics, Map<String, Integer> categoryDistribution) {
        this.metrics = metrics; this.categoryDistribution = categoryDistribution;
    }
    public Map<String, Integer> getMetrics() { return metrics; }
    public Map<String, Integer> getCategoryDistribution() { return categoryDistribution; }
}
