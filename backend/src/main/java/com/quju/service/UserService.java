package com.quju.service;

import com.quju.dto.AuthDtos;
import com.quju.dto.CommonDtos;
import com.quju.dto.UserDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserService {
    public static final String DEFAULT_USER_ID = "u-001";
    private static final String[] DEFAULT_AVATARS = {
        "01cf64c9de601412382036959cd5591e.jpg",
        "1082de0707ac1d71aaf1c7b36271f57b.jpg",
        "31b02ba3b35dc65308917a6d078bfb97.jpg",
        "4179dda7978f0beb9b02df68496f2126.jpg",
        "5935dfde6b6a77577e6ae0185d136196.jpg",
        "5c172fdf2693bb3cf0cf8a1a0b292cc8.jpg",
        "748f59e0cb8936d7195e7d3ef5996da7.jpg",
        "90c19721335786af5827ca4297426aa7.jpg",
        "9e39d7545e043373b8e3e642280f66b6.jpg",
        "a921481cc4f0a753f4ef0927d0d3a8c8.jpg",
        "b11c7bc2b04b10d9e2c120cb155a9bc5.jpg",
        "c399e93a68cac72874937e11fdcfc19d.jpg",
        "cd02c119c6f8f021673bf8fae200bc76.jpg",
        "cf3cb6c05d4c4192daf0ef56f6e6fea0.jpg",
        "d50edf9e226271a06ea9ddd6ce197f26.jpg",
        "d9e3a6b2d0cd7173df8c314d811720d7.jpg",
        "df0721138c4a603409035a39b1f873b3.jpg",
    };

    private final JdbcTemplate jdbc;
    private final IntegrationService integrationService;
    private final SocialService socialService;

    public UserService(JdbcTemplate jdbc) {
        this(jdbc, null, null);
    }

    @Autowired
    public UserService(JdbcTemplate jdbc, IntegrationService integrationService, @Lazy SocialService socialService) {
        this.jdbc = jdbc;
        this.integrationService = integrationService;
        this.socialService = socialService;
    }

    static String randomDefaultAvatar(String userId) {
        int idx = Math.abs(userId.hashCode() % DEFAULT_AVATARS.length);
        return "/pictures/" + DEFAULT_AVATARS[idx];
    }

    public UserDto findById(String id) {
        try {
            return jdbc.queryForObject("select * from users where id = ?", userMapper(), id);
        } catch (EmptyResultDataAccessException ex) {
            throw new NoSuchElementException("用户不存在");
        }
    }

    public Map<String, Object> getPublicProfile(String userId, String currentUserId) {
        UserDto user = findById(userId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("role", user.getRole());
        result.put("city", user.getCity());
        result.put("gender", user.getGender());
        result.put("birthday", user.getBirthday());
        result.put("bio", user.getBio());
        result.put("interests", user.getInterests());
        result.put("following", user.getFollowing());
        result.put("followers", user.getFollowers());
        result.put("credit", user.getCredit());
        result.put("verified", user.getVerified());
        if ("商家用户".equals(user.getRole())) {
            result.put("merchantName", user.getMerchantName());
            result.put("merchantFields", user.getMerchantFields());
        }
        if (currentUserId != null && !currentUserId.equals(userId)) {
            java.util.List<Integer> fc = jdbc.queryForList(
                "select 1 from friendships where user_id = ? and friend_id = ?", Integer.class, currentUserId, userId);
            result.put("isFriend", !fc.isEmpty());
            java.util.List<Integer> fw = jdbc.queryForList(
                "select 1 from follows where follower_id = ? and followee_id = ?", Integer.class, currentUserId, userId);
            result.put("isFollowed", !fw.isEmpty());
            java.util.List<Integer> bk = jdbc.queryForList(
                "select 1 from user_blocks where user_id = ? and blocked_user_id = ?", Integer.class, currentUserId, userId);
            result.put("isBlocked", !bk.isEmpty());
        }
        return result;
    }

    public List<UserDto> search(String query, String role, String status) {
        String normalized = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
        String sql = "select * from users where (lower(nickname) like ? or lower(email) like ? or lower(id) like ?) "
                + "and (? = '' or role = ?) and (? = '' or status = ?) order by created_at desc";
        return jdbc.query(sql, userMapper(), normalized, normalized, normalized,
                emptyFilter(role), role, emptyFilter(status), status);
    }

    @Transactional
    public AuthDtos.ActivationResponse register(AuthDtos.RegisterRequest request) {
        validateRegistration(request);
        String id = DbSupport.id("u");
        String activationToken = UUID.randomUUID().toString().replace("-", "");
        String role = DbSupport.safe(request.getRole(), "个人用户");
        String merchantName = "商家用户".equals(role) ? request.getMerchantName() : null;
        jdbc.update("insert into users (id,email,password_hash,nickname,avatar,role,city,bio,interests,credit,verified,activated,activation_token,status,merchant_name) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id, request.getEmail().trim().toLowerCase(), hashPassword(request.getPassword()), request.getNickname().trim(),
                                randomDefaultAvatar(id), role, "杭州", "", "", 100, false, false, activationToken, "正常", merchantName);
        if ("商家用户".equals(role)) {
            String applicationId = DbSupport.id("merchant");
            jdbc.update("insert into merchant_applications (id,user_id,merchant_name,license_name,status) values (?,?,?,?,?)",
                    applicationId, id, DbSupport.safe(request.getMerchantName(), request.getNickname()), request.getLicenseName(), "待审核");
            jdbc.update("insert into review_tasks (id,type,target_id,title,submitter,risk,reason,status) values (?,?,?,?,?,?,?,?)",
                    DbSupport.id("rv"), "商家认证", applicationId, DbSupport.safe(request.getMerchantName(), request.getNickname()),
                    request.getNickname(), "低", "营业执照与门店认证", "待审核");
        }
        if (integrationService != null) integrationService.sendActivationEmail(request.getEmail().trim().toLowerCase(), activationToken);
        return new AuthDtos.ActivationResponse(id, "未激活");
    }

    @Transactional
    public UserDto activate(String token) {
        String userId;
        try {
            userId = jdbc.queryForObject("select id from users where activation_token = ?", String.class, token);
        } catch (EmptyResultDataAccessException ex) {
            throw new NoSuchElementException("激活链接无效或已使用");
        }
        int updated = jdbc.update("update users set activated = 1, activation_token = null where id = ?", userId);
        if (updated == 0) throw new NoSuchElementException("激活链接无效或已使用");
        return findById(userId);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        UserRecord record;
        try {
            record = jdbc.queryForObject("select * from users where email = ?", userRecordMapper(), request.getEmail().trim().toLowerCase());
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalStateException("邮箱或密码错误");
        }
        if (!passwordMatches(request.getPassword(), record.passwordHash)) throw new IllegalStateException("邮箱或密码错误");
        if (request.isAdminLogin() && !"管理员".equals(record.user.getRole())) throw new IllegalStateException("非管理员账号，无法使用管理员后台登录");
        if (!request.isAdminLogin() && "管理员".equals(record.user.getRole())) throw new IllegalStateException("管理员账号无法在用户登录页面登录");
        if (!record.activated) throw new IllegalStateException("请先激活账号");
        if ("已注销".equals(record.status)) throw new IllegalStateException("账号已注销，无法登录");
        if ("已封禁".equals(record.status) && !banExpired(record.banUntil)) {
            String untilStr = record.banUntil == null ? "" : "，解封日期：" + record.banUntil.toString();
            throw new IllegalStateException("账号已被封禁：" + DbSupport.safe(record.banReason, "未填写原因") + untilStr);
        }
        if ("已封禁".equals(record.status) && banExpired(record.banUntil)) {
            jdbc.update("update users set status = '正常', ban_reason = null, ban_until = null where id = ?", record.user.getId());
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("insert into sessions (token,user_id,expires_at) values (?,?,?)",
                token, record.user.getId(), Timestamp.valueOf(LocalDateTime.now().plusDays(7)));
        return new AuthDtos.AuthResponse(token, record.user);
    }

    @Transactional
    public void logout(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return;
        String token = authorization.substring("Bearer ".length()).trim();
        if (!token.isEmpty()) jdbc.update("delete from sessions where token = ?", token);
    }

    public UserDto resolveToken(String authorization) {
        return requireToken(authorization);
    }

    public UserDto optionalToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring("Bearer ".length()).trim();
        try {
            return jdbc.queryForObject("select u.* from users u join sessions s on s.user_id = u.id where s.token = ? and s.expires_at > now()",
                    userMapper(), token);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public UserDto requireToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalStateException("请先登录");
        String token = authorization.substring("Bearer ".length()).trim();
        try {
            UserDto user = jdbc.queryForObject("select u.* from users u join sessions s on s.user_id = u.id where s.token = ? and s.expires_at > now()",
                    userMapper(), token);
            if ("已注销".equals(user.getStatus())) {
                jdbc.update("delete from sessions where token = ?", token);
                throw new IllegalStateException("账号已注销，请重新注册或联系平台");
            }
            if ("已封禁".equals(user.getStatus()) && !banExpired(user.getBanUntil())) {
                throw new IllegalStateException(banMessage(user.getBanReason(), user.getBanUntil()));
            }
            return user;
        } catch (EmptyResultDataAccessException ex) {
            throw new IllegalStateException("登录已过期，请重新登录");
        }
    }

    public UserDto requireAdmin(String authorization) {
        UserDto user = requireToken(authorization);
        if (!"管理员".equals(user.getRole())) throw new IllegalStateException("需要管理员权限");
        return user;
    }

    @Transactional
    public UserDto updateProfile(String authorization, CommonDtos.ProfileRequest request) {
        UserDto user = requireToken(authorization);
        if (request.getNickname() == null || request.getNickname().trim().isEmpty()) throw new IllegalStateException("昵称不能为空");
        if (!nicknameAvailable(request.getNickname(), user.getId())) throw new IllegalStateException("昵称已被占用");
        jdbc.update("update users set nickname = ?, avatar = ?, gender = ?, birthday = ?, city = ?, bio = ?, interests = ?, merchant_name = ?, merchant_nickname = ?, merchant_fields = ? where id = ?",
                request.getNickname().trim(),
                DbSupport.safe(request.getAvatar(), user.getAvatar()),
                DbSupport.safe(request.getGender(), user.getGender()),
                request.getBirthday() == null || request.getBirthday().trim().isEmpty() ? null : Date.valueOf(request.getBirthday()),
                DbSupport.safe(request.getCity(), user.getCity()),
                DbSupport.safe(request.getBio(), ""),
                DbSupport.join(request.getInterests()),
                DbSupport.safe(request.getMerchantName(), user.getMerchantName()),
                DbSupport.safe(request.getMerchantNickname(), user.getMerchantNickname()),
                DbSupport.join(request.getMerchantFields()),
                user.getId());
        return findById(user.getId());
    }

    public boolean nicknameAvailable(String nickname, String currentUserId) {
        if (nickname == null || nickname.trim().isEmpty()) return false;
        Integer count = jdbc.queryForObject("select count(*) from users where nickname = ? and (? is null or id <> ?)", Integer.class,
                nickname.trim(), currentUserId, currentUserId);
        return count == null || count == 0;
    }

    public List<Map<String, Object>> myMerchantApplications(String authorization) {
        UserDto user = requireToken(authorization);
        return jdbc.queryForList("select id, merchant_name merchantName, license_name licenseName, license_url licenseUrl, status, reason, submitted_at submittedAt, reviewed_at reviewedAt from merchant_applications where user_id = ? order by submitted_at desc", user.getId());
    }

    @Transactional
    public void submitMerchantApplication(String authorization, CommonDtos.MerchantApplicationRequest request) {
        UserDto user = requireToken(authorization);
        if (request.getMerchantName() == null || request.getMerchantName().trim().isEmpty()) throw new IllegalStateException("商家名称不能为空");
        if ((request.getLicenseName() == null || request.getLicenseName().trim().isEmpty())
                && (request.getLicenseUrl() == null || request.getLicenseUrl().trim().isEmpty())) throw new IllegalStateException("请上传营业执照或凭证");
        String applicationId = DbSupport.id("merchant");
        jdbc.update("insert into merchant_applications (id,user_id,merchant_name,license_name,license_url,status) values (?,?,?,?,?,?)",
                applicationId, user.getId(), request.getMerchantName().trim(), request.getLicenseName(), request.getLicenseUrl(), "待审核");
        jdbc.update("update users set merchant_name = ?, merchant_nickname = ?, merchant_fields = ? where id = ?",
                request.getMerchantName().trim(), DbSupport.safe(request.getMerchantNickname(), request.getMerchantName()), DbSupport.join(request.getMerchantFields()), user.getId());
        jdbc.update("insert into review_tasks (id,type,target_id,title,submitter,risk,reason,status) values (?,?,?,?,?,?,?,?)",
                DbSupport.id("rv"), "商家认证", applicationId, request.getMerchantName().trim(), user.getNickname(), "低", "商家资质审核", "待审核");
    }

    @Transactional
    public void changePassword(String authorization, AuthDtos.ChangePasswordRequest request) {
        UserDto user = requireToken(authorization);
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) throw new IllegalStateException("新密码至少需要 8 位");
        String stored = jdbc.queryForObject("select password_hash from users where id = ?", String.class, user.getId());
        if (!passwordMatches(request.getOldPassword(), stored)) throw new IllegalStateException("原密码错误");
        jdbc.update("update users set password_hash = ? where id = ?", hashPassword(request.getNewPassword()), user.getId());
        jdbc.update("delete from sessions where user_id = ?", user.getId());
    }

    @Transactional
    public void cancelAccount(String authorization, CommonDtos.AccountCancellationRequest request) {
        UserDto user = requireToken(authorization);
        if ("管理员".equals(user.getRole())) throw new IllegalStateException("管理员账号不支持自助注销");
        if ("已注销".equals(user.getStatus())) throw new IllegalStateException("账号已注销");
        if (request == null || request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalStateException("请输入当前密码");
        }
        if (!"注销账号".equals(DbSupport.safe(request.getConfirmText(), "").trim())) {
            throw new IllegalStateException("请输入确认文案：注销账号");
        }
        String stored = jdbc.queryForObject("select password_hash from users where id = ?", String.class, user.getId());
        if (!passwordMatches(request.getPassword(), stored)) throw new IllegalStateException("当前密码错误");

        String reason = DbSupport.safe(request.getReason(), "用户主动注销账号").trim();
        String suffix = user.getId().length() > 8 ? user.getId().substring(user.getId().length() - 8) : user.getId();
        String deletedEmail = "deleted-" + user.getId() + "@deleted.quju.local";
        String deletedNickname = "已注销用户-" + suffix;

        List<String> joinedActivityIds = jdbc.queryForList(
                "select activity_id from registrations where user_id = ? and status = '已报名'", String.class, user.getId());
        for (String activityId : joinedActivityIds) {
            jdbc.update("update activities set joined_count = case when joined_count > 0 then joined_count - 1 else 0 end where id = ?", activityId);
        }
        jdbc.update("update registrations set status = '已取消', updated_at = now() where user_id = ? and status in ('已报名','候补中')", user.getId());
        jdbc.update("update activities set status = '已下架', offline_reason = '发起人已注销账号' where organizer_id = ? and status not in ('已结束','已下架')", user.getId());
        jdbc.update("update teams set status = '已停用', stop_reason = '队长已注销账号' where owner_id = ? and status <> '已停用'", user.getId());
        jdbc.update("delete from team_members where user_id = ?", user.getId());
        jdbc.update("delete from follows where follower_id = ? or followee_id = ?", user.getId(), user.getId());
        jdbc.update("delete from friendships where user_id = ? or friend_id = ?", user.getId(), user.getId());
        jdbc.update("delete from friend_requests where requester_id = ? or receiver_id = ?", user.getId(), user.getId());
        jdbc.update("delete from user_blocks where user_id = ? or blocked_user_id = ?", user.getId(), user.getId());
        jdbc.update("update merchant_applications set status = '已取消', reason = '申请人已注销账号' where user_id = ? and status = '待审核'", user.getId());
        jdbc.update("delete from sessions where user_id = ?", user.getId());
        jdbc.update("update users set email = ?, password_hash = ?, nickname = ?, avatar = ?, city = '', gender = null, birthday = null, cover = null, bio = '', interests = '', following_count = 0, follower_count = 0, credit = 0, verified = 0, activated = 0, activation_token = null, status = '已注销', ban_reason = ?, ban_until = null, merchant_name = null, merchant_nickname = null, merchant_fields = null where id = ?",
                deletedEmail, "deleted:" + UUID.randomUUID().toString().replace("-", ""),
                deletedNickname, randomDefaultAvatar("deleted"), reason, user.getId());
        log(user.getId(), "CANCEL_ACCOUNT", "USER", user.getId(), reason);
    }

    @Transactional
    public void ban(String userId, String reason, String until, String actorId) {
        if ("已注销".equals(findById(userId).getStatus())) throw new IllegalStateException("已注销账号不能封禁");
        if (reason == null || reason.trim().isEmpty() || until == null || until.trim().isEmpty()) {
            throw new IllegalStateException("封禁原因和封禁期限均为必填项");
        }
        Date banUntil = Date.valueOf(until);
        if (!banUntil.toLocalDate().isAfter(LocalDate.now())) {
            throw new IllegalStateException("封禁截止日期必须在今天之后");
        }
        jdbc.update("update users set status = '已封禁', ban_reason = ?, ban_until = ? where id = ?",
                reason.trim(), banUntil, userId);
        notifyUserBan(userId, reason.trim(), banUntil.toString());
        log(actorId, "BAN_USER", "USER", userId, reason);
    }

    @Transactional
    public void unblock(String userId, String actorId) {
        if ("已注销".equals(findById(userId).getStatus())) throw new IllegalStateException("已注销账号不能解封");
        jdbc.update("update users set status = '正常', ban_reason = null, ban_until = null where id = ?", userId);
        notifyUserUnblock(userId);
        log(actorId, "UNBLOCK_USER", "USER", userId, "");
    }

    RowMapper<UserDto> userMapper() {
        return new RowMapper<UserDto>() {
            public UserDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                UserDto user = new UserDto();
                user.setId(rs.getString("id"));
                user.setEmail(rs.getString("email"));
                user.setNickname(rs.getString("nickname"));
                user.setAvatar(rs.getString("avatar"));
                user.setRole(rs.getString("role"));
                user.setCity(rs.getString("city"));
                user.setGender(rs.getString("gender"));
                Date birthday = rs.getDate("birthday");
                user.setBirthday(birthday == null ? null : birthday.toString());
                user.setBio(rs.getString("bio"));
                user.setInterests(DbSupport.split(rs.getString("interests")));
                user.setFollowing(rs.getInt("following_count"));
                user.setFollowers(rs.getInt("follower_count"));
                user.setCredit(rs.getInt("credit"));
                user.setVerified(rs.getBoolean("verified"));
                user.setStatus(rs.getString("status"));
                user.setBanReason(rs.getString("ban_reason"));
                Date banUntil = rs.getDate("ban_until");
                user.setBanUntil(banUntil == null ? null : banUntil.toString());
                user.setMerchantName(rs.getString("merchant_name"));
                user.setMerchantNickname(rs.getString("merchant_nickname"));
                user.setMerchantFields(DbSupport.split(rs.getString("merchant_fields")));
                return user;
            }
        };
    }

    private RowMapper<UserRecord> userRecordMapper() {
        return new RowMapper<UserRecord>() {
            public UserRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
                UserRecord record = new UserRecord();
                record.user = userMapper().mapRow(rs, rowNum);
                record.passwordHash = rs.getString("password_hash");
                record.activated = rs.getBoolean("activated");
                record.status = rs.getString("status");
                record.banReason = rs.getString("ban_reason");
                record.banUntil = rs.getDate("ban_until");
                return record;
            }
        };
    }

    private void validateRegistration(AuthDtos.RegisterRequest request) {
        if (request.getEmail() == null || !request.getEmail().matches("^\\S+@\\S+\\.\\S+$")) throw new IllegalStateException("请输入有效的邮箱地址");
        if (request.getPassword() == null || request.getPassword().length() < 8) throw new IllegalStateException("密码至少需要 8 位");
        if (request.getConfirmPassword() != null && !request.getPassword().equals(request.getConfirmPassword())) throw new IllegalStateException("两次密码不一致");
        if (request.getNickname() == null || request.getNickname().trim().isEmpty()) throw new IllegalStateException("请输入昵称");
        Integer count = jdbc.queryForObject("select count(*) from users where email = ? or nickname = ?", Integer.class,
                request.getEmail().trim().toLowerCase(), request.getNickname().trim());
        if (count != null && count > 0) throw new IllegalStateException("邮箱或昵称已被注册");
        if ("商家用户".equals(request.getRole()) && (request.getMerchantName() == null || request.getMerchantName().trim().isEmpty())) {
            throw new IllegalStateException("商家注册需要填写商家名称");
        }
    }

    private String hashPassword(String password) {
        String salt = UUID.randomUUID().toString().replace("-", "");
        return "sha256:" + salt + ":" + sha256(salt + ":" + password);
    }

    private boolean passwordMatches(String password, String stored) {
        if (stored == null) return false;
        if (stored.startsWith("plain:")) return stored.substring("plain:".length()).equals(password);
        if (stored.startsWith("sha256:")) {
            String[] parts = stored.split(":");
            return parts.length == 3 && parts[2].equals(sha256(parts[1] + ":" + password));
        }
        return false;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) builder.append(String.format("%02x", item));
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("密码处理失败");
        }
    }

    private boolean banExpired(Date banUntil) {
        return banUntil != null && banUntil.toLocalDate().isBefore(LocalDate.now());
    }

    private boolean banExpired(String banUntil) {
        return banUntil != null && Date.valueOf(banUntil).toLocalDate().isBefore(LocalDate.now());
    }

    private String banMessage(String reason, Date banUntil) {
        String until = banUntil == null ? "未设置期限" : banUntil.toString();
        return "账号已被封禁：" + DbSupport.safe(reason, "未填写原因") + "；封禁至：" + until;
    }

    private String banMessage(String reason, String banUntil) {
        return "账号已被封禁：" + DbSupport.safe(reason, "未填写原因") + "；封禁至：" + DbSupport.safe(banUntil, "未设置期限");
    }

    private void notifyUserBan(String userId, String reason, String until) {
        if (socialService == null) return;
        String content = "您的账号已被管理员封禁。原因：" + DbSupport.safe(reason, "未填写原因")
                + "；封禁至：" + DbSupport.safe(until, "未设置期限");
        socialService.createNotification(userId, "账号封禁通知", "账号已被封禁", content, "user", userId);
    }

    private void notifyUserUnblock(String userId) {
        if (socialService == null) return;
        socialService.createNotification(userId, "账号解封通知", "账号已恢复正常",
                "您的账号已被管理员解封，现在可以正常使用平台功能。", "user", userId);
    }

    private String emptyFilter(String value) {
        return value == null || value.trim().isEmpty() || value.startsWith("全部") ? "" : value;
    }

    private void log(String actorId, String action, String targetType, String targetId, String reason) {
        jdbc.update("insert into audit_logs (id,actor_id,action,target_type,target_id,reason) values (?,?,?,?,?,?)",
                DbSupport.id("log"), actorId, action, targetType, targetId, reason);
    }

    private static class UserRecord {
        UserDto user;
        String passwordHash;
        boolean activated;
        String status;
        String banReason;
        Date banUntil;
    }
}
