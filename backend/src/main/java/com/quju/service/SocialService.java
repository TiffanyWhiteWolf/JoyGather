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
        return jdbc.queryForList("select f.friend_id userId, f.remark, f.group_name groupName, u.nickname, u.avatar, u.city from friendships f join users u on u.id = f.friend_id where f.user_id = ? order by f.created_at desc", userId);
    }

    public List<Map<String, Object>> requests(String userId) {
        return jdbc.queryForList("select r.*, u.nickname requesterNickname, u.avatar requesterAvatar from friend_requests r join users u on u.id = r.requester_id where r.receiver_id = ? order by r.created_at desc", userId);
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
    }

    @Transactional
    public void handleRequest(String requestId, String receiverId, boolean approve) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from friend_requests where id = ? and receiver_id = ? and status = '待处理'", requestId, receiverId);
        if (rows.isEmpty()) throw new IllegalStateException("好友申请不存在或已处理");
        String requesterId = String.valueOf(rows.get(0).get("requester_id"));
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

    @Transactional
    public void unfollow(String followerId, String followeeId) {
        jdbc.update("delete from follows where follower_id = ? and followee_id = ?", followerId, followeeId);
        jdbc.update("delete from friendships where (user_id = ? and friend_id = ?) or (user_id = ? and friend_id = ?)", followerId, followeeId, followeeId, followerId);
        jdbc.update("update users set following_count = (select count(*) from follows where follower_id = ?) where id = ?", followerId, followerId);
        jdbc.update("update users set follower_count = (select count(*) from follows where followee_id = ?) where id = ?", followeeId, followeeId);
    }

    @Transactional
    public void updateFriendMeta(String userId, String friendId, SocialDtos.FriendMetaRequest request) {
        if (!isFriend(userId, friendId)) throw new IllegalStateException("好友关系不存在");
        jdbc.update("update friendships set remark = ?, group_name = ? where user_id = ? and friend_id = ?",
                request.getRemark(), request.getGroupName(), userId, friendId);
    }

    @Transactional
    public void block(String userId, String blockedUserId, String reason) {
        jdbc.update("insert into user_blocks (user_id,blocked_user_id,reason) values (?,?,?) on duplicate key update reason = values(reason)",
                userId, blockedUserId, reason);
        jdbc.update("delete from friendships where (user_id = ? and friend_id = ?) or (user_id = ? and friend_id = ?)", userId, blockedUserId, blockedUserId, userId);
    }

    private void createFriendship(String a, String b) {
        jdbc.update("insert ignore into friendships (user_id,friend_id) values (?,?)", a, b);
        jdbc.update("insert ignore into friendships (user_id,friend_id) values (?,?)", b, a);
        List<String> conversations = jdbc.queryForList("select id from conversations where type = '好友' and ((friend_user_id = ? and id in (select conversation_id from conversation_participants where user_id = ?)) or (friend_user_id = ? and id in (select conversation_id from conversation_participants where user_id = ?))) limit 1", String.class, b, a, a, b);
        if (conversations.isEmpty()) {
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
}
