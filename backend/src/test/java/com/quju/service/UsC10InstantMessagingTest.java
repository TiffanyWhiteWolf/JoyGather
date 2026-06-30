package com.quju.service;

import com.quju.dto.MessageDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * US-C10 即时通讯
 */
class UsC10InstantMessagingTest extends TestBase {
    private MessageService messageService;

    @BeforeEach
    void initMessageService() {
        messageService = new MessageService(jdbc);
        createFriendConversation("cv-friend", USER_ID, "user-a");
        createTeamConversation("team-c10", "cv-team", USER_ID, "user-a");
    }

    @Test
    @DisplayName("US-C10-1: 支持好友和小队文字、图片和位置消息")
    void shouldSendTextImageAndLocationMessages() {
        MessageDtos.SendMessageRequest text = new MessageDtos.SendMessageRequest();
        text.setType("TEXT");
        text.setContent("你好 😀");
        MessageDtos.MessageDto textMessage = messageService.send("cv-friend", USER_ID, text);
        assertEquals("你好 😀", textMessage.getContent());
        assertEquals("TEXT", textMessage.getType());

        MessageDtos.SendMessageRequest image = new MessageDtos.SendMessageRequest();
        image.setType("IMAGE");
        image.setContent("photo.png");
        image.setMediaUrl("https://example.com/photo.png");
        MessageDtos.MessageDto imageMessage = messageService.send("cv-team", USER_ID, image);
        assertEquals("IMAGE", imageMessage.getType());
        assertEquals("https://example.com/photo.png", imageMessage.getMediaUrl());

        MessageDtos.SendMessageRequest location = new MessageDtos.SendMessageRequest();
        location.setType("LOCATION");
        location.setContent("我的位置");
        location.setLatitude(30.25012);
        location.setLongitude(120.15515);
        MessageDtos.MessageDto locationMessage = messageService.send("cv-team", "user-a", location);
        assertEquals("LOCATION", locationMessage.getType());
        assertEquals(30.25012, locationMessage.getLatitude());
        assertEquals(120.15515, locationMessage.getLongitude());
    }

    @Test
    @DisplayName("US-C10-2: 消息可标记已读")
    void shouldMarkMessageRead() {
        MessageDtos.MessageDto message = sendText("cv-friend", USER_ID, "请查收");
        messageService.markRead(message.getId(), "user-a");
        Boolean read = jdbc.queryForObject("select read_flag from messages where id = ?", Boolean.class, message.getId());
        assertEquals(Boolean.TRUE, read);
    }

    @Test
    @DisplayName("US-C10-3: 2分钟内可撤回本人消息，超时或非本人不可撤回")
    void shouldRecallOnlyOwnRecentMessage() {
        MessageDtos.MessageDto mine = sendText("cv-friend", USER_ID, "撤回测试");
        messageService.recall(mine.getId(), USER_ID);
        Boolean recalled = jdbc.queryForObject("select recalled from messages where id = ?", Boolean.class, mine.getId());
        assertEquals(Boolean.TRUE, recalled);

        MessageDtos.MessageDto other = sendText("cv-friend", "user-a", "别人的消息");
        IllegalStateException notOwner = assertThrows(IllegalStateException.class,
                () -> messageService.recall(other.getId(), USER_ID));
        assertEquals("只能撤回自己发送的消息", notOwner.getMessage());

        MessageDtos.MessageDto old = sendText("cv-friend", USER_ID, "过期消息");
        jdbc.update("update messages set sent_at = dateadd('MINUTE', -3, now()) where id = ?", old.getId());
        IllegalStateException expired = assertThrows(IllegalStateException.class,
                () -> messageService.recall(old.getId(), USER_ID));
        assertEquals("消息发送超过 2 分钟，不能撤回", expired.getMessage());
    }

    @Test
    @DisplayName("US-C10-4: 消息可转发到好友或小队，位置消息保留坐标")
    void shouldForwardMessageToFriendOrTeamConversation() {
        MessageDtos.SendMessageRequest location = new MessageDtos.SendMessageRequest();
        location.setType("LOCATION");
        location.setContent("我的位置");
        location.setLatitude(30.1);
        location.setLongitude(120.2);
        MessageDtos.MessageDto original = messageService.send("cv-friend", USER_ID, location);

        MessageDtos.MessageDto forwarded = messageService.forward(original.getId(), "cv-team", USER_ID);

        assertEquals("LOCATION", forwarded.getType());
        assertEquals(30.1, forwarded.getLatitude());
        assertEquals(120.2, forwarded.getLongitude());
        String forwardedFrom = jdbc.queryForObject("select forwarded_from_id from messages where id = ?", String.class, forwarded.getId());
        assertEquals(original.getId(), forwardedFrom);

        messageService.recall(original.getId(), USER_ID);
        IllegalStateException recalled = assertThrows(IllegalStateException.class,
                () -> messageService.forward(original.getId(), "cv-team", USER_ID));
        assertEquals("已撤回的消息不能转发", recalled.getMessage());
    }

    @Test
    @DisplayName("US-C10-5: 非好友且非同队关系不可发送消息")
    void shouldRejectNonFriendAndNonTeamMember() {
        createDirectConversation("cv-not-friend", USER_ID, "user-b");
        MessageDtos.SendMessageRequest request = textRequest("不能发送");
        IllegalStateException notFriend = assertThrows(IllegalStateException.class,
                () -> messageService.send("cv-not-friend", USER_ID, request));
        assertEquals("你们还不是好友，不能发送消息", notFriend.getMessage());

        jdbc.update("insert ignore into conversation_participants (conversation_id,user_id) values (?,?)", "cv-team", "user-b");
        IllegalStateException notTeamMember = assertThrows(IllegalStateException.class,
                () -> messageService.send("cv-team", "user-b", request));
        assertEquals("非好友且非同队关系不可发送消息", notTeamMember.getMessage());
    }

    private MessageDtos.MessageDto sendText(String conversationId, String senderId, String content) {
        return messageService.send(conversationId, senderId, textRequest(content));
    }

    private MessageDtos.SendMessageRequest textRequest(String content) {
        MessageDtos.SendMessageRequest request = new MessageDtos.SendMessageRequest();
        request.setType("TEXT");
        request.setContent(content);
        return request;
    }

    private void createFriendConversation(String conversationId, String a, String b) {
        jdbc.update("insert ignore into friendships (user_id, friend_id) values (?,?), (?,?)", a, b, b, a);
        createDirectConversation(conversationId, a, b);
    }

    private void createDirectConversation(String conversationId, String a, String b) {
        jdbc.update("insert into conversations (id,name,avatar,type,friend_user_id,unread,last_message,last_time,online) values (?,?,?,?,?,?,?,?,?)",
                conversationId, "好友会话", "", "好友", b, 0, "", "刚刚", true);
        jdbc.update("insert into conversation_participants (conversation_id,user_id) values (?,?), (?,?)",
                conversationId, a, conversationId, b);
    }

    private void createTeamConversation(String teamId, String conversationId, String... userIds) {
        jdbc.update("insert into teams (id,name,description,cover,tags,members_count,capacity,join_mode,active_now,status,owner_id) values (?,?,?,?,?,?,?,?,?,?,?)",
                teamId, "C10小队", "即时通讯测试小队", "", "测试", userIds.length, 20, "公开加入", userIds.length, "正常", userIds[0]);
        jdbc.update("insert into conversations (id,name,avatar,type,team_id,unread,last_message,last_time,online) values (?,?,?,?,?,?,?,?,?)",
                conversationId, "C10小队", "", "小队", teamId, 0, "", "刚刚", true);
        for (int i = 0; i < userIds.length; i++) {
            jdbc.update("insert into team_members (team_id,user_id,role) values (?,?,?)", teamId, userIds[i], i == 0 ? "队长" : "成员");
            jdbc.update("insert into conversation_participants (conversation_id,user_id) values (?,?)", conversationId, userIds[i]);
        }
    }
}
