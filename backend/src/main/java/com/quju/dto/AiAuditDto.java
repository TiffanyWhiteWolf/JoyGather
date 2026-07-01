package com.quju.dto;

import java.util.List;

public class AiAuditDto {
    private String id;
    private String result;
    private String riskLevel;
    private List<String> riskLabels;
    private String reason;
    private Double confidence;
    private String provider;
    private String model;
    private String providerStatus;
    private String errorMessage;
    private int durationMs;
    private String createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public List<String> getRiskLabels() { return riskLabels; }
    public void setRiskLabels(List<String> riskLabels) { this.riskLabels = riskLabels; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
