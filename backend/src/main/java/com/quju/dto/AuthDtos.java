package com.quju.dto;

public class AuthDtos {
    public static class LoginRequest {
        private String email;
        private String password;
        private boolean adminLogin;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public boolean isAdminLogin() { return adminLogin; }
        public void setAdminLogin(boolean adminLogin) { this.adminLogin = adminLogin; }
    }

    public static class RegisterRequest {
        private String email;
        private String password;
        private String confirmPassword;
        private String nickname;
        private String role;
        private String merchantName;
        private String licenseName;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        public String getLicenseName() { return licenseName; }
        public void setLicenseName(String licenseName) { this.licenseName = licenseName; }
    }

    public static class AuthResponse {
        private String token;
        private UserDto user;
        public AuthResponse(String token, UserDto user) {
            this.token = token;
            this.user = user;
        }
        public String getToken() { return token; }
        public UserDto getUser() { return user; }
    }

    public static class ActivationResponse {
        private String userId;
        private String activationToken;
        private String status;
        public ActivationResponse(String userId, String activationToken, String status) {
            this.userId = userId;
            this.activationToken = activationToken;
            this.status = status;
        }
        public String getUserId() { return userId; }
        public String getActivationToken() { return activationToken; }
        public String getStatus() { return status; }
    }

    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }
}
