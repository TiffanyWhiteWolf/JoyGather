package com.quju.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public class ActivityCreateRequest {
    @NotBlank(message = "活动名称不能为空") private String title;
    @NotBlank(message = "活动简介不能为空") private String summary;
    @NotBlank(message = "活动类型不能为空") private String category;
    @NotBlank(message = "活动日期不能为空") private String date;
    @NotBlank(message = "活动时间不能为空") private String time;
    @NotBlank(message = "活动地点不能为空") private String location;
    private String district;
    private BigDecimal price = BigDecimal.ZERO;
    @Min(value = 2, message = "活动人数上限至少为2人") private int capacity;
    private List<String> tags;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
