package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.CommonDtos;
import com.quju.service.IntegrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/map")
public class MapController {
    private final IntegrationService integrationService;

    public MapController(IntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping("/places")
    public ApiResponse<List<CommonDtos.GeoPoint>> places(@RequestParam String keyword,
                                                         @RequestParam(required = false) String city) {
        return ApiResponse.success(integrationService.searchAmap(keyword, city));
    }

    @GetMapping("/reverse")
    public ApiResponse<CommonDtos.GeoPoint> reverse(@RequestParam BigDecimal longitude,
                                                    @RequestParam BigDecimal latitude) {
        return ApiResponse.success(integrationService.reverseGeocode(longitude, latitude));
    }
}
