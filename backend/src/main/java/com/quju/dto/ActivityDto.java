package com.quju.dto;

import java.math.BigDecimal;
import java.util.List;

public class ActivityDto {
    private String id;
    private String title;
    private String summary;
    private String category;
    private String date;
    private String time;
    private String location;
    private String district;
    private BigDecimal distance;
    private BigDecimal price;
    private int capacity;
    private int joined;
    private List<String> tags;
    private String status;

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
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getCategory() { return category; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getLocation() { return location; }
    public String getDistrict() { return district; }
    public BigDecimal getDistance() { return distance; }
    public BigDecimal getPrice() { return price; }
    public int getCapacity() { return capacity; }
    public int getJoined() { return joined; }
    public void setJoined(int joined) { this.joined = joined; }
    public List<String> getTags() { return tags; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
