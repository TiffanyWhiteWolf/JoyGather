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
}
