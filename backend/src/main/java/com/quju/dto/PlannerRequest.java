package com.quju.dto;

import javax.validation.constraints.NotBlank;

public class PlannerRequest {
    @NotBlank(message = "活动主题不能为空")
    private String theme;
    private String people;
    private String style;

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getPeople() { return people; }
    public void setPeople(String people) { this.people = people; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
}
