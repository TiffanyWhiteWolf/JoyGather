package com.quju.dto;

public class AdminDtos {
    public static class ReasonRequest {
        private String reason;
        private String until;
        private String handlerId;
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getUntil() { return until; }
        public void setUntil(String until) { this.until = until; }
        public String getHandlerId() { return handlerId; }
        public void setHandlerId(String handlerId) { this.handlerId = handlerId; }
    }

    public static class NotificationRequest {
        private String title;
        private String content;
        private String type;
        private String targetType;
        private String targetId;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
    }
}
