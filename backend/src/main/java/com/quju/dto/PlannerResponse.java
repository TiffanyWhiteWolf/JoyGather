package com.quju.dto;

import java.util.List;

public class PlannerResponse {
    private final String title;
    private final String introduction;
    private final List<String> tags;
    private final List<String> schedule;
    private final String safetyNote;
    private final boolean aiGenerated;
    private final String notice;

    public PlannerResponse(String title, String introduction, List<String> tags, List<String> schedule, String safetyNote) {
        this(title, introduction, tags, schedule, safetyNote, false, "");
    }

    public PlannerResponse(String title, String introduction, List<String> tags, List<String> schedule, String safetyNote,
                           boolean aiGenerated, String notice) {
        this.title = title;
        this.introduction = introduction;
        this.tags = tags;
        this.schedule = schedule;
        this.safetyNote = safetyNote;
        this.aiGenerated = aiGenerated;
        this.notice = notice;
    }
    public String getTitle() { return title; }
    public String getIntroduction() { return introduction; }
    public List<String> getTags() { return tags; }
    public List<String> getSchedule() { return schedule; }
    public String getSafetyNote() { return safetyNote; }
    public boolean isAiGenerated() { return aiGenerated; }
    public String getNotice() { return notice; }
}
