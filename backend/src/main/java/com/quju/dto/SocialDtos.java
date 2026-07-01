package com.quju.dto;

public class SocialDtos {
    public static class FriendRequestInput {
        private String userId;
        private String source;
        private String message;
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class FriendMetaRequest {
        private String remark;
        private String groupName;
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getGroupName() { return groupName; }
        public void setGroupName(String groupName) { this.groupName = groupName; }
    }

    public static class NotificationCreateRequest {
        private String userId;
        private String type;
        private String title;
        private String content;
        private String targetType;
        private String targetId;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
    }
}
