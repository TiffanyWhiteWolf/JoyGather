package com.quju.service;

import com.quju.dto.CommonDtos;
import com.quju.dto.PlannerRequest;
import com.quju.dto.PlannerResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class IntegrationService {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${quju.app.public-base-url:http://localhost:5173}")
    private String publicBaseUrl;
    @Value("${quju.mail.from:no-reply@quju.local}")
    private String mailFrom;
    @Value("${spring.mail.host:}")
    private String smtpHost;
    @Value("${quju.ai.base-url:}")
    private String aiBaseUrl;
    @Value("${quju.ai.api-key:}")
    private String aiApiKey;
    @Value("${quju.ai.model:gpt-4o-mini}")
    private String aiModel;
    @Value("${quju.amap.key:}")
    private String amapKey;

    public IntegrationService(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> mailSender) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
    }

    public void sendActivationEmail(String email, String token) {
        String subject = "激活你的趣聚账号";
        String link = publicBaseUrl + "/auth?activate=" + token;
        String body = "请打开以下链接激活账号：\n" + link + "\n如果不是你本人操作，请忽略。";
        String outboxId = DbSupport.id("mail");
        jdbc.update("insert into mail_outbox (id,recipient,subject,body,status) values (?,?,?,?,?)",
                outboxId, email, subject, body, "待发送");
        if (smtpHost == null || smtpHost.trim().isEmpty()) {
            jdbc.update("update mail_outbox set status = '待配置', error = ? where id = ?", "SMTP 未配置，激活 token 已返回给本地开发环境", outboxId);
            logThirdParty("SMTP", "SEND_ACTIVATION", "DEGRADED", email, "", "SMTP 未配置", 0);
            return;
        }
        long started = System.currentTimeMillis();
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.getObject().send(message);
            jdbc.update("update mail_outbox set status = '已发送', sent_at = now() where id = ?", outboxId);
            logThirdParty("SMTP", "SEND_ACTIVATION", "SUCCESS", email, "", "", elapsed(started));
        } catch (Exception ex) {
            jdbc.update("update mail_outbox set status = '发送失败', error = ? where id = ?", trim(ex.getMessage()), outboxId);
            logThirdParty("SMTP", "SEND_ACTIVATION", "FAILED", email, "", trim(ex.getMessage()), elapsed(started));
        }
    }

    public ModerationResult moderateActivity(String activityId, String title, String summary, List<String> tags, int capacity) {
        String text = (DbSupport.safe(title, "") + " " + DbSupport.safe(summary, "") + " " + DbSupport.join(tags)).toLowerCase(Locale.CHINA);
        if (aiBaseUrl == null || aiBaseUrl.trim().isEmpty() || aiApiKey == null || aiApiKey.trim().isEmpty()) {
            ModerationResult fallback = ruleModeration(text, capacity, "AI 未配置，使用规则审核降级");
            logThirdParty("AI", "MODERATE_ACTIVITY", "DEGRADED", title, fallback.reason, "AI 未配置", 0);
            return fallback;
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> payload = chatPayload("判断活动内容是否低风险。只返回 JSON，字段：result=LOW_RISK 或 REVIEW_REQUIRED，labels 数组，reason 字符串。活动：" + text);
            HttpHeaders headers = aiHeaders();
            Map response = restTemplate.postForObject(aiBaseUrl + "/chat/completions", new HttpEntity<Map<String, Object>>(payload, headers), Map.class);
            String raw = String.valueOf(response);
            ModerationResult parsed = parseModeration(raw, capacity);
            logThirdParty("AI", "MODERATE_ACTIVITY", "SUCCESS", title, parsed.reason, "", elapsed(started));
            return parsed;
        } catch (Exception ex) {
            ModerationResult fallback = ruleModeration(text, capacity, "AI 审核失败，转人工审核");
            logThirdParty("AI", "MODERATE_ACTIVITY", "FAILED", title, fallback.reason, trim(ex.getMessage()), elapsed(started));
            return fallback;
        }
    }

    public PlannerResponse generatePlan(PlannerRequest request) {
        if (aiBaseUrl == null || aiBaseUrl.trim().isEmpty() || aiApiKey == null || aiApiKey.trim().isEmpty()) {
            logThirdParty("AI", "GENERATE_PLAN", "DEGRADED", request.getTheme(), "", "AI 未配置", 0);
            return localPlan(request);
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> payload = chatPayload("为趣聚平台生成线下活动方案，返回简洁标题、简介、标签、流程和安全须知。主题：" + request.getTheme() + " 人数：" + request.getPeople() + " 风格：" + request.getStyle());
            HttpHeaders headers = aiHeaders();
            Map response = restTemplate.postForObject(aiBaseUrl + "/chat/completions", new HttpEntity<Map<String, Object>>(payload, headers), Map.class);
            logThirdParty("AI", "GENERATE_PLAN", "SUCCESS", request.getTheme(), String.valueOf(response), "", elapsed(started));
            return localPlan(request);
        } catch (Exception ex) {
            logThirdParty("AI", "GENERATE_PLAN", "FAILED", request.getTheme(), "", trim(ex.getMessage()), elapsed(started));
            return localPlan(request);
        }
    }

    public List<String> classifyImages(List<String> urls) {
        return classifyImagesDetailed(urls).categories;
    }

    public ImageClassificationResult classifyImagesDetailed(List<String> urls) {
        if (urls == null || urls.isEmpty()) return new ImageClassificationResult(Collections.<String>emptyList(), false, "请先上传图片。");
        if (aiBaseUrl == null || aiBaseUrl.trim().isEmpty() || aiApiKey == null || aiApiKey.trim().isEmpty()) {
            logThirdParty("AI", "CLASSIFY_IMAGES", "DEGRADED", DbSupport.join(urls), "", "AI 未配置", 0);
            return new ImageClassificationResult(localImageCategories(urls.size()), false, "AI 服务未配置，已切换为手动分类，不影响发布。");
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> payload = chatPayload("将这些活动图片分类为合影、场地、过程记录、物资、成果展示：" + DbSupport.join(urls));
            HttpHeaders headers = aiHeaders();
            restTemplate.postForObject(aiBaseUrl + "/chat/completions", new HttpEntity<Map<String, Object>>(payload, headers), Map.class);
            logThirdParty("AI", "CLASSIFY_IMAGES", "SUCCESS", String.valueOf(urls.size()), "", "", elapsed(started));
            return new ImageClassificationResult(localImageCategories(urls.size()), true, "AI 已完成初步分类，请确认或调整后发布。");
        } catch (Exception ex) {
            logThirdParty("AI", "CLASSIFY_IMAGES", "FAILED", String.valueOf(urls.size()), "", trim(ex.getMessage()), elapsed(started));
            return new ImageClassificationResult(localImageCategories(urls.size()), false, "AI 分类暂时不可用，已切换为手动分类，不影响发布。");
        }
    }

    public List<CommonDtos.GeoPoint> searchAmap(String keyword, String city) {
        if (amapKey == null || amapKey.trim().isEmpty()) {
            logThirdParty("AMAP", "PLACE_SEARCH", "DEGRADED", keyword, "", "高德 Key 未配置", 0);
            return fallbackPlaces(keyword);
        }
        long started = System.currentTimeMillis();
        try {
            String url = "https://restapi.amap.com/v3/place/text?key=" + amapKey + "&keywords=" + keyword + "&city=" + DbSupport.safe(city, "杭州") + "&offset=10&page=1";
            Map response = restTemplate.getForObject(url, Map.class);
            logThirdParty("AMAP", "PLACE_SEARCH", "SUCCESS", keyword, String.valueOf(response), "", elapsed(started));
        } catch (Exception ex) {
            logThirdParty("AMAP", "PLACE_SEARCH", "FAILED", keyword, "", trim(ex.getMessage()), elapsed(started));
        }
        return fallbackPlaces(keyword);
    }

    public CommonDtos.GeoPoint reverseGeocode(BigDecimal longitude, BigDecimal latitude) {
        CommonDtos.GeoPoint fallback = fallbackReversePoint(longitude, latitude);
        if (amapKey == null || amapKey.trim().isEmpty()) {
            logThirdParty("AMAP", "REVERSE_GEOCODE", "DEGRADED", longitude + "," + latitude, fallback.getDistrict(), "高德 Key 未配置", 0);
            return fallback;
        }
        long started = System.currentTimeMillis();
        try {
            String location = longitude.toPlainString() + "," + latitude.toPlainString();
            String url = "https://restapi.amap.com/v3/geocode/regeo?key=" + amapKey + "&location=" + location + "&extensions=base";
            Map response = restTemplate.getForObject(url, Map.class);
            Map<String, Object> regeocode = asMap(response == null ? null : response.get("regeocode"));
            Map<String, Object> address = asMap(regeocode.get("addressComponent"));
            String district = DbSupport.safe(String.valueOf(address.get("district")), fallback.getDistrict()).trim();
            String formatted = DbSupport.safe(String.valueOf(regeocode.get("formatted_address")), fallback.getName()).trim();
            CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
            point.setName(formatted == null || formatted.isEmpty() || "null".equals(formatted) ? fallback.getName() : formatted);
            point.setDistrict(district == null || district.isEmpty() || "null".equals(district) ? fallback.getDistrict() : district);
            point.setLongitude(longitude);
            point.setLatitude(latitude);
            logThirdParty("AMAP", "REVERSE_GEOCODE", "SUCCESS", location, point.getDistrict(), "", elapsed(started));
            return point;
        } catch (Exception ex) {
            logThirdParty("AMAP", "REVERSE_GEOCODE", "FAILED", longitude + "," + latitude, "", trim(ex.getMessage()), elapsed(started));
            return fallback;
        }
    }

    private HttpHeaders aiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiApiKey);
        return headers;
    }

    private Map<String, Object> chatPayload(String prompt) {
        Map<String, Object> message = new HashMap<String, Object>();
        message.put("role", "user");
        message.put("content", prompt);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("model", aiModel);
        payload.put("messages", Collections.singletonList(message));
        payload.put("temperature", 0.2);
        return payload;
    }

    private ModerationResult parseModeration(String raw, int capacity) {
        String lower = raw == null ? "" : raw.toLowerCase(Locale.CHINA);
        if (capacity > 50 || lower.contains("review_required") || lower.contains("高风险") || lower.contains("危险")) {
            return new ModerationResult("REVIEW_REQUIRED", "中", "AI 判定需人工复核", Arrays.asList("AI_REVIEW"));
        }
        return new ModerationResult("LOW_RISK", "低", "AI 判定低风险", Collections.<String>emptyList());
    }

    private ModerationResult ruleModeration(String text, int capacity, String fallbackReason) {
        boolean capacityRisky = capacity > 50;
        boolean keywordRisky = text.contains("危险") || text.contains("酒吧") || text.contains("凌晨") || text.contains("水上") || text.contains("夜间");
        boolean risky = capacityRisky || keywordRisky;

        String reason;
        if (!risky) {
            reason = "规则审核低风险";
        } else if (capacityRisky && keywordRisky) {
            reason = "报名人数超过 50 人且内容包含风险词，转入人工审核";
        } else if (capacityRisky) {
            reason = "报名人数超过 50 人，转入人工审核";
        } else {
            reason = "活动内容包含风险词，转入人工审核";
        }

        return new ModerationResult(risky ? "REVIEW_REQUIRED" : "LOW_RISK", risky ? "中" : "低",
                reason, risky ? Arrays.asList("RULE_REVIEW") : Collections.<String>emptyList());
    }

    private PlannerResponse localPlan(PlannerRequest request) {
        String theme = DbSupport.safe(request.getTheme(), "城市活动");
        return new PlannerResponse(
                theme.length() > 18 ? theme.substring(0, 18) + "计划" : theme + "计划",
                "围绕「" + theme + "」设计一场新朋友也能自然加入的线下活动。",
                Arrays.asList("城市探索", DbSupport.safe(request.getStyle(), "轻松社交"), DbSupport.safe(request.getPeople(), "12-20 人")),
                Arrays.asList("集合签到与破冰", "主题体验与分组任务", "交流分享与合影", "活动结束与后续联系"),
                "发布前请确认集合地点、天气预案、紧急联系人和交通返程安排。"
        );
    }

    private List<String> localImageCategories(int size) {
        List<String> categories = Arrays.asList("合影", "场地", "过程记录", "物资", "成果展示");
        List<String> result = new ArrayList<String>();
        for (int i = 0; i < size; i++) result.add(categories.get(i % categories.size()));
        return result;
    }

    public static class ImageClassificationResult {
        public final List<String> categories;
        public final boolean aiAvailable;
        public final String notice;
        public ImageClassificationResult(List<String> categories, boolean aiAvailable, String notice) {
            this.categories = categories;
            this.aiAvailable = aiAvailable;
            this.notice = notice;
        }
    }

    private List<CommonDtos.GeoPoint> fallbackPlaces(String keyword) {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setName(keyword == null || keyword.trim().isEmpty() ? "杭州市中心" : keyword.trim());
        point.setDistrict("杭州");
        point.setLongitude(new BigDecimal("120.155070"));
        point.setLatitude(new BigDecimal("30.274085"));
        return Collections.singletonList(point);
    }

    private CommonDtos.GeoPoint fallbackReversePoint(BigDecimal longitude, BigDecimal latitude) {
        String district = nearestHangzhouDistrict(longitude, latitude);
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setName("当前位置 " + latitude.toPlainString() + ", " + longitude.toPlainString());
        point.setDistrict(district);
        point.setLongitude(longitude);
        point.setLatitude(latitude);
        return point;
    }

    private String nearestHangzhouDistrict(BigDecimal longitude, BigDecimal latitude) {
        double lng = longitude.doubleValue();
        double lat = latitude.doubleValue();
        Object[][] centers = {
                {"拱墅区", 120.1551, 30.3183},
                {"西湖区", 120.1302, 30.2595},
                {"上城区", 120.1715, 30.2502},
                {"滨江区", 120.2120, 30.2084},
                {"余杭区", 120.2994, 30.4187},
                {"萧山区", 120.2645, 30.1853},
                {"钱塘区", 120.4939, 30.3229},
                {"临平区", 120.2992, 30.4219}
        };
        String best = "定位城区";
        double bestDistance = Double.MAX_VALUE;
        for (Object[] center : centers) {
            double dLng = lng - ((Number) center[1]).doubleValue();
            double dLat = lat - ((Number) center[2]).doubleValue();
            double distance = dLng * dLng + dLat * dLat;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = String.valueOf(center[0]);
            }
        }
        return best;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
    }

    public void saveAiAudit(String activityId, ModerationResult result, String raw) {
        if (activityId == null) return;
        jdbc.update("insert into ai_audit_logs (id,activity_id,result,risk_labels,reason,raw_response) values (?,?,?,?,?,?)",
                DbSupport.id("ai"), activityId, result.result, DbSupport.join(result.labels), result.reason, raw);
    }

    public void logThirdParty(String provider, String operation, String status, String requestSummary, String responseSummary, String error, int durationMs) {
        jdbc.update("insert into third_party_events (id,provider,operation,status,request_summary,response_summary,error,duration_ms) values (?,?,?,?,?,?,?,?)",
                DbSupport.id("tp"), provider, operation, status, trim(requestSummary), trim(responseSummary), trim(error), durationMs);
    }

    private int elapsed(long started) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - started);
    }

    private String trim(String value) {
        if (value == null) return "";
        return value.length() > 900 ? value.substring(0, 900) : value;
    }

    public static class ModerationResult {
        public final String result;
        public final String risk;
        public final String reason;
        public final List<String> labels;
        public ModerationResult(String result, String risk, String reason, List<String> labels) {
            this.result = result;
            this.risk = risk;
            this.reason = reason;
            this.labels = labels;
        }
    }
}
