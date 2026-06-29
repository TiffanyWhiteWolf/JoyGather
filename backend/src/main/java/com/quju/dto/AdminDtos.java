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
}
