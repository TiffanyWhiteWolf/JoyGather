package com.quju.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.dto.CommonDtos;
import com.quju.dto.PlannerRequest;
import com.quju.dto.PlannerResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RefreshScope
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
    @Value("${quju.nominatim.base-url:https://nominatim.openstreetmap.org}")
    private String nominatimBaseUrl;
    @Value("${quju.nominatim.user-agent:JoyGather/1.0 (+http://localhost:5173)}")
    private String nominatimUserAgent;
    @Value("${quju.nominatim.min-interval-ms:1000}")
    private long nominatimMinIntervalMs;
    @Value("${quju.nominatim.cache-ttl-ms:86400000}")
    private long nominatimCacheTtlMs;
    private final Map<String, GeoCacheEntry> geoCache = new ConcurrentHashMap<String, GeoCacheEntry>();
    private final Object nominatimLock = new Object();
    private volatile long lastNominatimRequestAt;

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

    public void sendActivationEmail(String email, String token, String origin) {
        String subject = "激活你的趣聚账号";
        String base = origin != null && !origin.isEmpty() ? origin : publicBaseUrl;
        String link = base + "/auth?activate=" + token;
        String htmlBody = "<html><body style=\"font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;padding:24px;\">"
                + "<h2 style=\"color:#ff6b45;\">趣聚</h2>"
                + "<p>感谢注册趣聚！请点击下方按钮激活你的账号：</p>"
                + "<p><a href=\"" + link + "\" style=\"display:inline-block;padding:12px 28px;background:#ff6b45;color:#fff;text-decoration:none;border-radius:24px;font-weight:bold;\">激活账号</a></p>"
                + "<p style=\"color:#9ca3af;font-size:13px;\">或复制以下链接到浏览器：<br><a href=\"" + link + "\">" + link + "</a></p>"
                + "<p style=\"color:#9ca3af;font-size:13px;\">如果不是你本人操作，请忽略此邮件。</p>"
                + "</body></html>";
        String textBody = "请打开以下链接激活账号：" + link + "\n如果不是你本人操作，请忽略。";
        String outboxId = DbSupport.id("mail");
        jdbc.update("insert into mail_outbox (id,recipient,subject,body,status) values (?,?,?,?,?)",
                outboxId, email, subject, textBody, "待发送");
        if (smtpHost == null || smtpHost.trim().isEmpty()) {
            jdbc.update("update mail_outbox set status = '待配置', error = ? where id = ?", "SMTP 未配置，激活 token 已返回给本地开发环境", outboxId);
            logThirdParty("SMTP", "SEND_ACTIVATION", "DEGRADED", email, "", "SMTP 未配置", 0);
            return;
        }
        long started = System.currentTimeMillis();
        try {
            MimeMessage mimeMessage = mailSender.getObject().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            mailSender.getObject().send(mimeMessage);
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
            return localPlan(request, false, "AI 未配置，已使用本地模板生成。");
        }
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> payload = plannerPayload(request);
            HttpHeaders headers = aiHeaders();
            Map response = restTemplate.postForObject(aiBaseUrl + "/chat/completions", new HttpEntity<Map<String, Object>>(payload, headers), Map.class);
            String content = extractAiContent(response);
            PlannerResponse plan = parsePlannerResponse(content, request);
            logThirdParty("AI", "GENERATE_PLAN", "SUCCESS", request.getTheme(), content, "", elapsed(started));
            return plan;
        } catch (Exception ex) {
            logThirdParty("AI", "GENERATE_PLAN", "FAILED", request.getTheme(), "", trim(ex.getMessage()), elapsed(started));
            return localPlan(request, false, "AI 生成暂时不可用，已使用本地模板生成。");
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
        String normalizedCity = normalizeCity(city);
        if (amapKey != null && !amapKey.trim().isEmpty()) {
            long started = System.currentTimeMillis();
            try {
                String url = "https://restapi.amap.com/v3/place/text?key=" + encode(amapKey) + "&keywords=" + encode(DbSupport.safe(keyword, "")) + "&city=" + encode(normalizedCity) + "&citylimit=true&offset=10&page=1";
                Map response = restTemplate.getForObject(url, Map.class);
                List<CommonDtos.GeoPoint> points = parseAmapPlaces(response, normalizedCity);
                logThirdParty("AMAP", "PLACE_SEARCH", "SUCCESS", keyword, String.valueOf(points.size()), "", elapsed(started));
                if (!points.isEmpty()) return points;
            } catch (Exception ex) {
                logThirdParty("AMAP", "PLACE_SEARCH", "FAILED", keyword, "", trim(ex.getMessage()), elapsed(started));
            }
        }
        return searchNominatim(keyword, normalizedCity);
    }

    public CommonDtos.GeoPoint reverseGeocode(BigDecimal longitude, BigDecimal latitude) {
        CommonDtos.GeoPoint fallback = fallbackReversePoint(longitude, latitude);
        if (amapKey != null && !amapKey.trim().isEmpty()) {
            long started = System.currentTimeMillis();
            try {
                String location = longitude.toPlainString() + "," + latitude.toPlainString();
                String url = "https://restapi.amap.com/v3/geocode/regeo?key=" + encode(amapKey) + "&location=" + location + "&extensions=base";
                Map response = restTemplate.getForObject(url, Map.class);
                Map<String, Object> regeocode = asMap(response == null ? null : response.get("regeocode"));
                Map<String, Object> address = asMap(regeocode.get("addressComponent"));
                String district = cleanValue(address.get("district"), fallback.getDistrict());
                String city = normalizeCity(cleanValue(address.get("city"), fallback.getCity()));
                String formatted = cleanValue(regeocode.get("formatted_address"), "");
                if (!formatted.isEmpty()) {
                    CommonDtos.GeoPoint point = geoPoint(formatted, city, district, longitude, latitude);
                    logThirdParty("AMAP", "REVERSE_GEOCODE", "SUCCESS", location, point.getDistrict(), "", elapsed(started));
                    return point;
                }
            } catch (Exception ex) {
                logThirdParty("AMAP", "REVERSE_GEOCODE", "FAILED", longitude + "," + latitude, "", trim(ex.getMessage()), elapsed(started));
            }
        }
        return reverseNominatim(longitude, latitude, fallback);
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

    private Map<String, Object> plannerPayload(PlannerRequest request) {
        Map<String, Object> system = new HashMap<String, Object>();
        system.put("role", "system");
        system.put("content",
                "你是趣聚平台的线下活动策划助手。请输出可以直接展示给用户的活动方案。"
                        + "必须只返回 JSON 对象，不要 Markdown，不要代码块。"
                        + "JSON 字段固定为：title 字符串，introduction 字符串，tags 字符串数组，schedule 字符串数组，safetyNote 字符串。");

        Map<String, Object> user = new HashMap<String, Object>();
        user.put("role", "user");
        user.put("content",
                "活动主题：" + DbSupport.safe(request.getTheme(), "")
                        + "\n预计人数：" + DbSupport.safe(request.getPeople(), "")
                        + "\n活动氛围：" + DbSupport.safe(request.getStyle(), "")
                        + "\n要求：标题要具体、有吸引力；简介 80-140 字；标签 3-5 个；流程 4-6 步；安全须知贴合活动场景。");

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("model", aiModel);
        payload.put("messages", Arrays.asList(system, user));
        payload.put("temperature", 0.7);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private String extractAiContent(Map response) {
        if (response == null) throw new IllegalStateException("AI 返回为空");
        Object choicesValue = response.get("choices");
        if (choicesValue instanceof List && !((List) choicesValue).isEmpty()) {
            Object choiceValue = ((List) choicesValue).get(0);
            Map<String, Object> choice = asMap(choiceValue);
            Map<String, Object> message = asMap(choice.get("message"));
            String content = contentToText(message.get("content"));
            if (!content.trim().isEmpty()) return content;
            content = contentToText(choice.get("text"));
            if (!content.trim().isEmpty()) return content;
        }
        String content = contentToText(response.get("content"));
        if (!content.trim().isEmpty()) return content;
        content = contentToText(response.get("output_text"));
        if (!content.trim().isEmpty()) return content;
        throw new IllegalStateException("AI 返回中没有可解析内容");
    }

    private String contentToText(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof List) {
            StringBuilder builder = new StringBuilder();
            for (Object item : (List) value) {
                String text = contentToText(item);
                if (!text.trim().isEmpty()) builder.append(text);
            }
            return builder.toString();
        }
        if (value instanceof Map) {
            Map<String, Object> map = asMap(value);
            String text = contentToText(map.get("text"));
            if (!text.trim().isEmpty()) return text;
            return contentToText(map.get("content"));
        }
        return String.valueOf(value);
    }

    private PlannerResponse parsePlannerResponse(String content, PlannerRequest request) throws IOException {
        String json = stripJsonWrapper(content);
        JsonNode root = objectMapper.readTree(json);
        if (!root.isObject()) throw new IllegalStateException("AI 返回不是 JSON 对象");

        PlannerResponse fallback = localPlan(request, false, "");
        String title = firstText(root, fallback.getTitle(), "title", "name");
        String introduction = firstText(root, fallback.getIntroduction(), "introduction", "summary", "description", "intro");
        List<String> tags = stringArray(root, "tags");
        if (tags.isEmpty()) tags = fallback.getTags();
        List<String> schedule = stringArray(root, "schedule", "agenda", "steps", "flow");
        if (schedule.isEmpty()) schedule = fallback.getSchedule();
        String safetyNote = firstText(root, fallback.getSafetyNote(), "safetyNote", "safety_note", "safety", "notice");

        boolean hasMeaningfulAiContent = !title.equals(fallback.getTitle())
                || !introduction.equals(fallback.getIntroduction())
                || !schedule.equals(fallback.getSchedule());
        if (!hasMeaningfulAiContent) throw new IllegalStateException("AI 返回缺少有效方案字段");
        return new PlannerResponse(title, introduction, tags, schedule, safetyNote, true, "AI 已生成活动方案。");
    }

    private String stripJsonWrapper(String content) {
        String text = DbSupport.safe(content, "").trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            int fence = text.lastIndexOf("```");
            if (fence >= 0) text = text.substring(0, fence).trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }

    private String firstText(JsonNode root, String fallback, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && node.isTextual() && !node.asText().trim().isEmpty()) return node.asText().trim();
        }
        return fallback;
    }

    private List<String> stringArray(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            List<String> values = stringArray(node);
            if (!values.isEmpty()) return values;
        }
        return Collections.emptyList();
    }

    private List<String> stringArray(JsonNode node) {
        if (node == null || node.isNull()) return Collections.emptyList();
        List<String> values = new ArrayList<String>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = nodeToText(item);
                if (!text.isEmpty()) values.add(text);
            }
        } else {
            String text = nodeToText(node);
            if (!text.isEmpty()) values.add(text);
        }
        return values;
    }

    private String nodeToText(JsonNode node) {
        if (node == null || node.isNull()) return "";
        if (node.isTextual()) return node.asText().trim();
        if (node.isObject()) {
            StringBuilder builder = new StringBuilder();
            appendNodeField(builder, node, "time");
            appendNodeField(builder, node, "title");
            appendNodeField(builder, node, "step");
            appendNodeField(builder, node, "content");
            appendNodeField(builder, node, "description");
            return builder.toString().trim();
        }
        return node.asText("").trim();
    }

    private void appendNodeField(StringBuilder builder, JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isValueNode()) return;
        String text = value.asText("").trim();
        if (text.isEmpty()) return;
        if (builder.length() > 0) builder.append(" ");
        builder.append(text);
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

    private PlannerResponse localPlan(PlannerRequest request, boolean aiGenerated, String notice) {
        String theme = DbSupport.safe(request.getTheme(), "城市活动");
        return new PlannerResponse(
                theme.length() > 18 ? theme.substring(0, 18) + "计划" : theme + "计划",
                "围绕「" + theme + "」设计一场新朋友也能自然加入的线下活动。",
                Arrays.asList("城市探索", DbSupport.safe(request.getStyle(), "轻松社交"), DbSupport.safe(request.getPeople(), "12-20 人")),
                Arrays.asList("集合签到与破冰", "主题体验与分组任务", "交流分享与合影", "活动结束与后续联系"),
                "发布前请确认集合地点、天气预案、紧急联系人和交通返程安排。",
                aiGenerated,
                notice
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

    private List<CommonDtos.GeoPoint> parseAmapPlaces(Map response, String fallbackCity) {
        Object rawPois = response == null ? null : response.get("pois");
        if (!(rawPois instanceof List)) return Collections.emptyList();
        List<CommonDtos.GeoPoint> result = new ArrayList<CommonDtos.GeoPoint>();
        for (Object raw : (List) rawPois) {
            Map<String, Object> poi = asMap(raw);
            String[] location = DbSupport.safe(String.valueOf(poi.get("location")), "").split(",");
            if (location.length != 2) continue;
            try {
                CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
                point.setName(DbSupport.safe(String.valueOf(poi.get("name")), "地点"));
                point.setAddress(cleanValue(poi.get("address"), point.getName()));
                point.setCity(normalizeCity(DbSupport.safe(String.valueOf(poi.get("cityname")), fallbackCity)));
                point.setDistrict(DbSupport.safe(String.valueOf(poi.get("adname")), point.getCity()));
                point.setLongitude(new BigDecimal(location[0]));
                point.setLatitude(new BigDecimal(location[1]));
                result.add(point);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<CommonDtos.GeoPoint> searchNominatim(String keyword, String city) {
        String normalizedKeyword = DbSupport.safe(keyword, "").trim();
        if (normalizedKeyword.isEmpty()) return Collections.emptyList();
        String cacheKey = "search|" + city + "|" + normalizedKeyword.toLowerCase(Locale.CHINA);
        GeoCacheEntry cached = geoCache.get(cacheKey);
        if (cached != null && !cached.expired(nominatimCacheTtlMs)) {
            return new ArrayList<CommonDtos.GeoPoint>((List<CommonDtos.GeoPoint>) cached.value);
        }
        long started = System.currentTimeMillis();
        try {
            String viewbox = "北京".equals(city) ? "115.4,41.1,117.5,39.4" : "119.0,31.0,121.5,29.3";
            String url = nominatimBaseUrl + "/search?format=jsonv2&addressdetails=1&namedetails=1&limit=5&countrycodes=cn&bounded=1&viewbox="
                    + viewbox + "&q=" + encode(normalizedKeyword + ", " + city);
            Object response = nominatimGet(url, List.class);
            List<CommonDtos.GeoPoint> result = new ArrayList<CommonDtos.GeoPoint>();
            if (response instanceof List) {
                for (Object raw : (List) response) {
                    Map<String, Object> row = asMap(raw);
                    CommonDtos.GeoPoint point = parseNominatimPoint(row, city, null, null);
                    if (point != null && city.equals(point.getCity())) result.add(point);
                }
            }
            cache(cacheKey, result);
            String responseSummary = result.isEmpty() ? "0 / raw=" + trim(String.valueOf(response)) : String.valueOf(result.size());
            logThirdParty("NOMINATIM", "PLACE_SEARCH", "SUCCESS", normalizedKeyword + " / " + city, responseSummary, "", elapsed(started));
            return result;
        } catch (Exception ex) {
            logThirdParty("NOMINATIM", "PLACE_SEARCH", "FAILED", normalizedKeyword + " / " + city, "", trim(ex.getMessage()), elapsed(started));
            return exactFallbackPlaces(normalizedKeyword, city);
        }
    }

    private CommonDtos.GeoPoint reverseNominatim(BigDecimal longitude, BigDecimal latitude, CommonDtos.GeoPoint fallback) {
        String cacheKey = "reverse|" + longitude.setScale(5, RoundingMode.HALF_UP) + "|" + latitude.setScale(5, RoundingMode.HALF_UP);
        GeoCacheEntry cached = geoCache.get(cacheKey);
        if (cached != null && !cached.expired(nominatimCacheTtlMs)) return (CommonDtos.GeoPoint) cached.value;
        long started = System.currentTimeMillis();
        try {
            String url = nominatimBaseUrl + "/reverse?format=jsonv2&addressdetails=1&namedetails=1&zoom=18&lat="
                    + encode(latitude.toPlainString()) + "&lon=" + encode(longitude.toPlainString());
            Object response = nominatimGet(url, Map.class);
            CommonDtos.GeoPoint point = parseNominatimPoint(asMap(response), fallback.getCity(), longitude, latitude);
            if (point == null || point.getName() == null || point.getName().trim().isEmpty()) return fallback;
            cache(cacheKey, point);
            logThirdParty("NOMINATIM", "REVERSE_GEOCODE", "SUCCESS", longitude + "," + latitude, point.getName(), "", elapsed(started));
            return point;
        } catch (Exception ex) {
            logThirdParty("NOMINATIM", "REVERSE_GEOCODE", "FAILED", longitude + "," + latitude, "", trim(ex.getMessage()), elapsed(started));
            return fallback;
        }
    }

    private CommonDtos.GeoPoint parseNominatimPoint(Map<String, Object> row, String fallbackCity,
                                                     BigDecimal forcedLongitude, BigDecimal forcedLatitude) {
        if (row == null || row.isEmpty()) return null;
        Map<String, Object> address = asMap(row.get("address"));
        String displayName = cleanValue(row.get("display_name"), "");
        String city = cityFromNominatim(address, displayName, fallbackCity);
        BigDecimal longitude = forcedLongitude == null ? decimalValue(row.get("lon")) : forcedLongitude;
        BigDecimal latitude = forcedLatitude == null ? decimalValue(row.get("lat")) : forcedLatitude;
        if (longitude == null || latitude == null) return null;
        String district = firstNonBlank(address, "city_district", "district", "city", "county", "suburb");
        if (district.isEmpty() || "北京市".equals(district) || "杭州市".equals(district)) district = nearestDistrict(city, longitude, latitude);
        String name = cleanValue(row.get("name"), "");
        if (name.isEmpty()) name = firstNonBlank(address, "amenity", "building", "shop", "tourism", "leisure", "road", "neighbourhood", "suburb");
        if (name.isEmpty() && !displayName.isEmpty()) name = displayName.split(",")[0].trim();
        if (name.isEmpty()) return null;
        CommonDtos.GeoPoint point = geoPoint(name, city, district, longitude, latitude);
        point.setAddress(displayName.isEmpty() ? name : displayName);
        return point;
    }

    private Object nominatimGet(String url, Class responseType) throws InterruptedException {
        synchronized (nominatimLock) {
            long waitMs = nominatimMinIntervalMs - (System.currentTimeMillis() - lastNominatimRequestAt);
            if (waitMs > 0) Thread.sleep(waitMs);
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", nominatimUserAgent);
            headers.set("Referer", publicBaseUrl);
            headers.set("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.5");
            Object body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<Void>(headers), responseType).getBody();
            lastNominatimRequestAt = System.currentTimeMillis();
            return body;
        }
    }

    private void cache(String key, Object value) {
        if (geoCache.size() >= 512) geoCache.clear();
        geoCache.put(key, new GeoCacheEntry(value));
    }

    private String cityFromNominatim(Map<String, Object> address, String displayName, String fallbackCity) {
        String iso = cleanValue(address.get("ISO3166-2-lvl4"), "");
        if ("CN-BJ".equalsIgnoreCase(iso) || displayName.contains("北京市")) return "北京";
        if ("CN-ZJ".equalsIgnoreCase(iso) || displayName.contains("杭州市")) return "杭州";
        return normalizeCity(fallbackCity);
    }

    private String firstNonBlank(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = cleanValue(values.get(key), "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private String cleanValue(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? fallback : text;
    }

    private BigDecimal decimalValue(Object value) {
        try {
            String text = cleanValue(value, "");
            return text.isEmpty() ? null : new BigDecimal(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<CommonDtos.GeoPoint> exactFallbackPlaces(String keyword, String city) {
        String[][] rows = "北京".equals(city)
                ? new String[][] {
                    {"奥林匹克森林公园南门", "朝阳区", "116.392891", "40.015120"},
                    {"国家图书馆", "海淀区", "116.325190", "39.943047"},
                    {"北京坊", "西城区", "116.397910", "39.898215"},
                    {"798艺术区", "朝阳区", "116.495570", "39.984110"}
                }
                : new String[][] {
                    {"桥西历史文化街区", "拱墅区", "120.139863", "30.318332"},
                    {"九溪公交站", "西湖区", "120.119235", "30.209840"},
                    {"湖滨银泰 IN77", "上城区", "120.168929", "30.255672"},
                    {"天目里社区中心", "西湖区", "120.121900", "30.283300"}
                };
        String search = DbSupport.safe(keyword, "").trim().toLowerCase(Locale.CHINA);
        List<CommonDtos.GeoPoint> result = new ArrayList<CommonDtos.GeoPoint>();
        for (String[] row : rows) {
            if (!search.isEmpty() && !row[0].toLowerCase(Locale.CHINA).contains(search) && !row[1].contains(search)) continue;
            result.add(geoPoint(row[0], city, row[1], row[2], row[3]));
        }
        return result;
    }

    private CommonDtos.GeoPoint fallbackReversePoint(BigDecimal longitude, BigDecimal latitude) {
        String city = cityForCoordinate(longitude, latitude);
        String district = nearestDistrict(city, longitude, latitude);
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setName("");
        point.setAddress("");
        point.setCity(city);
        point.setDistrict(district);
        point.setLongitude(longitude);
        point.setLatitude(latitude);
        return point;
    }

    private String nearestDistrict(String city, BigDecimal longitude, BigDecimal latitude) {
        double lng = longitude.doubleValue();
        double lat = latitude.doubleValue();
        Object[][] centers = "北京".equals(city) ? new Object[][] {
                {"东城区", 116.4188, 39.9175},
                {"西城区", 116.3668, 39.9153},
                {"朝阳区", 116.4436, 39.9219},
                {"海淀区", 116.2981, 39.9593},
                {"丰台区", 116.2867, 39.8584},
                {"石景山区", 116.2229, 39.9066},
                {"通州区", 116.6571, 39.9097},
                {"昌平区", 116.2312, 40.2207}
        } : new Object[][] {
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

    private String cityForCoordinate(BigDecimal longitude, BigDecimal latitude) {
        double lng = longitude.doubleValue();
        double lat = latitude.doubleValue();
        return lng >= 115D && lng <= 118D && lat >= 39D && lat <= 41.5D ? "北京" : "杭州";
    }

    private String normalizeCity(String city) {
        String value = DbSupport.safe(city, "杭州").replace("市", "").trim();
        return "北京".equals(value) ? "北京" : "杭州";
    }

    private CommonDtos.GeoPoint geoPoint(String name, String city, String district, String longitude, String latitude) {
        return geoPoint(name, city, district, new BigDecimal(longitude), new BigDecimal(latitude));
    }

    private CommonDtos.GeoPoint geoPoint(String name, String city, String district, BigDecimal longitude, BigDecimal latitude) {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setName(name);
        point.setAddress(name);
        point.setCity(city);
        point.setDistrict(district);
        point.setLongitude(longitude);
        point.setLatitude(latitude);
        return point;
    }

    private static class GeoCacheEntry {
        private final Object value;
        private final long createdAt = System.currentTimeMillis();
        private GeoCacheEntry(Object value) { this.value = value; }
        private boolean expired(long ttlMs) { return System.currentTimeMillis() - createdAt > ttlMs; }
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception ex) {
            return value;
        }
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
        configureProxyFromEnvironment();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }

    private static void configureProxyFromEnvironment() {
        String proxyUrl = System.getenv("HTTPS_PROXY");
        if (proxyUrl == null || proxyUrl.trim().isEmpty()) proxyUrl = System.getenv("https_proxy");
        if (proxyUrl == null || proxyUrl.trim().isEmpty()) return;
        try {
            URI proxy = URI.create(proxyUrl);
            if (proxy.getHost() == null || proxy.getPort() < 1) return;
            System.setProperty("https.proxyHost", proxy.getHost());
            System.setProperty("https.proxyPort", String.valueOf(proxy.getPort()));
            System.setProperty("http.proxyHost", proxy.getHost());
            System.setProperty("http.proxyPort", String.valueOf(proxy.getPort()));
            System.setProperty("http.nonProxyHosts", "localhost|127.*|[::1]");
        } catch (Exception ignored) {
            // Invalid proxy environment variables should not prevent application startup.
        }
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
