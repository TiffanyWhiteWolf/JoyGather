package com.quju.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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

    static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
