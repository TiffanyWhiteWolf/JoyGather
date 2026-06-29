package com.quju.service;

import com.quju.dto.PlannerRequest;
import com.quju.dto.PlannerResponse;
import org.springframework.stereotype.Service;

@Service
public class PlannerService {
    private final IntegrationService integrationService;

    public PlannerService(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    public PlannerResponse generate(PlannerRequest request) {
        return integrationService.generatePlan(request);
    }
}
