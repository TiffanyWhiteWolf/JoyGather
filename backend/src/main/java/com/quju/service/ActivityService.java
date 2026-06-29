package com.quju.service;

import com.quju.dto.ActivityDto;
import com.quju.dto.ActivityCreateRequest;
import com.quju.dto.RegistrationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ActivityService {
    private final List<ActivityDto> activities = new ArrayList<ActivityDto>();
    private final Map<String, Set<String>> registrations = new LinkedHashMap<String, Set<String>>();
    private final Map<String, Deque<String>> waitlists = new LinkedHashMap<String, Deque<String>>();
    private final AtomicInteger sequence = new AtomicInteger(100);

    public ActivityService() {
        activities.add(new ActivityDto("act-001", "落日以后，沿运河散步", "从桥西到小河直街，收集夏夜的橘色时刻", "城市探索", "06月30日", "18:30 - 21:00", "桥西历史文化街区", "拱墅区", new BigDecimal("2.4"), BigDecimal.ZERO, 18, 13, Arrays.asList("Citywalk", "日落", "摄影友好"), "报名中"));
        activities.add(new ActivityDto("act-002", "九溪轻徒步｜去山里喝杯茶", "低强度，新手友好，在茶山里认识新朋友", "户外运动", "07月02日", "09:00 - 14:30", "九溪公交站", "西湖区", new BigDecimal("8.6"), new BigDecimal("39"), 24, 21, Arrays.asList("轻徒步", "新手友好", "自然"), "报名中"));
        activities.add(new ActivityDto("act-003", "不熟也能玩的桌游夜", "拒绝硬核规则，专注快乐和有趣的人", "桌游聚会", "07月04日", "19:00 - 22:30", "湖滨银泰 IN77", "上城区", new BigDecimal("4.1"), new BigDecimal("49"), 12, 12, Arrays.asList("桌游", "破冰", "室内"), "即将开始"));
        registrations.put("act-001", new LinkedHashSet<String>(Arrays.asList("u-001")));
        registrations.put("act-002", new LinkedHashSet<String>());
        registrations.put("act-003", new LinkedHashSet<String>());
    }

    public List<ActivityDto> findAll(String keyword, String category) {
        final String normalized = keyword == null ? "" : keyword.toLowerCase(Locale.CHINA);
        return activities.stream()
                .filter(item -> normalized.isEmpty() || (item.getTitle() + item.getSummary()).toLowerCase(Locale.CHINA).contains(normalized))
                .filter(item -> category == null || category.isEmpty() || item.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    public Optional<ActivityDto> findById(String id) {
        return activities.stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    public synchronized ActivityDto create(ActivityCreateRequest request) {
        String status = request.getCapacity() > 50 ? "审核中" : "报名中";
        ActivityDto activity = new ActivityDto(
                "act-" + sequence.incrementAndGet(), request.getTitle(), request.getSummary(), request.getCategory(),
                request.getDate(), request.getTime(), request.getLocation(),
                request.getDistrict() == null ? "" : request.getDistrict(), BigDecimal.ZERO,
                request.getPrice() == null ? BigDecimal.ZERO : request.getPrice(), request.getCapacity(), 0,
                request.getTags() == null ? Collections.<String>emptyList() : request.getTags(), status);
        activities.add(activity);
        registrations.put(activity.getId(), new LinkedHashSet<String>());
        return activity;
    }

    public synchronized RegistrationResult register(String activityId, String userId) {
        ActivityDto activity = requireActivity(activityId);
        if (!"报名中".equals(activity.getStatus()) && !"即将开始".equals(activity.getStatus())) {
            throw new IllegalStateException("当前活动不可报名");
        }
        Set<String> joinedUsers = registrations.computeIfAbsent(activityId, key -> new LinkedHashSet<String>());
        Deque<String> queue = waitlists.computeIfAbsent(activityId, key -> new ArrayDeque<String>());
        if (joinedUsers.contains(userId)) return new RegistrationResult(activityId, userId, "已报名", 0, null);
        if (queue.contains(userId)) return new RegistrationResult(activityId, userId, "候补中", queuePosition(queue, userId), null);
        if (activity.getJoined() >= activity.getCapacity()) {
            queue.addLast(userId);
            return new RegistrationResult(activityId, userId, "候补中", queue.size(), null);
        }
        joinedUsers.add(userId);
        activity.setJoined(activity.getJoined() + 1);
        return new RegistrationResult(activityId, userId, "已报名", 0, null);
    }

    public synchronized RegistrationResult cancel(String activityId, String userId) {
        ActivityDto activity = requireActivity(activityId);
        Set<String> joinedUsers = registrations.computeIfAbsent(activityId, key -> new LinkedHashSet<String>());
        Deque<String> queue = waitlists.computeIfAbsent(activityId, key -> new ArrayDeque<String>());
        if (queue.remove(userId)) return new RegistrationResult(activityId, userId, "已取消", 0, null);
        if (!joinedUsers.remove(userId)) throw new IllegalStateException("没有可取消的报名记录");
        activity.setJoined(Math.max(0, activity.getJoined() - 1));
        String promoted = queue.pollFirst();
        if (promoted != null) {
            joinedUsers.add(promoted);
            activity.setJoined(activity.getJoined() + 1);
        }
        return new RegistrationResult(activityId, userId, "已取消", 0, promoted);
    }

    private ActivityDto requireActivity(String id) {
        return findById(id).orElseThrow(() -> new java.util.NoSuchElementException("活动不存在"));
    }

    private int queuePosition(Deque<String> queue, String userId) {
        int position = 1;
        for (String queuedUser : queue) {
            if (queuedUser.equals(userId)) return position;
            position++;
        }
        return 0;
    }
}
