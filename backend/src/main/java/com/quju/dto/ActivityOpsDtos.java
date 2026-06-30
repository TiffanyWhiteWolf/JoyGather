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

    public static class RegistrationManagementDto {
        private String id;
        private String userId;
        private String nickname;
        private String avatar;
        private String status;
        private int queuePosition;
        private String createdAt;
        private String checkedInAt;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getQueuePosition() { return queuePosition; }
        public void setQueuePosition(int queuePosition) { this.queuePosition = queuePosition; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getCheckedInAt() { return checkedInAt; }
        public void setCheckedInAt(String checkedInAt) { this.checkedInAt = checkedInAt; }
    }

    public static class CheckinScanRequest {
        private String code;
        private Double latitude;
        private Double longitude;
        private Boolean locationRequired;
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public Boolean getLocationRequired() { return locationRequired; }
        public void setLocationRequired(Boolean locationRequired) { this.locationRequired = locationRequired; }
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
        private String authorId;
        private String authorName;
        private String authorAvatar;
        private String title;
        private String content;
        private List<String> imageUrls;
        private List<String> categories;
        private List<SummaryImageDto> images;
        private String createdAt;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getActivityId() { return activityId; }
        public void setActivityId(String activityId) { this.activityId = activityId; }
        public String getAuthorId() { return authorId; }
        public void setAuthorId(String authorId) { this.authorId = authorId; }
        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorAvatar() { return authorAvatar; }
        public void setAuthorAvatar(String authorAvatar) { this.authorAvatar = authorAvatar; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public List<String> getImageUrls() { return imageUrls; }
        public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
        public List<String> getCategories() { return categories; }
        public void setCategories(List<String> categories) { this.categories = categories; }
        public List<SummaryImageDto> getImages() { return images; }
        public void setImages(List<SummaryImageDto> images) { this.images = images; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    public static class SummaryImageDto {
        private String url;
        private String aiCategory;
        private String confirmedCategory;
        public SummaryImageDto() {}
        public SummaryImageDto(String url, String aiCategory, String confirmedCategory) {
            this.url = url;
            this.aiCategory = aiCategory;
            this.confirmedCategory = confirmedCategory;
        }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getAiCategory() { return aiCategory; }
        public void setAiCategory(String aiCategory) { this.aiCategory = aiCategory; }
        public String getConfirmedCategory() { return confirmedCategory; }
        public void setConfirmedCategory(String confirmedCategory) { this.confirmedCategory = confirmedCategory; }
    }

    public static class SummaryClassifyRequest {
        private List<String> imageUrls;
        public List<String> getImageUrls() { return imageUrls; }
        public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    }

    public static class SummaryClassificationDto {
        private List<String> categories;
        private boolean aiAvailable;
        private String notice;
        public SummaryClassificationDto(List<String> categories, boolean aiAvailable, String notice) {
            this.categories = categories;
            this.aiAvailable = aiAvailable;
            this.notice = notice;
        }
        public List<String> getCategories() { return categories; }
        public boolean isAiAvailable() { return aiAvailable; }
        public String getNotice() { return notice; }
    }

    public static class ReviewRequest {
        private int rating;
        private String content;
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class ReviewDto {
        private String id;
        private String userId;
        private String nickname;
        private String avatar;
        private int rating;
        private String content;
        private String createdAt;
        private boolean mine;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public int getRating() { return rating; }
        public void setRating(int rating) { this.rating = rating; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public boolean isMine() { return mine; }
        public void setMine(boolean mine) { this.mine = mine; }
    }

    public static class AfterEventDto {
        private SummaryDto summary;
        private List<ReviewDto> reviews;
        private double averageRating;
        private int reviewCount;
        private boolean canPublishSummary;
        private boolean canReview;
        private boolean reviewExpired;
        private String reviewDeadline;
        private String eligibilityMessage;
        private ReviewDto myReview;
        public SummaryDto getSummary() { return summary; }
        public void setSummary(SummaryDto summary) { this.summary = summary; }
        public List<ReviewDto> getReviews() { return reviews; }
        public void setReviews(List<ReviewDto> reviews) { this.reviews = reviews; }
        public double getAverageRating() { return averageRating; }
        public void setAverageRating(double averageRating) { this.averageRating = averageRating; }
        public int getReviewCount() { return reviewCount; }
        public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
        public boolean isCanPublishSummary() { return canPublishSummary; }
        public void setCanPublishSummary(boolean canPublishSummary) { this.canPublishSummary = canPublishSummary; }
        public boolean isCanReview() { return canReview; }
        public void setCanReview(boolean canReview) { this.canReview = canReview; }
        public boolean isReviewExpired() { return reviewExpired; }
        public void setReviewExpired(boolean reviewExpired) { this.reviewExpired = reviewExpired; }
        public String getReviewDeadline() { return reviewDeadline; }
        public void setReviewDeadline(String reviewDeadline) { this.reviewDeadline = reviewDeadline; }
        public String getEligibilityMessage() { return eligibilityMessage; }
        public void setEligibilityMessage(String eligibilityMessage) { this.eligibilityMessage = eligibilityMessage; }
        public ReviewDto getMyReview() { return myReview; }
        public void setMyReview(ReviewDto myReview) { this.myReview = myReview; }
    }
}
