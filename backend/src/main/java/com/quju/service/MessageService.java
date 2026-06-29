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
import java.util.List;

@Service
public class MessageService {
    private final JdbcTemplate jdbc;

    public MessageService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MessageDtos.ConversationDto> conversations(String userId) {
        List<MessageDtos.ConversationDto> rows = jdbc.query("select c.* from conversations c join conversation_participants p on p.conversation_id = c.id where p.user_id = ? order by p.pinned desc, c.unread desc, c.id asc", conversationMapper(), userId);
        for (MessageDtos.ConversationDto conversation : rows) {
            conversation.setMessages(messages(conversation.getId(), userId));
        }
        return rows;
    }

    public List<MessageDtos.MessageDto> messages(String conversationId, String userId) {
        requireParticipant(conversationId, userId);
        return jdbc.query("select * from messages where conversation_id = ? order by sent_at asc", messageMapper(userId), conversationId);
    }

    @Transactional
    public MessageDtos.MessageDto send(String conversationId, String senderId, MessageDtos.SendMessageRequest request) {
        requireParticipant(conversationId, senderId);
        String type = DbSupport.safe(request.getType(), "TEXT");
        String content = DbSupport.safe(request.getContent(), "");
        if ("TEXT".equals(type) && content.trim().isEmpty()) throw new IllegalStateException("消息内容不能为空");
        if (("IMAGE".equals(type) || "FILE".equals(type)) && (request.getMediaUrl() == null || request.getMediaUrl().trim().isEmpty())) throw new IllegalStateException("请先上传文件");
        String id = DbSupport.id("m");
        jdbc.update("insert into messages (id,conversation_id,sender_id,content,message_type,media_url,location_lat,location_lng,mine,read_flag) values (?,?,?,?,?,?,?,?,?,?)",
                id, conversationId, senderId, content.trim(), type, request.getMediaUrl(), request.getLatitude(), request.getLongitude(), true, false);
        jdbc.update("update conversations set last_message = ?, last_time = '刚刚' where id = ?", displayContent(type, content), conversationId);
        jdbc.update("update conversations set unread = unread + 1 where id = ?", conversationId);
        return jdbc.queryForObject("select * from messages where id = ?", messageMapper(senderId), id);
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
                return item;
            }
        };
    }

    private void requireParticipant(String conversationId, String userId) {
        Integer count = jdbc.queryForObject("select count(*) from conversation_participants where conversation_id = ? and user_id = ?", Integer.class, conversationId, userId);
        if (count == null || count == 0) throw new IllegalStateException("非好友且非同队关系不可发送消息");
    }

    private String displayContent(String type, String content) {
        if ("IMAGE".equals(type)) return "[图片]";
        if ("LOCATION".equals(type)) return "[位置]";
        if ("FILE".equals(type)) return "[文件]";
        return content;
    }
}
