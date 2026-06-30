package com.quju.service;

import com.quju.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-D04 商家申请审核
 */
class UsD04MerchantReviewTest extends TestBase {

    @Test
    @DisplayName("US-D04-1: 管理员可查看待审核商家申请的资质材料")
    void shouldShowMerchantApplicationMaterials() {
        createMerchantReview("merchant-d04-a", "rv-d04-a", USER_ID, "流木咖啡", "license-a.pdf", "https://cdn.test/license-a.pdf");

        List<Map<String, Object>> applications = adminService.merchantApplications("待审核");

        Map<String, Object> row = applications.stream()
                .filter(item -> "merchant-d04-a".equals(item.get("ID")) || "merchant-d04-a".equals(item.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("license-a.pdf", value(row, "LICENSE_NAME", "license_name"));
        assertEquals("https://cdn.test/license-a.pdf", value(row, "LICENSE_URL", "license_url"));
    }

    @Test
    @DisplayName("US-D04-2/4: 审核通过后用户获得商家身份标识")
    void shouldApproveMerchantAndGrantMerchantRole() {
        createMerchantReview("merchant-d04-b", "rv-d04-b", USER_ID, "湖滨咖啡", "license-b.pdf", "https://cdn.test/license-b.pdf");

        adminService.review("rv-d04-b", "已通过", "", ADMIN_ID);

        UserDto user = userService.findById(USER_ID);
        assertEquals("商家用户", user.getRole());
        assertTrue(user.getVerified());
        assertEquals("湖滨咖啡", user.getMerchantName());
    }

    @Test
    @DisplayName("US-D04-3: 驳回时必须填写原因")
    void shouldRequireReasonWhenRejectingMerchantApplication() {
        createMerchantReview("merchant-d04-c", "rv-d04-c", USER_ID, "山野店", "license-c.pdf", "https://cdn.test/license-c.pdf");

        assertThrows(IllegalStateException.class,
                () -> adminService.review("rv-d04-c", "已驳回", " ", ADMIN_ID));

        adminService.review("rv-d04-c", "已驳回", "营业执照不清晰", ADMIN_ID);
        String status = jdbc.queryForObject("select status from merchant_applications where id = ?", String.class, "merchant-d04-c");
        assertEquals("已驳回", status);
    }

    private void createMerchantReview(String applicationId, String reviewId, String userId, String merchantName, String licenseName, String licenseUrl) {
        jdbc.update("insert into merchant_applications (id,user_id,merchant_name,license_name,license_url,status) values (?,?,?,?,?,'待审核')",
                applicationId, userId, merchantName, licenseName, licenseUrl);
        jdbc.update("insert into review_tasks (id,type,target_id,title,submitter,risk,reason,status) values (?,?,?,?,?,?,?,?)",
                reviewId, "商家认证", applicationId, merchantName, "小满", "低", "商家资质审核", "待审核");
    }

    private Object value(Map<String, Object> row, String upper, String lower) {
        return row.containsKey(upper) ? row.get(upper) : row.get(lower);
    }
}
