package com.quju.dto;

import java.util.List;
import java.util.Map;

public class ActivityOpsDtos {
    public static class RegistrationRequest {
        private Map<String, String> fields;
        public Map<String, String> getFields() { return fields; }
        public void setFields(Map<String, String> fields) { this.fields = fields; }
    }

    public static class CheckinCodeResponse {
        private String code;
        private String url;
        private String expiresAt;
        public CheckinCodeResponse(String code, String url, String expiresAt) {
            this.code = code;
            this.url = url;
            this.expiresAt = expiresAt;
        }
        public String getCode() { return code; }
        public String getUrl() { return url; }
        public String getExpiresAt() { return expiresAt; }
    }

    public static class CheckinScanRequest {
        private String code;
        private Double latitude;
        private Double longitude;
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
    }

    public static class SummaryRequest {
        private String title;
        private String content;
        private List<String> imageUrls;
        private List<String> confirmedCategories;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getImageUrls() { return imageUrls; }
        public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
        public List<String> getConfirmedCategories() { return confirmedCategories; }
        public void setConfirmedCategories(List<String> confirmedCategories) { this.confirmedCategories = confirmedCategories; }
    }

    public static class SummaryDto {
        private String id;
        private String activityId;
        private String title;
        private String content;
        private List<String> imageUrls;
        private List<String> categories;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getActivityId() { return activityId; }
        public void setActivityId(String activityId) { this.activityId = activityId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getImageUrls() { return imageUrls; }
        public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
        public List<String> getCategories() { return categories; }
        public void setCategories(List<String> categories) { this.categories = categories; }
    }

    public static class ReviewRequest {
        private int rating;
        private String content;
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
