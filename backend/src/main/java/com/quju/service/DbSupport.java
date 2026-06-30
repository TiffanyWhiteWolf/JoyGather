package com.quju.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class DbSupport {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DbSupport() { }

    static String id(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + Math.abs(java.util.UUID.randomUUID().toString().hashCode());
    }

    static List<String> split(String value) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        String normalized = value.replace('、', ',');
        List<String> result = new ArrayList<String>();
        for (String item : Arrays.asList(normalized.split(","))) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(",");
            builder.append(value.trim());
        }
        return builder.toString();
    }

    static String formatTime(Timestamp timestamp) {
        if (timestamp == null) return "";
        LocalDateTime value = timestamp.toLocalDateTime();
        return DATE_TIME.format(value);
    }

    static String relativeTime(Timestamp timestamp) {
        if (timestamp == null) return "";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime then = timestamp.toLocalDateTime();
        Duration d = Duration.between(then, now);
        long seconds = d.getSeconds();
        if (seconds < 60) return "刚刚";
        if (seconds < 3600) return (seconds / 60) + "分钟前";
        if (seconds < 86400) {
            LocalDate today = now.toLocalDate();
            LocalDate thenDate = then.toLocalDate();
            if (thenDate.equals(today)) return "今天 " + then.toLocalTime().format(LocalTimeFormatter);
            if (thenDate.equals(today.minusDays(1))) return "昨天 " + then.toLocalTime().format(LocalTimeFormatter);
        }
        return then.toLocalDate().format(DateFormatter) + " " + then.toLocalTime().format(LocalTimeFormatter);
    }

    private static final DateTimeFormatter LocalTimeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DateFormatter = DateTimeFormatter.ofPattern("MM-dd");

    static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
