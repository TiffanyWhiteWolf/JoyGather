package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsB01bMapLocationTest extends TestBase {

    @Test
    void persistsBeijingMapSelectionAndReturnsItInDetail() {
        ActivityCreateRequest request = validActivityRequest(20);
        request.setLocation("奥林匹克森林公园南门");
        request.setCity("北京");
        request.setDistrict("朝阳区");
        request.setLongitude(new BigDecimal("116.392891"));
        request.setLatitude(new BigDecimal("40.015120"));

        ActivityDto created = activityService.create(request, USER_ID);
        ActivityDto detail = activityService.findById(created.getId()).get();

        assertEquals("北京", detail.getCity());
        assertEquals("朝阳区", detail.getDistrict());
        assertEquals(new BigDecimal("116.392891"), detail.getLongitude());
        assertEquals(new BigDecimal("40.015120"), detail.getLatitude());
    }

    @Test
    void filtersPublishedActivitiesBySelectedCity() {
        ActivityCreateRequest request = validActivityRequest(20);
        request.setLocation("国家图书馆总馆南区东门");
        request.setCity("北京");
        request.setDistrict("海淀区");
        request.setLongitude(new BigDecimal("116.325190"));
        request.setLatitude(new BigDecimal("39.943047"));
        activityService.create(request, USER_ID);

        List<ActivityDto> beijing = activityService.findAll(null, null, null, "北京", null,
                null, null, null, null, null, null, null, null, "latest", null, null);
        List<ActivityDto> hangzhou = activityService.findAll(null, null, null, "杭州", null,
                null, null, null, null, null, null, null, null, "latest", null, null);

        assertEquals(1, beijing.size());
        assertEquals(0, hangzhou.size());
    }

    @Test
    void manualAddressFallsBackToSelectedCityCenterWhenMapIsUnavailable() {
        ActivityCreateRequest request = validActivityRequest(20);
        request.setLocation("北京市东城区东华门大街 1 号");
        request.setCity("北京");
        request.setDistrict("东城区");
        request.setLongitude(null);
        request.setLatitude(null);

        ActivityDto created = activityService.create(request, USER_ID);

        assertEquals(new BigDecimal("116.407400"), created.getLongitude());
        assertEquals(new BigDecimal("39.904200"), created.getLatitude());
    }

    @Test
    void rejectsUnsupportedActivityCity() {
        ActivityCreateRequest request = validActivityRequest(20);
        request.setCity("上海");
        assertThrows(IllegalStateException.class, () -> activityService.create(request, USER_ID));
    }
}
