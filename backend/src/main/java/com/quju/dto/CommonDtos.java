package com.quju.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class CommonDtos {
    public static class BooleanResponse {
        private boolean available;
        public BooleanResponse(boolean available) { this.available = available; }
        public boolean isAvailable() { return available; }
    }

    public static class FileResponse {
        private String id;
        private String url;
        private String originalName;
        private String contentType;
        private long size;
        private String provider;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getOriginalName() { return originalName; }
        public void setOriginalName(String originalName) { this.originalName = originalName; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
    }

    public static class ProfileRequest {
        private String nickname;
        private String avatar;
        private String gender;
        private String birthday;
        private String city;
        private String bio;
        private List<String> interests;
        private String merchantName;
        private String merchantNickname;
        private List<String> merchantFields;
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        public String getBirthday() { return birthday; }
        public void setBirthday(String birthday) { this.birthday = birthday; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
        public List<String> getInterests() { return interests; }
        public void setInterests(List<String> interests) { this.interests = interests; }
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        public String getMerchantNickname() { return merchantNickname; }
        public void setMerchantNickname(String merchantNickname) { this.merchantNickname = merchantNickname; }
        public List<String> getMerchantFields() { return merchantFields; }
        public void setMerchantFields(List<String> merchantFields) { this.merchantFields = merchantFields; }
    }

    public static class MerchantApplicationRequest {
        private String merchantName;
        private String merchantNickname;
        private List<String> merchantFields;
        private String licenseName;
        private String licenseUrl;
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        public String getMerchantNickname() { return merchantNickname; }
        public void setMerchantNickname(String merchantNickname) { this.merchantNickname = merchantNickname; }
        public List<String> getMerchantFields() { return merchantFields; }
        public void setMerchantFields(List<String> merchantFields) { this.merchantFields = merchantFields; }
        public String getLicenseName() { return licenseName; }
        public void setLicenseName(String licenseName) { this.licenseName = licenseName; }
        public String getLicenseUrl() { return licenseUrl; }
        public void setLicenseUrl(String licenseUrl) { this.licenseUrl = licenseUrl; }
    }

    public static class AccountCancellationRequest {
        private String password;
        private String confirmText;
        private String reason;
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmText() { return confirmText; }
        public void setConfirmText(String confirmText) { this.confirmText = confirmText; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class GeoPoint {
        private String name;
        private String address;
        private String city;
        private String district;
        private BigDecimal longitude;
        private BigDecimal latitude;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getDistrict() { return district; }
        public void setDistrict(String district) { this.district = district; }
        public BigDecimal getLongitude() { return longitude; }
        public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
        public BigDecimal getLatitude() { return latitude; }
        public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    }

    public static class GenericRequest {
        private String reason;
        private String content;
        private String targetId;
        private String targetType;
        private Map<String, Object> payload;
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public Map<String, Object> getPayload() { return payload; }
        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }
}
