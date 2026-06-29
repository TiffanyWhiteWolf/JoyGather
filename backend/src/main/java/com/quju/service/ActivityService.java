package com.quju.service;

import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.ActivityDto;
import com.quju.dto.ActivityOpsDtos;
import com.quju.dto.RegistrationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class ActivityService {
    private final JdbcTemplate jdbc;
    private final UserService userService;
    private final IntegrationService integrationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActivityService(JdbcTemplate jdbc, UserService userService) {
        this(jdbc, userService, null);
    }

    @Autowired
    public ActivityService(JdbcTemplate jdbc, UserService userService, IntegrationService integrationService) {
        this.jdbc = jdbc;
        this.userService = userService;
        this.integrationService = integrationService;
    }

    public List<ActivityDto> findAll(String keyword, String category) {
        return findAll(keyword, category, null, null, null, null, "latest");
    }

    public List<ActivityDto> findAll(String keyword, String category, BigDecimal minLng, BigDecimal maxLng,
                                     BigDecimal minLat, BigDecimal maxLat, String sort) {
        List<ActivityDto> activities = jdbc.query(
                "select * from activities where status <> '草稿' and status <> '已下架' and visibility = 'PUBLIC' order by featured desc, published_at desc, created_at desc",
                activityMapper());
        return filter(activities, keyword, category, null, minLng, maxLng, minLat, maxLat, sort);
    }

    public List<ActivityDto> findAll(String keyword, String category, String categories, String city, String fee,
                                     String timeRange, BigDecimal distance, BigDecimal lat, BigDecimal lng,
                                     BigDecimal minLng, BigDecimal maxLng, BigDecimal minLat, BigDecimal maxLat,
                                     String sort, Integer page, Integer size) {
        List<ActivityDto> result = findAll(keyword, category, minLng, maxLng, minLat, maxLat, sort);
        List<ActivityDto> refined = new ArrayList<ActivityDto>();
        List<String> categoryList = categories == null || categories.trim().isEmpty() ? Collections.<String>emptyList() : DbSupport.split(categories);
        for (ActivityDto item : result) {
            if (!categoryList.isEmpty() && !categoryList.contains(item.getCategory())) continue;
            if (city != null && !city.trim().isEmpty() && !(DbSupport.safe(item.getDistrict(), "").contains(city.trim()) || DbSupport.safe(item.getLocation(), "").contains(city.trim()))) continue;
            if ("免费".equals(fee) && item.getPrice().compareTo(BigDecimal.ZERO) > 0) continue;
            if ("付费".equals(fee) && item.getPrice().compareTo(BigDecimal.ZERO) <= 0) continue;
            if (distance != null && item.getDistance() != null && item.getDistance().compareTo(distance) > 0) continue;
            if (timeRange != null && !timeRange.trim().isEmpty() && item.getStartAt() != null && !matchesTimeRange(item.getStartAt(), timeRange)) continue;
            refined.add(item);
        }
        int pageSize = size == null || size < 1 ? refined.size() : Math.min(size, 50);
        int pageIndex = page == null || page < 1 ? 1 : page;
        int from = Math.min(refined.size(), (pageIndex - 1) * pageSize);
        int to = Math.min(refined.size(), from + pageSize);
        return refined.subList(from, to);
    }

    public List<ActivityDto> findAllForAdmin(String keyword, String status) {
        List<ActivityDto> activities = jdbc.query("select * from activities order by created_at desc", activityMapper());
        return filter(activities, keyword, null, status, null, null, null, null, "latest");
    }

    public Optional<ActivityDto> findById(String id) {
        List<ActivityDto> items = jdbc.query("select * from activities where id = ?", activityMapper(), id);
        return items.isEmpty() ? Optional.<ActivityDto>empty() : Optional.of(items.get(0));
    }

    public List<ActivityDto> findDrafts(String userId) {
        return jdbc.query("select * from activities where status = '草稿' and organizer_id = ? order by updated_at desc", activityMapper(), userId);
    }

    public Map<String, String> myRegistrationStatus(String userId) {
        Map<String, String> result = new java.util.LinkedHashMap<String, String>();
        List<Map<String, Object>> rows = jdbc.queryForList("select activity_id,status from registrations where user_id = ? and status in ('已报名','候补中','已签到')", userId);
        for (Map<String, Object> row : rows) result.put(String.valueOf(row.get("activity_id")), String.valueOf(row.get("status")));
        return result;
    }

    @Transactional
    public ActivityDto create(ActivityCreateRequest request) {
        return create(request, DbSupport.safe(request.getOrganizerId(), UserService.DEFAULT_USER_ID));
    }

    @Transactional
    public ActivityDto create(ActivityCreateRequest request, String organizerId) {
        validateForSubmit(request);
        String id = DbSupport.id("act");
        IntegrationService.ModerationResult moderation = integrationService == null
                ? localModeration(request)
                : integrationService.moderateActivity(id, request.getTitle(), request.getSummary(), request.getTags(), request.getCapacity());
        String status = "LOW_RISK".equals(moderation.result) ? "报名中" : "审核中";
        jdbc.update("insert into activities (id,title,summary,description,category,cover,date_label,time_label,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,safety_note,min_age,join_fields,published_at,team_id,visibility,ai_review_status,ai_risk_labels,submit_token) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,case when ? = '报名中' then now() else null end,?,?,?,?,?)",
                id, request.getTitle(), request.getSummary(), DbSupport.safe(request.getDescription(), request.getSummary()),
                request.getCategory(), DbSupport.safe(request.getCover(), defaultCover(request.getCategory())), request.getDate(), normalizedTime(request),
                request.getLocation(), DbSupport.safe(request.getDistrict(), ""), BigDecimal.ZERO,
                request.getLongitude() == null ? new BigDecimal("50") : request.getLongitude(),
                request.getLatitude() == null ? new BigDecimal("50") : request.getLatitude(),
                request.getPrice() == null ? BigDecimal.ZERO : request.getPrice(), request.getCapacity(), 0,
                status, organizerId, false, request.getSafetyNote(), request.getMinAge(), DbSupport.join(request.getJoinFields()), status,
                request.getTeamId(), DbSupport.safe(request.getVisibility(), request.getTeamId() == null ? "PUBLIC" : "TEAM"),
                moderation.result, DbSupport.join(moderation.labels), request.getSubmitToken());
        applySchedule(id, request);
        replaceTags(id, request.getTags());
        if (integrationService != null) integrationService.saveAiAudit(id, moderation, moderation.reason);
        if ("审核中".equals(status)) createActivityReview(id, request, organizerId, moderation);
        return requireActivity(id);
    }

    @Transactional
    public ActivityDto saveDraft(ActivityCreateRequest request, String userId) {
        String id = DbSupport.id("draft");
        jdbc.update("insert into activities (id,title,summary,description,category,cover,date_label,time_label,location,district,distance,longitude,latitude,price,capacity,joined_count,status,organizer_id,featured,safety_note,min_age,join_fields) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, DbSupport.safe(request.getTitle(), "未命名草稿"), DbSupport.safe(request.getSummary(), "草稿暂未填写简介"),
                DbSupport.safe(request.getDescription(), request.getSummary()), DbSupport.safe(request.getCategory(), "城市探索"),
                defaultCover(request.getCategory()), DbSupport.safe(request.getDate(), ""), normalizedTime(request),
                DbSupport.safe(request.getLocation(), ""), DbSupport.safe(request.getDistrict(), ""), BigDecimal.ZERO,
                request.getLongitude() == null ? new BigDecimal("50") : request.getLongitude(),
                request.getLatitude() == null ? new BigDecimal("50") : request.getLatitude(),
                request.getPrice() == null ? BigDecimal.ZERO : request.getPrice(), Math.max(2, request.getCapacity()),
                0, "草稿", userId, false, request.getSafetyNote(), request.getMinAge(), DbSupport.join(request.getJoinFields()));
        applySchedule(id, request);
        replaceTags(id, request.getTags());
        return requireActivity(id);
    }

    @Transactional
    public ActivityDto updateDraft(String id, ActivityCreateRequest request, String userId) {
        int updated = jdbc.update("update activities set title = ?, summary = ?, description = ?, category = ?, cover = ?, date_label = ?, time_label = ?, location = ?, district = ?, longitude = ?, latitude = ?, price = ?, capacity = ?, safety_note = ?, min_age = ?, join_fields = ?, team_id = ?, visibility = ? where id = ? and organizer_id = ? and status = '草稿'",
                DbSupport.safe(request.getTitle(), "未命名草稿"), DbSupport.safe(request.getSummary(), "草稿暂未填写简介"),
                DbSupport.safe(request.getDescription(), request.getSummary()), DbSupport.safe(request.getCategory(), "城市探索"),
                DbSupport.safe(request.getCover(), defaultCover(request.getCategory())), DbSupport.safe(request.getDate(), ""), normalizedTime(request),
                DbSupport.safe(request.getLocation(), ""), DbSupport.safe(request.getDistrict(), ""),
                request.getLongitude() == null ? new BigDecimal("50") : request.getLongitude(),
                request.getLatitude() == null ? new BigDecimal("50") : request.getLatitude(),
                request.getPrice() == null ? BigDecimal.ZERO : request.getPrice(), Math.max(2, request.getCapacity()),
                request.getSafetyNote(), request.getMinAge(), DbSupport.join(request.getJoinFields()),
                request.getTeamId(), DbSupport.safe(request.getVisibility(), request.getTeamId() == null ? "PUBLIC" : "TEAM"),
                id, userId);
        if (updated == 0) throw new NoSuchElementException("草稿不存在或不可编辑");
        applySchedule(id, request);
        replaceTags(id, request.getTags());
        return requireActivity(id);
    }

    @Transactional
    public ActivityDto submitDraft(String id) {
        return submitDraft(id, null);
    }

    @Transactional
    public ActivityDto submitDraft(String id, String userId) {
        ActivityDto draft = requireActivity(id);
        if (!"草稿".equals(draft.getStatus())) return draft;
        if (userId != null && !userId.equals(draft.getOrganizer().getId())) throw new IllegalStateException("只能提交自己的草稿");
        if (draft.getTitle() == null || draft.getTitle().trim().isEmpty() || draft.getLocation() == null || draft.getLocation().trim().isEmpty()
                || draft.getDeadline() == null || draft.getStartAt() == null || draft.getEndAt() == null) {
            throw new IllegalStateException("草稿信息不完整，请补全必填字段后提交");
        }
        if (LocalDateTime.now().isAfter(LocalDateTime.parse(draft.getDeadline()))) throw new IllegalStateException("报名截止时间已过，请修改后提交");
        IntegrationService.ModerationResult moderation = integrationService == null
                ? new IntegrationService.ModerationResult(draft.getCapacity() > 50 ? "REVIEW_REQUIRED" : "LOW_RISK", draft.getCapacity() > 50 ? "中" : "低", draft.getCapacity() > 50 ? "报名人数超过 50 人，转入人工审核" : "规则审核低风险", Collections.<String>emptyList())
                : integrationService.moderateActivity(id, draft.getTitle(), draft.getSummary(), draft.getTags(), draft.getCapacity());
        String status = "LOW_RISK".equals(moderation.result) ? "报名中" : "审核中";
        jdbc.update("update activities set status = ?, ai_review_status = ?, ai_risk_labels = ?, published_at = case when ? = '报名中' then now() else null end where id = ?", status, moderation.result, DbSupport.join(moderation.labels), status, id);
        if (integrationService != null) integrationService.saveAiAudit(id, moderation, moderation.reason);
        if ("审核中".equals(status)) {
            jdbc.update("insert into review_tasks (id,type,target_id,title,submitter,risk,reason,status) values (?,?,?,?,?,?,?,?)",
                    DbSupport.id("rv"), "活动审核", id, draft.getTitle(), draft.getOrganizer().getNickname(), moderation.risk, moderation.reason, "待审核");
        }
        return requireActivity(id);
    }

    @Transactional
    public ActivityDto cloneAsDraft(String id, String userId) {
        ActivityDto source = requireActivity(id);
        ActivityCreateRequest request = new ActivityCreateRequest();
        request.setTitle(source.getTitle() + "（复刻）");
        request.setSummary(source.getSummary());
        request.setDescription(source.getDescription());
        request.setCategory(source.getCategory());
        request.setDate("");
        request.setTime("");
        request.setLocation(source.getLocation());
        request.setDistrict(source.getDistrict());
        request.setLongitude(source.getLongitude());
        request.setLatitude(source.getLatitude());
        request.setPrice(source.getPrice());
        request.setCapacity(source.getCapacity());
        request.setTags(source.getTags());
        request.setSafetyNote(source.getSafetyNote());
        request.setMinAge(source.getMinAge());
        request.setJoinFields(source.getJoinFields());
        return saveDraft(request, userId);
    }

    @Transactional
    public void deleteDraft(String id, String userId) {
        int updated = jdbc.update("delete from activities where id = ? and organizer_id = ? and status = '草稿'", id, userId);
        if (updated == 0) throw new NoSuchElementException("草稿不存在或不可删除");
    }

    @Transactional
    public RegistrationResult register(String activityId, String userId) {
        return register(activityId, userId, null);
    }

    @Transactional
    public RegistrationResult register(String activityId, String userId, Map<String, String> fields) {
        ActivityDto activity = lockActivity(activityId);
        validateCanJoin(activity, userId);
        if (!"报名中".equals(activity.getStatus()) && !"即将开始".equals(activity.getStatus())) {
            throw new IllegalStateException("当前活动不可报名");
        }
        List<String> statuses = jdbc.queryForList("select status from registrations where activity_id = ? and user_id = ?", String.class, activityId, userId);
        if (!statuses.isEmpty()) {
            String status = statuses.get(0);
            if ("已取消".equals(status)) {
                jdbc.update("delete from registrations where activity_id = ? and user_id = ?", activityId, userId);
            } else {
            int position = "候补中".equals(status) ? queuePosition(activityId, userId) : 0;
            return new RegistrationResult(activityId, userId, status, position, null);
            }
        }
        if (activity.getJoined() >= activity.getCapacity()) {
            int position = nextQueuePosition(activityId);
            jdbc.update("insert into registrations (id,activity_id,user_id,status,queue_position,form_data) values (?,?,?,?,?,?)",
                    DbSupport.id("reg"), activityId, userId, "候补中", position, json(fields));
            return new RegistrationResult(activityId, userId, "候补中", position, null);
        }
        jdbc.update("insert into registrations (id,activity_id,user_id,status,queue_position,form_data) values (?,?,?,?,0,?)",
                DbSupport.id("reg"), activityId, userId, "已报名", json(fields));
        jdbc.update("update activities set joined_count = joined_count + 1 where id = ?", activityId);
        return new RegistrationResult(activityId, userId, "已报名", 0, null);
    }

    @Transactional
    public RegistrationResult cancel(String activityId, String userId) {
        ActivityDto activity = lockActivity(activityId);
        if (activity.getDeadline() != null && !activity.getDeadline().trim().isEmpty()
                && LocalDateTime.now().isAfter(LocalDateTime.parse(activity.getDeadline()))) {
            throw new IllegalStateException("报名截止后不允许取消");
        }
        List<String> statuses = jdbc.queryForList("select status from registrations where activity_id = ? and user_id = ?", String.class, activityId, userId);
        if (statuses.isEmpty()) throw new IllegalStateException("没有可取消的报名记录");
        String status = statuses.get(0);
        jdbc.update("update registrations set status = '已取消', queue_position = 0 where activity_id = ? and user_id = ?", activityId, userId);
        String promoted = null;
        if ("已报名".equals(status)) {
            jdbc.update("update activities set joined_count = greatest(0, joined_count - 1) where id = ?", activityId);
            List<String> waiting = jdbc.queryForList("select user_id from registrations where activity_id = ? and status = '候补中' order by queue_position asc, created_at asc limit 1", String.class, activityId);
            if (!waiting.isEmpty()) {
                promoted = waiting.get(0);
                jdbc.update("update registrations set status = '已报名', queue_position = 0 where activity_id = ? and user_id = ?", activityId, promoted);
                jdbc.update("update activities set joined_count = least(capacity, joined_count + 1) where id = ?", activityId);
                renumberQueue(activityId);
            }
        } else {
            renumberQueue(activityId);
        }
        return new RegistrationResult(activityId, userId, "已取消", 0, promoted);
    }

    @Transactional
    public RegistrationResult confirmWaitlist(String activityId, String userId) {
        ActivityDto activity = lockActivity(activityId);
        List<String> statuses = jdbc.queryForList("select status from registrations where activity_id = ? and user_id = ?", String.class, activityId, userId);
        if (statuses.isEmpty() || !"候补中".equals(statuses.get(0))) throw new IllegalStateException("没有可确认的候补机会");
        if (activity.getJoined() >= activity.getCapacity()) throw new IllegalStateException("当前暂无可递补名额");
        jdbc.update("update registrations set status = '已报名', queue_position = 0 where activity_id = ? and user_id = ?", activityId, userId);
        jdbc.update("update activities set joined_count = joined_count + 1 where id = ?", activityId);
        renumberQueue(activityId);
        return new RegistrationResult(activityId, userId, "已报名", 0, null);
    }

    @Transactional
    public ActivityOpsDtos.CheckinCodeResponse createCheckinCode(String activityId, String organizerId, boolean locationRequired) {
        ActivityDto activity = requireActivity(activityId);
        if (!organizerId.equals(activity.getOrganizer().getId())) throw new IllegalStateException("只有发起人可以生成签到码");
        String code = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(12);
        jdbc.update("insert into activity_checkin_codes (id,activity_id,organizer_id,code,location_required,expires_at) values (?,?,?,?,?,?)",
                DbSupport.id("ck"), activityId, organizerId, code, locationRequired, Timestamp.valueOf(expiresAt));
        return new ActivityOpsDtos.CheckinCodeResponse(code, "/check-in?code=" + code, expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @Transactional
    public RegistrationResult scanCheckin(String code, String userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from activity_checkin_codes where code = ? and (expires_at is null or expires_at > now())", code);
        if (rows.isEmpty()) throw new IllegalStateException("签到码无效或已过期");
        String activityId = String.valueOf(rows.get(0).get("activity_id"));
        List<String> statuses = jdbc.queryForList("select status from registrations where activity_id = ? and user_id = ?", String.class, activityId, userId);
        if (statuses.isEmpty() || (!"已报名".equals(statuses.get(0)) && !"已签到".equals(statuses.get(0)))) throw new IllegalStateException("未报名用户不可签到");
        jdbc.update("update registrations set status = '已签到', checked_in_at = coalesce(checked_in_at, now()) where activity_id = ? and user_id = ?", activityId, userId);
        return new RegistrationResult(activityId, userId, "已签到", 0, null);
    }

    @Transactional
    public ActivityOpsDtos.SummaryDto publishSummary(String activityId, String userId, ActivityOpsDtos.SummaryRequest request) {
        ActivityDto activity = requireActivity(activityId);
        if (!userId.equals(activity.getOrganizer().getId())) throw new IllegalStateException("只有发起人可以发布活动总结");
        if (!"已结束".equals(activity.getStatus())) throw new IllegalStateException("活动结束后才能发布正式总结");
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) throw new IllegalStateException("总结标题不能为空");
        if (request.getContent() == null || request.getContent().trim().isEmpty()) throw new IllegalStateException("总结正文不能为空");
        String summaryId = DbSupport.id("sum");
        jdbc.update("insert into activity_summaries (id,activity_id,author_id,title,content) values (?,?,?,?,?)",
                summaryId, activityId, userId, request.getTitle().trim(), request.getContent().trim());
        List<String> categories = integrationService == null ? Collections.<String>emptyList() : integrationService.classifyImages(request.getImageUrls());
        int order = 1;
        if (request.getImageUrls() != null) {
            for (int i = 0; i < request.getImageUrls().size(); i++) {
                String aiCategory = i < categories.size() ? categories.get(i) : "过程记录";
                String confirmed = request.getConfirmedCategories() != null && i < request.getConfirmedCategories().size() ? request.getConfirmedCategories().get(i) : aiCategory;
                jdbc.update("insert into summary_images (id,summary_id,url,ai_category,confirmed_category,rank_order) values (?,?,?,?,?,?)",
                        DbSupport.id("simg"), summaryId, request.getImageUrls().get(i), aiCategory, confirmed, order++);
            }
        }
        ActivityOpsDtos.SummaryDto dto = new ActivityOpsDtos.SummaryDto();
        dto.setId(summaryId);
        dto.setActivityId(activityId);
        dto.setTitle(request.getTitle());
        dto.setContent(request.getContent());
        dto.setImageUrls(request.getImageUrls());
        dto.setCategories(categories);
        return dto;
    }

    @Transactional
    public void reviewActivity(String activityId, String userId, ActivityOpsDtos.ReviewRequest request) {
        ActivityDto activity = requireActivity(activityId);
        if (!"已结束".equals(activity.getStatus())) throw new IllegalStateException("活动结束后才能评价");
        Integer count = jdbc.queryForObject("select count(*) from registrations where activity_id = ? and user_id = ? and status in ('已报名','已签到')", Integer.class, activityId, userId);
        if (count == null || count == 0) throw new IllegalStateException("未参加活动不可评价");
        if (request.getRating() < 1 || request.getRating() > 5) throw new IllegalStateException("评分必须在 1 到 5 之间");
        jdbc.update("insert into activity_reviews_user (id,activity_id,user_id,rating,content) values (?,?,?,?,?) on duplicate key update rating = values(rating), content = values(content), created_at = now()",
                DbSupport.id("arev"), activityId, userId, request.getRating(), request.getContent());
    }

    @Transactional
    public void takeOffline(String id, String reason, String actorId) {
        if (reason == null || reason.trim().isEmpty()) throw new IllegalStateException("下架原因不能为空");
        jdbc.update("update activities set status = '已下架', offline_reason = ? where id = ?", reason.trim(), id);
        log(actorId, "OFFLINE_ACTIVITY", "ACTIVITY", id, reason);
    }

    @Transactional
    public void restore(String id, String actorId) {
        jdbc.update("update activities set status = '报名中', offline_reason = null, published_at = coalesce(published_at, now()) where id = ?", id);
        log(actorId, "RESTORE_ACTIVITY", "ACTIVITY", id, "");
    }

    @Transactional
    public void handleReview(String id, String result, String reason, String handlerId) {
        if (("已驳回".equals(result) || "要求修改".equals(result)) && (reason == null || reason.trim().isEmpty())) {
            throw new IllegalStateException("驳回或要求修改时必须填写原因");
        }
        jdbc.update("update review_tasks set status = ?, handled_at = now(), handler_id = ?, handler_reason = ? where id = ?",
                result, handlerId, reason, id);
        List<String> targetIds = jdbc.queryForList("select target_id from review_tasks where id = ?", String.class, id);
        if (!targetIds.isEmpty() && id.startsWith("rv")) {
            String activityId = targetIds.get(0);
            if ("已通过".equals(result)) jdbc.update("update activities set status = '报名中', published_at = now() where id = ?", activityId);
            if ("已驳回".equals(result)) jdbc.update("update activities set status = '已下架', offline_reason = ? where id = ?", reason, activityId);
        }
        log(handlerId, "HANDLE_REVIEW", "REVIEW", id, result + ":" + DbSupport.safe(reason, ""));
    }

    private List<ActivityDto> filter(List<ActivityDto> items, String keyword, String category, String status,
                                     BigDecimal minLng, BigDecimal maxLng, BigDecimal minLat, BigDecimal maxLat, String sort) {
        String normalized = keyword == null ? "" : keyword.toLowerCase(Locale.CHINA);
        List<ActivityDto> result = new ArrayList<ActivityDto>();
        for (ActivityDto item : items) {
            String haystack = (item.getTitle() + item.getSummary() + item.getDescription() + DbSupport.join(item.getTags())).toLowerCase(Locale.CHINA);
            if (!normalized.isEmpty() && !haystack.contains(normalized)) continue;
            if (category != null && !category.isEmpty() && !category.equals(item.getCategory())) continue;
            if (status != null && !status.isEmpty() && !status.startsWith("全部") && !status.equals(item.getStatus())) continue;
            if (minLng != null && item.getLongitude().compareTo(minLng) < 0) continue;
            if (maxLng != null && item.getLongitude().compareTo(maxLng) > 0) continue;
            if (minLat != null && item.getLatitude().compareTo(minLat) < 0) continue;
            if (maxLat != null && item.getLatitude().compareTo(maxLat) > 0) continue;
            result.add(item);
        }
        if ("nearby".equals(sort)) {
            Collections.sort(result, (a, b) -> a.getDistance().compareTo(b.getDistance()));
        } else if ("recommended".equals(sort)) {
            Collections.sort(result, (a, b) -> Boolean.compare(Boolean.TRUE.equals(b.getFeatured()), Boolean.TRUE.equals(a.getFeatured())));
        }
        return result;
    }

    private boolean matchesTimeRange(String startAt, String timeRange) {
        try {
            LocalDate date = LocalDateTime.parse(startAt).toLocalDate();
            LocalDate today = LocalDate.now();
            if ("今天".equals(timeRange) || "today".equalsIgnoreCase(timeRange)) return date.equals(today);
            if ("本周".equals(timeRange) || "week".equalsIgnoreCase(timeRange)) return !date.isBefore(today) && !date.isAfter(today.plusDays(7));
            if ("周末".equals(timeRange) || "weekend".equalsIgnoreCase(timeRange)) {
                java.time.DayOfWeek day = date.getDayOfWeek();
                return day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY;
            }
            return true;
        } catch (Exception ex) {
            return true;
        }
    }

    private ActivityDto requireActivity(String id) {
        return findById(id).orElseThrow(() -> new NoSuchElementException("活动不存在"));
    }

    private ActivityDto lockActivity(String id) {
        List<ActivityDto> items = jdbc.query("select * from activities where id = ? for update", activityMapper(), id);
        if (items.isEmpty()) throw new NoSuchElementException("活动不存在");
        return items.get(0);
    }

    private RowMapper<ActivityDto> activityMapper() {
        return new RowMapper<ActivityDto>() {
            public ActivityDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                ActivityDto activity = new ActivityDto();
                activity.setId(rs.getString("id"));
                activity.setTitle(rs.getString("title"));
                activity.setSummary(rs.getString("summary"));
                activity.setDescription(rs.getString("description"));
                activity.setCategory(rs.getString("category"));
                activity.setCover(rs.getString("cover"));
                activity.setDate(rs.getString("date_label"));
                activity.setTime(rs.getString("time_label"));
                activity.setStartAt(formatDateTime(rs.getTimestamp("start_at")));
                activity.setEndAt(formatDateTime(rs.getTimestamp("end_at")));
                activity.setDeadline(formatDateTime(rs.getTimestamp("registration_deadline")));
                activity.setLocation(rs.getString("location"));
                activity.setDistrict(rs.getString("district"));
                activity.setDistance(rs.getBigDecimal("distance"));
                activity.setLongitude(rs.getBigDecimal("longitude"));
                activity.setLatitude(rs.getBigDecimal("latitude"));
                activity.setPrice(rs.getBigDecimal("price"));
                activity.setCapacity(rs.getInt("capacity"));
                activity.setJoined(rs.getInt("joined_count"));
                activity.setStatus(effectiveStatus(rs.getString("status"), rs.getTimestamp("start_at"), rs.getTimestamp("end_at"), rs.getTimestamp("registration_deadline")));
                activity.setFeatured(rs.getBoolean("featured"));
                activity.setSafetyNote(rs.getString("safety_note"));
                activity.setMinAge(rs.getInt("min_age"));
                activity.setJoinFields(DbSupport.split(rs.getString("join_fields")));
                activity.setTags(tagsFor(activity.getId()));
                activity.setOrganizer(userService.findById(rs.getString("organizer_id")));
                return activity;
            }
        };
    }

    private List<String> tagsFor(String activityId) {
        return jdbc.queryForList("select tag from activity_tags where activity_id = ? order by rank_order asc", String.class, activityId);
    }

    private void replaceTags(String activityId, List<String> tags) {
        jdbc.update("delete from activity_tags where activity_id = ?", activityId);
        List<String> values = tags == null ? Collections.<String>emptyList() : tags;
        int order = 1;
        for (String tag : values) {
            if (tag == null || tag.trim().isEmpty()) continue;
            jdbc.update("insert into activity_tags (activity_id,tag,rank_order) values (?,?,?)", activityId, tag.trim(), order++);
        }
    }

    private void validateForSubmit(ActivityCreateRequest request) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) throw new IllegalStateException("活动名称不能为空");
        if (request.getSummary() == null || request.getSummary().trim().isEmpty()) throw new IllegalStateException("活动简介不能为空");
        if (request.getCategory() == null || request.getCategory().trim().isEmpty()) throw new IllegalStateException("活动类型不能为空");
        if (request.getDate() == null || request.getDate().trim().isEmpty()) throw new IllegalStateException("活动日期不能为空");
        if (request.getTime() == null && (request.getStartTime() == null || request.getEndTime() == null)) throw new IllegalStateException("活动时间不能为空");
        if (request.getLocation() == null || request.getLocation().trim().isEmpty()) throw new IllegalStateException("活动地点不能为空");
        if (request.getCapacity() < 2) throw new IllegalStateException("活动人数上限至少为2人");
        LocalDateTime startAt = parseStartAt(request);
        LocalDateTime endAt = parseEndAt(request);
        LocalDateTime deadline = parseDeadline(request);
        if (startAt == null || endAt == null || deadline == null) throw new IllegalStateException("活动开始、结束和报名截止时间不能为空");
        if (!endAt.isAfter(startAt)) throw new IllegalStateException("活动结束时间需要晚于开始时间");
        if (!startAt.isAfter(LocalDateTime.now())) throw new IllegalStateException("活动开始时间需要晚于当前时间");
        if (deadline.isAfter(startAt)) throw new IllegalStateException("报名截止时间不能晚于活动开始时间");
    }

    private boolean requiresManualReview(ActivityCreateRequest request) {
        String text = (request.getTitle() + " " + request.getSummary() + " " + DbSupport.join(request.getTags())).toLowerCase(Locale.CHINA);
        return request.getCapacity() > 50 || text.contains("危险") || text.contains("酒吧") || text.contains("凌晨") || text.contains("水上");
    }

    private IntegrationService.ModerationResult localModeration(ActivityCreateRequest request) {
        boolean manual = requiresManualReview(request);
        return new IntegrationService.ModerationResult(manual ? "REVIEW_REQUIRED" : "LOW_RISK",
                manual ? "中" : "低",
                manual ? "报名人数或风险词触发人工审核" : "规则审核低风险",
                manual ? java.util.Arrays.asList("RULE_REVIEW") : Collections.<String>emptyList());
    }

    private void applySchedule(String id, ActivityCreateRequest request) {
        LocalDateTime startAt = parseStartAt(request);
        LocalDateTime endAt = parseEndAt(request);
        LocalDateTime deadline = parseDeadline(request);
        jdbc.update("update activities set start_at = ?, end_at = ?, registration_deadline = ? where id = ?",
                startAt == null ? null : Timestamp.valueOf(startAt),
                endAt == null ? null : Timestamp.valueOf(endAt),
                deadline == null ? null : Timestamp.valueOf(deadline),
                id);
    }

    private void validateCanJoin(ActivityDto activity, String userId) {
        com.quju.dto.UserDto user = userService.findById(userId);
        if (!"正常".equals(user.getStatus())) throw new IllegalStateException("账号状态不可报名：" + user.getStatus());
        if (user.getCredit() < 80) throw new IllegalStateException("信用分低于 80，暂不能报名");
        if (activity.getMinAge() > 0 && ageOf(user) < activity.getMinAge()) throw new IllegalStateException("当前账号年龄不满足最低年龄要求");
        if (activity.getDeadline() != null && !activity.getDeadline().trim().isEmpty()
                && LocalDateTime.now().isAfter(LocalDateTime.parse(activity.getDeadline()))) {
            throw new IllegalStateException("报名已截止");
        }
    }

    private int ageOf(com.quju.dto.UserDto user) {
        if (user.getBirthday() == null || user.getBirthday().trim().isEmpty()) return 24;
        try {
            return java.time.Period.between(LocalDate.parse(user.getBirthday()), LocalDate.now()).getYears();
        } catch (Exception ex) {
            return 24;
        }
    }

    private String effectiveStatus(String stored, Timestamp startAt, Timestamp endAt, Timestamp deadline) {
        if ("草稿".equals(stored) || "审核中".equals(stored) || "已下架".equals(stored) || "已结束".equals(stored)) return stored;
        LocalDateTime now = LocalDateTime.now();
        if (endAt != null && now.isAfter(endAt.toLocalDateTime())) return "已结束";
        if (startAt != null && !now.isBefore(startAt.toLocalDateTime()) && (endAt == null || now.isBefore(endAt.toLocalDateTime()))) return "进行中";
        if (deadline != null && now.isAfter(deadline.toLocalDateTime())) return "已截止";
        return stored;
    }

    private String formatDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private LocalDateTime parseStartAt(ActivityCreateRequest request) {
        LocalDate date = parseDate(request.getDate());
        LocalTime time = parseTime(request.getStartTime(), request.getTime(), true);
        return date == null || time == null ? null : LocalDateTime.of(date, time);
    }

    private LocalDateTime parseEndAt(ActivityCreateRequest request) {
        LocalDate date = parseDate(request.getDate());
        LocalTime time = parseTime(request.getEndTime(), request.getTime(), false);
        return date == null || time == null ? null : LocalDateTime.of(date, time);
    }

    private LocalDateTime parseDeadline(ActivityCreateRequest request) {
        if (request.getDeadline() == null || request.getDeadline().trim().isEmpty()) return null;
        String value = request.getDeadline().trim();
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String text = value.trim();
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})月(\\d{1,2})日").matcher(text);
            if (matcher.find()) {
                return LocalDate.of(LocalDate.now().getYear(), Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            }
            return null;
        }
    }

    private LocalTime parseTime(String explicit, String label, boolean start) {
        String value = explicit;
        if ((value == null || value.trim().isEmpty()) && label != null) {
            String[] parts = label.split("-");
            value = parts.length > (start ? 0 : 1) ? parts[start ? 0 : 1].trim() : null;
        }
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private void createActivityReview(String id, ActivityCreateRequest request, String organizerId, IntegrationService.ModerationResult moderation) {
        String risk = moderation == null ? (request.getCapacity() > 50 ? "中" : "高") : moderation.risk;
        String reason = moderation == null ? (request.getCapacity() > 50 ? "报名人数超过 50 人，转入人工审核" : "AI 内容安全审核标记风险词") : moderation.reason;
        jdbc.update("insert into review_tasks (id,type,target_id,title,submitter,risk,reason,status) values (?,?,?,?,?,?,?,?)",
                DbSupport.id("rv"), "活动审核", id, request.getTitle(), userService.findById(organizerId).getNickname(), risk, reason, "待审核");
    }

    private String normalizedTime(ActivityCreateRequest request) {
        if (request.getTime() != null && !request.getTime().trim().isEmpty()) return request.getTime();
        String start = DbSupport.safe(request.getStartTime(), "");
        String end = DbSupport.safe(request.getEndTime(), "");
        return start + (end.isEmpty() ? "" : " - " + end);
    }

    private String defaultCover(String category) {
        if ("户外运动".equals(category)) return "https://images.unsplash.com/photo-1551632811-561732d1e306?auto=format&fit=crop&w=1200&q=85";
        if ("桌游聚会".equals(category)) return "https://images.unsplash.com/photo-1610890716171-6b1bb98ffd09?auto=format&fit=crop&w=1200&q=85";
        if ("运动健身".equals(category)) return "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?auto=format&fit=crop&w=1200&q=85";
        if ("学习交流".equals(category)) return "https://images.unsplash.com/photo-1526243741027-444d633d7365?auto=format&fit=crop&w=1200&q=85";
        if ("公益活动".equals(category)) return "https://images.unsplash.com/photo-1542601906990-b4d3fb778b09?auto=format&fit=crop&w=1200&q=85";
        return "https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&w=1400&q=85";
    }

    private int nextQueuePosition(String activityId) {
        Integer max = jdbc.queryForObject("select coalesce(max(queue_position), 0) from registrations where activity_id = ? and status = '候补中'", Integer.class, activityId);
        return (max == null ? 0 : max) + 1;
    }

    private int queuePosition(String activityId, String userId) {
        Integer position = jdbc.queryForObject("select queue_position from registrations where activity_id = ? and user_id = ?", Integer.class, activityId, userId);
        return position == null ? 0 : position;
    }

    private void renumberQueue(String activityId) {
        List<String> waiting = jdbc.queryForList("select user_id from registrations where activity_id = ? and status = '候补中' order by queue_position asc, created_at asc", String.class, activityId);
        int position = 1;
        for (String userId : waiting) {
            jdbc.update("update registrations set queue_position = ? where activity_id = ? and user_id = ?", position++, activityId, userId);
        }
    }

    private void log(String actorId, String action, String targetType, String targetId, String reason) {
        jdbc.update("insert into audit_logs (id,actor_id,action,target_type,target_id,reason) values (?,?,?,?,?,?)",
                DbSupport.id("log"), actorId, action, targetType, targetId, reason);
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
