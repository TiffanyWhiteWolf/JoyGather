package com.quju.dto;

public class RegistrationResult {
    private final String activityId;
    private final String userId;
    private final String status;
    private final int queuePosition;
    private final String promotedUserId;
    private final String activityTitle;

    public RegistrationResult(String activityId, String userId, String status, int queuePosition, String promotedUserId) {
        this(activityId, userId, status, queuePosition, promotedUserId, null);
    }

    public RegistrationResult(String activityId, String userId, String status, int queuePosition, String promotedUserId, String activityTitle) {
        this.activityId = activityId;
        this.userId = userId;
        this.status = status;
        this.queuePosition = queuePosition;
        this.promotedUserId = promotedUserId;
        this.activityTitle = activityTitle;
    }
    public String getActivityId() { return activityId; }
    public String getUserId() { return userId; }
    public String getStatus() { return status; }
    public int getQueuePosition() { return queuePosition; }
    public String getPromotedUserId() { return promotedUserId; }
    public String getActivityTitle() { return activityTitle; }
}
