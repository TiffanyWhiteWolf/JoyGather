package com.quju.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public class ActivityCreateRequest {
    @NotBlank(message = "活动名称不能为空") private String title;
    @NotBlank(message = "活动简介不能为空") private String summary;
    private String description;
    @NotBlank(message = "活动类型不能为空") private String category;
    @NotBlank(message = "活动日期不能为空") private String date;
    @NotBlank(message = "活动时间不能为空") private String time;
    private String startTime;
    private String endTime;
    private String deadline;
    @NotBlank(message = "活动地点不能为空") private String location;
    private String district;
    private String cover;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private BigDecimal price = BigDecimal.ZERO;
    @Min(value = 2, message = "活动人数上限必须为大于等于2的整数") private int capacity;
    private List<String> tags;
    private int minAge = 0;
    private String safetyNote;
    private List<String> joinFields;
    private String organizerId;
    private String teamId;
    private String visibility;
    private String submitToken;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public int getMinAge() { return minAge; }
    public void setMinAge(int minAge) { this.minAge = minAge; }
    public String getSafetyNote() { return safetyNote; }
    public void setSafetyNote(String safetyNote) { this.safetyNote = safetyNote; }
    public List<String> getJoinFields() { return joinFields; }
    public void setJoinFields(List<String> joinFields) { this.joinFields = joinFields; }
    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getSubmitToken() { return submitToken; }
    public void setSubmitToken(String submitToken) { this.submitToken = submitToken; }
}
