package com.quju.service;

import com.quju.dto.SocialDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class SocialService {
    private final JdbcTemplate jdbc;
    private final UserService userService;

    public SocialService(JdbcTemplate jdbc, UserService userService) {
        this.jdbc = jdbc;
        this.userService = userService;
    }

    public List<Map<String, Object>> friends(String userId) {
        return jdbc.queryForList("select f.friend_id userId, f.remark, f.group_name groupName, u.nickname, u.avatar, u.city, u.bio, u.interests from friendships f join users u on u.id = f.friend_id where f.user_id = ? order by f.created_at desc", userId);
    }

    public List<Map<String, Object>> requests(String userId) {
        return jdbc.queryForList("select r.id, r.requester_id requesterId, r.receiver_id receiverId, r.source, r.message, r.status, r.created_at createdAt, r.handled_at handledAt, u.nickname requesterNickname, u.avatar requesterAvatar from friend_requests r join users u on u.id = r.requester_id where r.receiver_id = ? order by r.created_at desc", userId);
    }

    @Transactional
    public void requestFriend(String requesterId, SocialDtos.FriendRequestInput input) {
        String receiverId = input.getUserId();
        if (receiverId == null || receiverId.trim().isEmpty()) throw new IllegalStateException("请选择要添加的用户");
        if (requesterId.equals(receiverId)) throw new IllegalStateException("不能添加自己为好友");
        if (blocked(requesterId, receiverId)) throw new IllegalStateException("存在黑名单关系，不能发送好友申请");
        if (isFriend(requesterId, receiverId)) throw new IllegalStateException("你们已经是好友");
        jdbc.update("insert ignore into friend_requests (id,requester_id,receiver_id,source,message,status) values (?,?,?,?,?,'待处理')",
                DbSupport.id("fr"), requesterId, receiverId, DbSupport.safe(input.getSource(), "PROFILE"), input.getMessage());
        // 自动关注对方
        try { follow(requesterId, receiverId); } catch (Exception ignored) { /* 已关注或其它约束 */ }
        // 给对方发消息通知
        String requesterName = jdbc.queryForObject("select nickname from users where id = ?", String.class, requesterId);
        String content = input.getMessage() != null && !input.getMessage().trim().isEmpty() ? input.getMessage() : "发来了好友申请";
        jdbc.update("insert into notifications (id,user_id,type,title,content,target_type,target_id) values (?,?,?,?,?,?,?)",
                DbSupport.id("nf"), receiverId, "好友申请", (requesterName != null ? requesterName : "用户") + "申请添加你为好友",
                content, "friend_request", requesterId);
    }

    @Transactional
    public void handleRequest(String requestId, String receiverId, boolean approve) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from friend_requests where id = ? and receiver_id = ? and status = '待处理'", requestId, receiverId);
        if (rows.isEmpty()) throw new IllegalStateException("好友申请不存在或已处理");
        String requesterId = String.valueOf(rows.get(0).get("requester_id"));
        // 删除同方向+反方向所有其他申请记录，避免 uk_friend_request_open 唯一键冲突
        jdbc.update("delete from friend_requests where ((requester_id = ? and receiver_id = ?) or (requester_id = ? and receiver_id = ?)) and id != ?",
                requesterId, receiverId, receiverId, requesterId, requestId);
        jdbc.update("update friend_requests set status = ?, handled_at = now() where id = ?", approve ? "已通过" : "已拒绝", requestId);
        if (approve) createFriendship(requesterId, receiverId);
    }

    @Transactional
    public void follow(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) throw new IllegalStateException("不能关注自己");
        if (blocked(followerId, followeeId)) throw new IllegalStateException("存在黑名单关系，不能关注");
        jdbc.update("insert ignore into follows (follower_id,followee_id) values (?,?)", followerId, followeeId);
        jdbc.update("update users set following_count = (select count(*) from follows where follower_id = ?) where id = ?", followerId, followerId);
        jdbc.update("update users set follower_count = (select count(*) from follows where followee_id = ?) where id = ?", followeeId, followeeId);
        Integer mutual = jdbc.queryForObject("select count(*) from follows where follower_id = ? and followee_id = ?", Integer.class, followeeId, followerId);
        if (mutual != null && mutual > 0) createFriendship(followerId, followeeId);
    }

    public List<String> followedUserIds(String userId) {
        return jdbc.queryForList("select followee_id from follows where follower_id = ?", String.class, userId);
    }

    @Transactional
    public void unfollow(String followerId, String followeeId) {
        jdbc.update("delete from follows where follower_id = ? and followee_id = ?", followerId, followeeId);
        jdbc.update("delete from friendships where (user_id = ? and friend_id = ?) or (user_id = ? and friend_id = ?)", followerId, followeeId, followeeId, followerId);
        jdbc.update("update users set following_count = (select count(*) from follows where follower_id = ?) where id = ?", followerId, followerId);
        jdbc.update("update users set follower_count = (select count(*) from follows where followee_id = ?) where id = ?", followeeId, followeeId);
        insertNonFriendBoundary(followerId, followeeId);
    }

    @Transactional
    public void updateFriendMeta(String userId, String friendId, SocialDtos.FriendMetaRequest request) {
        if (!isFriend(userId, friendId)) throw new IllegalStateException("好友关系不存在");
        jdbc.update("update friendships set remark = ?, group_name = ? where user_id = ? and friend_id = ?",
                request.getRemark(), request.getGroupName(), userId, friendId);
    }

    @Transactional
    public void removeFriend(String userId, String friendId) {
        if (!isFriend(userId, friendId)) throw new IllegalStateException("好友关系不存在");
        jdbc.update("delete from friendships where (user_id = ? and friend_id = ?) or (user_id = ? and friend_id = ?)", userId, friendId, friendId, userId);
        insertNonFriendBoundary(userId, friendId);
    }

    @Transactional
    public void block(String userId, String blockedUserId, String reason) {
        jdbc.update("insert into user_blocks (user_id,blocked_user_id,reason) values (?,?,?) on duplicate key update reason = values(reason)",
                userId, blockedUserId, reason);
        // 解除双向好友关系
        jdbc.update("delete from friendships where (user_id = ? and friend_id = ?) or (user_id = ? and friend_id = ?)", userId, blockedUserId, blockedUserId, userId);
        // A 取关 B（如果 A 关注了 B）
        jdbc.update("delete from follows where follower_id = ? and followee_id = ?", userId, blockedUserId);
        // B 取关 A（如果 B 关注了 A）
        jdbc.update("delete from follows where follower_id = ? and followee_id = ?", blockedUserId, userId);
        // 更新双方的关注/粉丝计数
        jdbc.update("update users set following_count = (select count(*) from follows where follower_id = ?) where id = ?", userId, userId);
        jdbc.update("update users set follower_count = (select count(*) from follows where followee_id = ?) where id = ?", userId, userId);
        jdbc.update("update users set following_count = (select count(*) from follows where follower_id = ?) where id = ?", blockedUserId, blockedUserId);
        jdbc.update("update users set follower_count = (select count(*) from follows where followee_id = ?) where id = ?", blockedUserId, blockedUserId);
        insertNonFriendBoundary(userId, blockedUserId);
    }

    @Transactional
    public void unblock(String userId, String blockedUserId) {
        jdbc.update("delete from user_blocks where user_id = ? and blocked_user_id = ?", userId, blockedUserId);
    }

    public List<String> blockedUserIds(String userId) {
        return jdbc.queryForList("select blocked_user_id from user_blocks where user_id = ?", String.class, userId);
    }

    public List<Map<String, Object>> notifications(String userId) {
        return jdbc.queryForList("select * from notifications where user_id = ? order by created_at desc", userId);
    }

    public void markNotificationRead(String id, String userId) {
        jdbc.update("update notifications set read_flag = 1 where id = ? and user_id = ?", id, userId);
    }

    private void createFriendship(String a, String b) {
        jdbc.update("insert ignore into friendships (user_id,friend_id) values (?,?)", a, b);
        jdbc.update("insert ignore into friendships (user_id,friend_id) values (?,?)", b, a);
        // 用双向 participant 查重，比 friend_user_id 更可靠
        Integer existing = jdbc.queryForObject(
            "select count(*) from conversations c where c.type = '好友' and exists (select 1 from conversation_participants where conversation_id = c.id and user_id = ?) and exists (select 1 from conversation_participants where conversation_id = c.id and user_id = ?)",
            Integer.class, a, b);
        if (existing == null || existing == 0) {
            String id = DbSupport.id("cv");
            jdbc.update("insert into conversations (id,name,avatar,type,friend_user_id,unread,last_message,last_time,online) values (?,?,?,?,?,?,?,?,?)",
                    id, userService.findById(b).getNickname(), userService.findById(b).getAvatar(), "好友", b, 0, "你们已经成为好友", "刚刚", true);
            jdbc.update("insert into conversation_participants (conversation_id,user_id) values (?,?),(?,?)", id, a, id, b);
        }
    }

    private boolean isFriend(String a, String b) {
        Integer count = jdbc.queryForObject("select count(*) from friendships where user_id = ? and friend_id = ?", Integer.class, a, b);
        return count != null && count > 0;
    }

    private boolean blocked(String a, String b) {
        Integer count = jdbc.queryForObject("select count(*) from user_blocks where (user_id = ? and blocked_user_id = ?) or (user_id = ? and blocked_user_id = ?)", Integer.class, a, b, b, a);
        return count != null && count > 0;
    }

    private void insertNonFriendBoundary(String userA, String userB) {
        try {
            List<String> convIds = jdbc.queryForList(
                "select c.id from conversations c where c.type = '好友' " +
                "and exists (select 1 from conversation_participants where conversation_id = c.id and user_id = ?) " +
                "and exists (select 1 from conversation_participants where conversation_id = c.id and user_id = ?)",
                String.class, userA, userB);
            if (!convIds.isEmpty()) {
                jdbc.update(
                    "insert into messages (id, conversation_id, sender_id, content, message_type, mine, read_flag) " +
                    "values (?, ?, ?, '', 'SYSTEM', false, true)",
                    DbSupport.id("ms"), convIds.get(0), userA);
            }
        } catch (Exception e) {
            // 分界消息插入失败不影响取消关注/拉黑主流程
        }
    }
}
