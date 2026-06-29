package com.quju.dto;

import java.util.List;

public class UserDto {
    private String id;
    private String email;
    private String nickname;
    private String avatar;
    private String role;
    private String city;
    private String gender;
    private String birthday;
    private String bio;
    private List<String> interests;
    private int following;
    private int followers;
    private int credit;
    private Boolean verified;
    private String status;
    private String banReason;
    private String banUntil;
    private String merchantName;
    private String merchantNickname;
    private List<String> merchantFields;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public List<String> getInterests() { return interests; }
    public void setInterests(List<String> interests) { this.interests = interests; }
    public int getFollowing() { return following; }
    public void setFollowing(int following) { this.following = following; }
    public int getFollowers() { return followers; }
    public void setFollowers(int followers) { this.followers = followers; }
    public int getCredit() { return credit; }
    public void setCredit(int credit) { this.credit = credit; }
    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; }
    public String getBanUntil() { return banUntil; }
    public void setBanUntil(String banUntil) { this.banUntil = banUntil; }
    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
    public String getMerchantNickname() { return merchantNickname; }
    public void setMerchantNickname(String merchantNickname) { this.merchantNickname = merchantNickname; }
    public List<String> getMerchantFields() { return merchantFields; }
    public void setMerchantFields(List<String> merchantFields) { this.merchantFields = merchantFields; }
}
