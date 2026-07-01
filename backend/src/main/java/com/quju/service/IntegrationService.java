package com.quju.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.dto.CommonDtos;
import com.quju.dto.PlannerRequest;
import com.quju.dto.PlannerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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
    @Value("${quju.ai.response-format:auto}")
    private String aiResponseFormat;
    @Value("${quju.amap.key:}")
    private String amapKey;

    @Autowired
    public IntegrationService(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> mailSender) {
        this(jdbc, mailSender, defaultRestTemplate(), new ObjectMapper());
    }

    IntegrationService(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> mailSender,
                       RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.mailSender = mailSender;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
        return moderateActivity(activityId, title, summary, summary, "", tags, "", "", capacity);
    }

    public ModerationResult moderateActivity(String activityId, String title, String summary, String description,
                                             String category, List<String> tags, String safetyNote,
                                             String location, int capacity) {
        String requestSnapshot = moderationRequestSnapshot(title, summary, description, category, tags, safetyNote, location, capacity);
        if (aiBaseUrl == null || aiBaseUrl.trim().isEmpty() || aiApiKey == null || aiApiKey.trim().isEmpty()) {
            ModerationResult unavailable = new ModerationResult("INDETERMINATE", "中",
                    "AI 审核服务尚未配置，活动已转入人工审核",
                    Arrays.asList("AI_NOT_CONFIGURED"), "", "NOT_CONFIGURED",
                    aiModel, null, "AI 未配置", 0, requestSnapshot);
            logThirdParty("AI", "MODERATE_ACTIVITY", "DEGRADED", title, unavailable.reason, unavailable.errorMessage, 0);
            return unavailable;
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> payload = moderationPayload(requestSnapshot);
            HttpHeaders headers = aiHeaders();
            Map response = restTemplate.postForObject(aiBaseUrl + "/chat/completions", new HttpEntity<Map<String, Object>>(payload, headers), Map.class);
            String raw = objectMapper.writeValueAsString(response);
            String content = extractAssistantContent(response);
            ModerationResult parsed = parseModeration(content, raw, capacity, elapsed(started), requestSnapshot);
            logThirdParty("AI", "MODERATE_ACTIVITY", parsed.providerStatus, title, parsed.reason,
                    parsed.errorMessage, parsed.durationMs);
            return parsed;
        } catch (Exception ex) {
            ModerationResult failed = new ModerationResult("ERROR", "中",
                    "AI 审核服务异常，活动已转入人工审核",
                    Arrays.asList("AI_SERVICE_ERROR"), "", "FAILED", aiModel, null,
                    trim(ex.getMessage()), elapsed(started), requestSnapshot);
            logThirdParty("AI", "MODERATE_ACTIVITY", "FAILED", title, failed.reason,
                    failed.errorMessage, failed.durationMs);
            return failed;
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

    private Map<String, Object> moderationPayload(String requestSnapshot) {
        Map<String, Object> systemMessage = new HashMap<String, Object>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是活动平台内容安全审核员。检查违法违规、低俗色情、暴力危险、毒品赌博、诈骗、仇恨骚扰和自伤风险。"
                + "只有明确安全且无需人工判断时才能返回 LOW_RISK；存在风险或信息不足时返回 REVIEW_REQUIRED 或 INDETERMINATE。"
                + "仅输出 JSON，格式示例：{\"decision\":\"LOW_RISK\",\"riskLevel\":\"LOW\",\"labels\":[],"
                + "\"reason\":\"未发现内容安全风险\",\"confidence\":0.95}。");
        Map<String, Object> userMessage = new HashMap<String, Object>();
        userMessage.put("role", "user");
        userMessage.put("content", requestSnapshot);

        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", "object");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("decision", enumSchema("LOW_RISK", "REVIEW_REQUIRED", "INDETERMINATE"));
        properties.put("riskLevel", enumSchema("LOW", "MEDIUM", "HIGH"));
        Map<String, Object> labels = new HashMap<String, Object>();
        labels.put("type", "array");
        Map<String, Object> labelItem = new HashMap<String, Object>();
        labelItem.put("type", "string");
        labels.put("items", labelItem);
        properties.put("labels", labels);
        Map<String, Object> reason = new HashMap<String, Object>();
        reason.put("type", "string");
        properties.put("reason", reason);
        Map<String, Object> confidence = new HashMap<String, Object>();
        confidence.put("type", "number");
        confidence.put("minimum", 0);
        confidence.put("maximum", 1);
        properties.put("confidence", confidence);
        schema.put("properties", properties);
        schema.put("required", Arrays.asList("decision", "riskLevel", "labels", "reason", "confidence"));
        schema.put("additionalProperties", false);

        Map<String, Object> responseFormat = new HashMap<String, Object>();
        if (useJsonObjectMode()) {
            responseFormat.put("type", "json_object");
        } else {
            Map<String, Object> jsonSchema = new HashMap<String, Object>();
            jsonSchema.put("name", "activity_moderation");
            jsonSchema.put("strict", true);
            jsonSchema.put("schema", schema);
            responseFormat.put("type", "json_schema");
            responseFormat.put("json_schema", jsonSchema);
        }

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("model", aiModel);
        payload.put("messages", Arrays.asList(systemMessage, userMessage));
        payload.put("temperature", 0);
        payload.put("max_tokens", 800);
        payload.put("response_format", responseFormat);
        return payload;
    }

    private boolean useJsonObjectMode() {
        if ("json_object".equalsIgnoreCase(aiResponseFormat)) return true;
        if ("json_schema".equalsIgnoreCase(aiResponseFormat)) return false;
        return aiBaseUrl != null && aiBaseUrl.toLowerCase(Locale.ROOT).contains("deepseek");
    }

    private Map<String, Object> enumSchema(String... values) {
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", "string");
        schema.put("enum", Arrays.asList(values));
        return schema;
    }

    private String moderationRequestSnapshot(String title, String summary, String description, String category,
                                             List<String> tags, String safetyNote, String location, int capacity) {
        Map<String, Object> content = new HashMap<String, Object>();
        content.put("title", DbSupport.safe(title, ""));
        content.put("summary", DbSupport.safe(summary, ""));
        content.put("description", DbSupport.safe(description, ""));
        content.put("category", DbSupport.safe(category, ""));
        content.put("tags", tags == null ? Collections.emptyList() : tags);
        content.put("safetyNote", DbSupport.safe(safetyNote, ""));
        content.put("location", DbSupport.safe(location, ""));
        content.put("capacity", capacity);
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception ex) {
            return String.valueOf(content);
        }
    }

    private String extractAssistantContent(Map response) {
        if (response == null) throw new IllegalStateException("AI 返回为空");
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List) || ((List) choicesValue).isEmpty()) {
            throw new IllegalStateException("AI 返回缺少 choices");
        }
        Object first = ((List) choicesValue).get(0);
        Map<String, Object> choice = asMap(first);
        Map<String, Object> message = asMap(choice.get("message"));
        Object content = message.get("content");
        if (content == null || String.valueOf(content).trim().isEmpty()) {
            throw new IllegalStateException("AI 返回缺少审核内容");
        }
        return String.valueOf(content).trim();
    }

    private ModerationResult parseModeration(String content, String raw, int capacity, int durationMs,
                                             String requestSnapshot) {
        try {
            String json = stripCodeFence(content);
            JsonNode node = objectMapper.readTree(json);
            String decision = requiredText(node, "decision");
            String riskLevel = requiredText(node, "riskLevel");
            String reason = requiredText(node, "reason");
            JsonNode confidenceNode = node.get("confidence");
            if (confidenceNode == null || !confidenceNode.isNumber()) throw new IllegalStateException("confidence 缺失");
            double confidence = confidenceNode.asDouble();
            if (confidence < 0 || confidence > 1) throw new IllegalStateException("confidence 超出范围");
            JsonNode labelsNode = node.get("labels");
            if (labelsNode == null || !labelsNode.isArray()) throw new IllegalStateException("labels 缺失");
            List<String> labels = new ArrayList<String>();
            for (JsonNode label : labelsNode) {
                if (label.isTextual() && !label.asText().trim().isEmpty()) labels.add(label.asText().trim());
            }
            if (!Arrays.asList("LOW_RISK", "REVIEW_REQUIRED", "INDETERMINATE").contains(decision)) {
                throw new IllegalStateException("未知审核结论");
            }
            if (capacity > 50) {
                decision = "REVIEW_REQUIRED";
                if (!labels.contains("LARGE_EVENT")) labels.add("LARGE_EVENT");
                reason = "报名人数超过 50 人，需人工复核；AI 结论：" + reason;
            } else if ("LOW_RISK".equals(decision) && (!labels.isEmpty() || confidence < 0.8D)) {
                decision = "INDETERMINATE";
                reason = confidence < 0.8D ? "AI 置信度不足，需人工复核：" + reason : "AI 返回风险标签，需人工复核：" + reason;
            }
            if (!"LOW_RISK".equals(decision) && labels.isEmpty()) labels.add("AI_REVIEW");
            String risk = riskName(riskLevel);
            if (!"LOW_RISK".equals(decision) && "低".equals(risk)) risk = "中";
            return new ModerationResult(decision, risk, reason, labels, raw,
                    "SUCCESS", aiModel, confidence, "", durationMs, requestSnapshot);
        } catch (Exception ex) {
            return new ModerationResult("INDETERMINATE", "中",
                    "AI 返回结果无法可靠解析，活动已转入人工审核",
                    Arrays.asList("AI_INVALID_RESPONSE"), raw, "INVALID_RESPONSE", aiModel,
                    null, trim(ex.getMessage()), durationMs, requestSnapshot);
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty()) {
            throw new IllegalStateException(field + " 缺失");
        }
        return value.asText().trim();
    }

    private String stripCodeFence(String value) {
        String result = value == null ? "" : value.trim();
        if (result.startsWith("```")) {
            int firstBreak = result.indexOf('\n');
            int lastFence = result.lastIndexOf("```");
            if (firstBreak >= 0 && lastFence > firstBreak) result = result.substring(firstBreak + 1, lastFence).trim();
        }
        return result;
    }

    private String riskName(String value) {
        if ("HIGH".equalsIgnoreCase(value) || "高".equals(value)) return "高";
        if ("MEDIUM".equalsIgnoreCase(value) || "中".equals(value)) return "中";
        return "低";
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

    public void saveAiAudit(String activityId, ModerationResult result) {
        if (activityId == null) return;
        jdbc.update("insert into ai_audit_logs "
                        + "(id,activity_id,result,risk_level,risk_labels,reason,confidence,provider,model,provider_status,"
                        + "request_snapshot,raw_response,error_message,duration_ms) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                DbSupport.id("ai"), activityId, result.result, result.risk, DbSupport.join(result.labels),
                result.reason, result.confidence, "OPENAI_COMPATIBLE", result.model, result.providerStatus,
                trimAudit(result.requestSnapshot), trimAudit(result.rawResponse), trim(result.errorMessage), result.durationMs);
    }

    @Deprecated
    public void saveAiAudit(String activityId, ModerationResult result, String raw) {
        saveAiAudit(activityId, result);
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

    private String trimAudit(String value) {
        if (value == null) return "";
        return value.length() > 12000 ? value.substring(0, 12000) : value;
    }

    private static RestTemplate defaultRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }

    public static class ModerationResult {
        public final String result;
        public final String risk;
        public final String reason;
        public final List<String> labels;
        public final String rawResponse;
        public final String providerStatus;
        public final String model;
        public final Double confidence;
        public final String errorMessage;
        public final int durationMs;
        public final String requestSnapshot;

        public ModerationResult(String result, String risk, String reason, List<String> labels) {
            this(result, risk, reason, labels, "", "LOCAL", "", null, "", 0, "");
        }

        public ModerationResult(String result, String risk, String reason, List<String> labels,
                                String rawResponse, String providerStatus, String model, Double confidence,
                                String errorMessage, int durationMs, String requestSnapshot) {
            this.result = result;
            this.risk = risk;
            this.reason = reason;
            this.labels = labels;
            this.rawResponse = rawResponse;
            this.providerStatus = providerStatus;
            this.model = model;
            this.confidence = confidence;
            this.errorMessage = errorMessage;
            this.durationMs = durationMs;
            this.requestSnapshot = requestSnapshot;
        }
    }
}
