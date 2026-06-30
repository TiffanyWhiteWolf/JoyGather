package com.quju.dto;

import java.util.List;
import java.util.Map;

public class TeamDto {
    private String id;
    private String name;
    private String description;
    private String cover;
    private List<String> tags;
    private int members;
    private int capacity;
    private String joinMode;
    private int activeNow;
    private String status;
    private String stopReason;
    private String ownerId;
    private String ownerNickname;
    private List<Map<String, Object>> memberRecords;
    private List<Map<String, Object>> activityRecords;
    private List<Map<String, Object>> reportRecords;
    private String myRole;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public int getMembers() { return members; }
    public void setMembers(int members) { this.members = members; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getJoinMode() { return joinMode; }
    public void setJoinMode(String joinMode) { this.joinMode = joinMode; }
    public int getActiveNow() { return activeNow; }
    public void setActiveNow(int activeNow) { this.activeNow = activeNow; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStopReason() { return stopReason; }
    public void setStopReason(String stopReason) { this.stopReason = stopReason; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getOwnerNickname() { return ownerNickname; }
    public void setOwnerNickname(String ownerNickname) { this.ownerNickname = ownerNickname; }
    public List<Map<String, Object>> getMemberRecords() { return memberRecords; }
    public void setMemberRecords(List<Map<String, Object>> memberRecords) { this.memberRecords = memberRecords; }
    public List<Map<String, Object>> getActivityRecords() { return activityRecords; }
    public void setActivityRecords(List<Map<String, Object>> activityRecords) { this.activityRecords = activityRecords; }
    public List<Map<String, Object>> getReportRecords() { return reportRecords; }
    public void setReportRecords(List<Map<String, Object>> reportRecords) { this.reportRecords = reportRecords; }
    public String getMyRole() { return myRole; }
    public void setMyRole(String myRole) { this.myRole = myRole; }
}
