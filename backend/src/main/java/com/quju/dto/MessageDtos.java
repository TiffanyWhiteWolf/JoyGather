package com.quju.dto;

import java.util.List;

public class MessageDtos {
    public static class ConversationDto {
        private String id;
        private String name;
        private String avatar;
        private String type;
        private int unread;
        private String lastMessage;
        private String lastTime;
        private Boolean online;
        private List<MessageDto> messages;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public int getUnread() { return unread; }
        public void setUnread(int unread) { this.unread = unread; }
        public String getLastMessage() { return lastMessage; }
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
        public String getLastTime() { return lastTime; }
        public void setLastTime(String lastTime) { this.lastTime = lastTime; }
        public Boolean getOnline() { return online; }
        public void setOnline(Boolean online) { this.online = online; }
        public List<MessageDto> getMessages() { return messages; }
        public void setMessages(List<MessageDto> messages) { this.messages = messages; }
    }

    public static class MessageDto {
        private String id;
        private String senderId;
        private String content;
        private String type;
        private String mediaUrl;
        private Double latitude;
        private Double longitude;
        private String time;
        private Boolean mine;
        private Boolean read;
        private Boolean recalled;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMediaUrl() { return mediaUrl; }
        public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public Boolean getMine() { return mine; }
        public void setMine(Boolean mine) { this.mine = mine; }
        public Boolean getRead() { return read; }
        public void setRead(Boolean read) { this.read = read; }
        public Boolean getRecalled() { return recalled; }
        public void setRecalled(Boolean recalled) { this.recalled = recalled; }
    }

    public static class SendMessageRequest {
        private String content;
        private String senderId;
        private String type;
        private String mediaUrl;
        private Double latitude;
        private Double longitude;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMediaUrl() { return mediaUrl; }
        public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
    }

    public static class ForwardRequest {
        private String conversationId;
        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    }
}
