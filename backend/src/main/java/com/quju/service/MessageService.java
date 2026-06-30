package com.quju.service;

import com.quju.dto.MessageDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {
    private static final List<String> MESSAGE_TYPES = Arrays.asList("TEXT", "IMAGE", "FILE", "LOCATION");
    private final JdbcTemplate jdbc;

    public MessageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public List<MessageDtos.ConversationDto> conversations(String userId) {
        List<MessageDtos.ConversationDto> rows = findConversations(userId);
        if (rows.isEmpty()) {
            ensureWelcomeConversation(userId);
            rows = findConversations(userId);
        }
        for (MessageDtos.ConversationDto conversation : rows) {
            conversation.setMessages(messages(conversation.getId(), userId));
            List<Timestamp> lastSentList = jdbc.query(
                "select sent_at from messages where conversation_id = ? order by sent_at desc limit 1",
                (rs, n) -> rs.getTimestamp("sent_at"), conversation.getId());
            if (!lastSentList.isEmpty()) conversation.setLastTime(DbSupport.relativeTime(lastSentList.get(0)));
            // 好友会话：动态设置对方的 name、avatar、userId
            if ("好友".equals(conversation.getType())) {
                List<Map<String, Object>> others = jdbc.queryForList(
                    "select u.id, u.nickname, u.avatar from conversation_participants cp join users u on u.id = cp.user_id where cp.conversation_id = ? and cp.user_id != ?",
                    conversation.getId(), userId);
                if (!others.isEmpty()) {
                    Map<String, Object> other = others.get(0);
                    conversation.setName(String.valueOf(other.get("nickname")));
                    conversation.setAvatar(String.valueOf(other.get("avatar")));
                    conversation.setFriendUserId(String.valueOf(other.get("id")));
                }
            }
        }
        return rows;
    }

    private List<MessageDtos.ConversationDto> findConversations(String userId) {
        return jdbc.query("select c.*, p.pinned, p.muted from conversations c join conversation_participants p on p.conversation_id = c.id where p.user_id = ? order by p.pinned desc, c.unread desc, c.id asc", conversationMapper(), userId);
    }

    private void ensureWelcomeConversation(String userId) {
        List<Map<String, Object>> assistants = jdbc.queryForList(
                "select id,nickname,avatar from users where role = '管理员' and id <> ? and status = '正常' order by id limit 1", userId);
        if (assistants.isEmpty()) return;
        Map<String, Object> assistant = assistants.get(0);
        String assistantId = String.valueOf(assistant.get("id"));
        String conversationId = "welcome-" + userId;
        String messageId = "welcome-message-" + userId;
        String greeting = "欢迎来到趣聚！你可以在这里试试发送文字、表情、图片、文件和位置。";
        jdbc.update("insert ignore into conversations (id,name,avatar,type,friend_user_id,unread,last_message,last_time,online) values (?,?,?,?,?,?,?,?,?)",
                conversationId, "趣聚小助手", assistant.get("avatar"), "好友", assistantId, 0, greeting, "刚刚", true);
        jdbc.update("insert ignore into conversation_participants (conversation_id,user_id) values (?,?),(?,?)",
                conversationId, userId, conversationId, assistantId);
        jdbc.update("insert ignore into messages (id,conversation_id,sender_id,content,message_type,mine,read_flag) values (?,?,?,?,?,?,?)",
                messageId, conversationId, assistantId, greeting, "TEXT", false, true);
    }

    public List<MessageDtos.MessageDto> messages(String conversationId, String userId) {
        requireParticipant(conversationId, userId);
        return jdbc.query("select m.*, u.avatar sender_avatar from messages m left join users u on u.id = m.sender_id where m.conversation_id = ? order by m.sent_at asc", messageMapper(userId), conversationId);
    }

    @Transactional
    public MessageDtos.MessageDto send(String conversationId, String senderId, MessageDtos.SendMessageRequest request) {
        requireParticipant(conversationId, senderId);
        // 好友会话需要检查是否仍是好友
        String convType = jdbc.queryForObject("select type from conversations where id = ?", String.class, conversationId);
        if ("好友".equals(convType)) {
            List<String> others = jdbc.queryForList(
                "select user_id from conversation_participants where conversation_id = ? and user_id != ?",
                String.class, conversationId, senderId);
            if (!others.isEmpty()) {
                String other = others.get(0);
                Integer fc = jdbc.queryForObject("select count(*) from friendships where user_id = ? and friend_id = ?", Integer.class, senderId, other);
                if (fc == null || fc == 0) throw new IllegalStateException("你们还不是好友，不能发送消息");
            }
        }
        if (request == null) throw new IllegalStateException("消息内容不能为空");
        String type = DbSupport.safe(request.getType(), "TEXT");
        String content = DbSupport.safe(request.getContent(), "");
        if (!MESSAGE_TYPES.contains(type)) throw new IllegalStateException("不支持的消息类型");
        if ("TEXT".equals(type) && content.trim().isEmpty()) throw new IllegalStateException("消息内容不能为空");
        if (("IMAGE".equals(type) || "FILE".equals(type)) && (request.getMediaUrl() == null || request.getMediaUrl().trim().isEmpty())) throw new IllegalStateException("请先上传文件");
        if ("LOCATION".equals(type)) validateLocation(request.getLatitude(), request.getLongitude());
        String id = DbSupport.id("m");
        jdbc.update("insert into messages (id,conversation_id,sender_id,content,message_type,media_url,location_lat,location_lng,mine,read_flag) values (?,?,?,?,?,?,?,?,?,?)",
                id, conversationId, senderId, content.trim(), type, request.getMediaUrl(), request.getLatitude(), request.getLongitude(), true, false);
        jdbc.update("update conversations set last_message = ?, last_time = ? where id = ?", displayContent(type, content), DbSupport.relativeTime(new Timestamp(System.currentTimeMillis())), conversationId);
        jdbc.update("update conversations set unread = unread + 1 where id = ?", conversationId);
        return jdbc.queryForObject("select m.*, u.avatar sender_avatar from messages m left join users u on u.id = m.sender_id where m.id = ?", messageMapper(senderId), id);
    }

    @Transactional
    public void togglePin(String conversationId, String userId) {
        requireParticipant(conversationId, userId);
        jdbc.update("update conversation_participants set pinned = 1 - pinned where conversation_id = ? and user_id = ?", conversationId, userId);
    }

    @Transactional
    public void toggleMute(String conversationId, String userId) {
        requireParticipant(conversationId, userId);
        jdbc.update("update conversation_participants set muted = 1 - muted where conversation_id = ? and user_id = ?", conversationId, userId);
    }

    @Transactional
    public void markConversationRead(String conversationId, String userId) {
        requireParticipant(conversationId, userId);
        jdbc.update("update conversations set unread = 0 where id = ?", conversationId);
    }

    @Transactional
    public void markRead(String messageId, String userId) {
        List<String> conversations = jdbc.queryForList("select conversation_id from messages where id = ?", String.class, messageId);
        if (conversations.isEmpty()) throw new java.util.NoSuchElementException("消息不存在");
        requireParticipant(conversations.get(0), userId);
        jdbc.update("update messages set read_flag = 1 where id = ?", messageId);
        jdbc.update("update conversation_participants set last_read_at = now() where conversation_id = ? and user_id = ?", conversations.get(0), userId);
    }

    @Transactional
    public void recall(String messageId, String userId) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("select * from messages where id = ?", messageId);
        if (rows.isEmpty()) throw new java.util.NoSuchElementException("消息不存在");
        java.util.Map<String, Object> row = rows.get(0);
        if (!userId.equals(String.valueOf(row.get("sender_id")))) throw new IllegalStateException("只能撤回自己发送的消息");
        Timestamp sentAt = (Timestamp) row.get("sent_at");
        if (sentAt != null && sentAt.toLocalDateTime().isBefore(LocalDateTime.now().minusMinutes(2))) throw new IllegalStateException("消息发送超过 2 分钟，不能撤回");
        jdbc.update("update messages set recalled = 1, recalled_at = now(), content = '消息已撤回' where id = ?", messageId);
    }

    @Transactional
    public MessageDtos.MessageDto forward(String messageId, String targetConversationId, String userId) {
        requireParticipant(targetConversationId, userId);
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("select * from messages where id = ?", messageId);
        if (rows.isEmpty()) throw new java.util.NoSuchElementException("消息不存在");
        java.util.Map<String, Object> row = rows.get(0);
        requireParticipant(String.valueOf(row.get("conversation_id")), userId);
        MessageDtos.SendMessageRequest request = new MessageDtos.SendMessageRequest();
        request.setContent(String.valueOf(row.get("content")));
        request.setType(String.valueOf(row.get("message_type")));
        request.setMediaUrl((String) row.get("media_url"));
        MessageDtos.MessageDto created = send(targetConversationId, userId, request);
        jdbc.update("update messages set forwarded_from_id = ? where id = ?", messageId, created.getId());
        return created;
    }

    private RowMapper<MessageDtos.ConversationDto> conversationMapper() {
        return new RowMapper<MessageDtos.ConversationDto>() {
            public MessageDtos.ConversationDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                MessageDtos.ConversationDto item = new MessageDtos.ConversationDto();
                item.setId(rs.getString("id"));
                item.setName(rs.getString("name"));
                item.setAvatar(rs.getString("avatar"));
                item.setType(rs.getString("type"));
                item.setUnread(rs.getInt("unread"));
                item.setLastMessage(rs.getString("last_message"));
                item.setLastTime(rs.getString("last_time"));
                item.setOnline(rs.getBoolean("online"));
                item.setPinned(rs.getBoolean("pinned"));
                item.setMuted(rs.getBoolean("muted"));
                item.setFriendUserId(rs.getString("friend_user_id"));
                return item;
            }
        };
    }

    private RowMapper<MessageDtos.MessageDto> messageMapper(String currentUserId) {
        return new RowMapper<MessageDtos.MessageDto>() {
            public MessageDtos.MessageDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                MessageDtos.MessageDto item = new MessageDtos.MessageDto();
                item.setId(rs.getString("id"));
                item.setSenderId(rs.getString("sender_id"));
                item.setContent(rs.getString("content"));
                item.setType(rs.getString("message_type"));
                item.setMediaUrl(rs.getString("media_url"));
                item.setLatitude(rs.getObject("location_lat") == null ? null : rs.getDouble("location_lat"));
                item.setLongitude(rs.getObject("location_lng") == null ? null : rs.getDouble("location_lng"));
                item.setTime(DbSupport.formatTime(rs.getTimestamp("sent_at")));
                item.setMine(currentUserId != null && currentUserId.equals(rs.getString("sender_id")));
                item.setRead(rs.getBoolean("read_flag"));
                item.setRecalled(rs.getBoolean("recalled"));
                item.setSenderAvatar(rs.getString("sender_avatar"));
                return item;
            }
        };
    }

    private void requireParticipant(String conversationId, String userId) {
        Integer count = jdbc.queryForObject("select count(*) from conversation_participants where conversation_id = ? and user_id = ?", Integer.class, conversationId, userId);
        if (count == null || count == 0) throw new IllegalStateException("非好友且非同队关系不可发送消息");
    }

    private void validateLocation(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) throw new IllegalStateException("位置坐标不完整");
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalStateException("位置坐标无效");
        }
    }

    private String displayContent(String type, String content) {
        if ("IMAGE".equals(type)) return "[图片]";
        if ("LOCATION".equals(type)) return "[位置]";
        if ("FILE".equals(type)) return "[文件]";
        return content;
    }
}
