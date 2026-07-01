package com.quju.dto;

import java.util.List;

public class TeamOpsDtos {
    public static class AnnouncementRequest {
        private String content;
        private boolean mentionAll;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public boolean isMentionAll() { return mentionAll; }
        public void setMentionAll(boolean mentionAll) { this.mentionAll = mentionAll; }
    }

    public static class PollRequest {
        private String title;
        private List<String> options;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public List<String> getOptions() { return options; }
        public void setOptions(List<String> options) { this.options = options; }
    }

    public static class TeamContentRequest {
        private String fileId;
        private String url;
        private String caption;
        private String activityId;
        private String reason;
        public String getFileId() { return fileId; }
        public void setFileId(String fileId) { this.fileId = fileId; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getCaption() { return caption; }
        public void setCaption(String caption) { this.caption = caption; }
        public String getActivityId() { return activityId; }
        public void setActivityId(String activityId) { this.activityId = activityId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class DissolveRequest {
        private boolean confirmed;
        private String confirmationText;
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
        public String getConfirmationText() { return confirmationText; }
        public void setConfirmationText(String confirmationText) { this.confirmationText = confirmationText; }
    }

    public static class UpdateTeamRequest {
        private String name;
        private String description;
        private String cover;
        private List<String> tags;
        private Integer capacity;
        private String joinMode;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCover() { return cover; }
        public void setCover(String cover) { this.cover = cover; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public Integer getCapacity() { return capacity; }
        public void setCapacity(Integer capacity) { this.capacity = capacity; }
        public String getJoinMode() { return joinMode; }
        public void setJoinMode(String joinMode) { this.joinMode = joinMode; }
    }

    public static class RoleChangeRequest {
        private String role;
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
