package com.quju.service;

import com.quju.dto.MessageDtos;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MessageServiceWelcomeTest {
    @Test
    void createsOneWelcomeConversationForUserWithoutChats() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:message_welcome;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table users (id varchar(64) primary key, nickname varchar(80), avatar varchar(500), role varchar(32), status varchar(32))");
        jdbc.execute("create table conversations (id varchar(64) primary key, name varchar(120), avatar varchar(500), type varchar(32), team_id varchar(64), friend_user_id varchar(64), unread int default 0, last_message varchar(1000), last_time varchar(40), online boolean default false)");
        jdbc.execute("create table conversation_participants (conversation_id varchar(64), user_id varchar(64), last_read_at timestamp, muted boolean default false, pinned boolean default false, primary key(conversation_id,user_id))");
        jdbc.execute("create table messages (id varchar(64) primary key, conversation_id varchar(64), sender_id varchar(64), content varchar(1000), message_type varchar(32), media_url varchar(500), location_lat double, location_lng double, sent_at timestamp default current_timestamp, mine boolean default false, read_flag boolean default false, recalled boolean default false)");
        jdbc.update("insert into users (id,nickname,avatar,role,status) values ('admin','周晴','admin.png','管理员','正常'),('new-user','新用户','user.png','个人用户','正常')");

        MessageService service = new MessageService(jdbc);
        List<MessageDtos.ConversationDto> first = service.conversations("new-user");
        List<MessageDtos.ConversationDto> second = service.conversations("new-user");

        assertEquals(1, first.size());
        assertEquals("趣聚小助手", first.get(0).getName());
        assertFalse(first.get(0).getMessages().isEmpty());
        assertEquals(1, second.size());
        assertEquals(1, jdbc.queryForObject("select count(*) from conversations where id = 'welcome-new-user'", Integer.class));
    }
}
