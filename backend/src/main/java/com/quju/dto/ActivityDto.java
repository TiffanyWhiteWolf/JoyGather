package com.quju.dto;

import java.math.BigDecimal;
import java.util.List;

public class ActivityDto {
    private String id;
    private String title;
    private String summary;
    private String description;
    private String category;
    private String cover;
    private String date;
    private String time;
    private String startAt;
    private String endAt;
    private String deadline;
    private String location;
    private String district;
    private BigDecimal distance;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal price;
    private int capacity;
    private int joined;
    private List<String> tags;
    private String status;
    private UserDto organizer;
    private Boolean featured;
    private String safetyNote;
    private int minAge;
    private List<String> joinFields;
    private String offlineReason;

    public ActivityDto() { }

    public ActivityDto(String id, String title, String summary, String category, String date,
                       String time, String location, String district, BigDecimal distance,
                       BigDecimal price, int capacity, int joined, List<String> tags, String status) {
        this.id = id; this.title = title; this.summary = summary; this.category = category;
        this.date = date; this.time = time; this.location = location; this.district = district;
        this.distance = distance; this.price = price; this.capacity = capacity; this.joined = joined;
        this.tags = tags; this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getStartAt() { return startAt; }
    public void setStartAt(String startAt) { this.startAt = startAt; }
    public String getEndAt() { return endAt; }
    public void setEndAt(String endAt) { this.endAt = endAt; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public BigDecimal getDistance() { return distance; }
    public void setDistance(BigDecimal distance) { this.distance = distance; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getJoined() { return joined; }
    public void setJoined(int joined) { this.joined = joined; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UserDto getOrganizer() { return organizer; }
    public void setOrganizer(UserDto organizer) { this.organizer = organizer; }
    public Boolean getFeatured() { return featured; }
    public void setFeatured(Boolean featured) { this.featured = featured; }
    public String getSafetyNote() { return safetyNote; }
    public void setSafetyNote(String safetyNote) { this.safetyNote = safetyNote; }
    public int getMinAge() { return minAge; }
    public void setMinAge(int minAge) { this.minAge = minAge; }
    public List<String> getJoinFields() { return joinFields; }
    public void setJoinFields(List<String> joinFields) { this.joinFields = joinFields; }
    public String getOfflineReason() { return offlineReason; }
    public void setOfflineReason(String offlineReason) { this.offlineReason = offlineReason; }
}
