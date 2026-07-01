package com.quju.dto;

import java.util.List;

public class ReviewDetailDto {
    private ReviewTaskDto task;
    private ActivityDto activity;
    private List<AiAuditDto> aiAudits;

    public ReviewDetailDto(ReviewTaskDto task, ActivityDto activity, List<AiAuditDto> aiAudits) {
        this.task = task;
        this.activity = activity;
        this.aiAudits = aiAudits;
    }

    public ReviewTaskDto getTask() { return task; }
    public void setTask(ReviewTaskDto task) { this.task = task; }
    public ActivityDto getActivity() { return activity; }
    public void setActivity(ActivityDto activity) { this.activity = activity; }
    public List<AiAuditDto> getAiAudits() { return aiAudits; }
    public void setAiAudits(List<AiAuditDto> aiAudits) { this.aiAudits = aiAudits; }
}
