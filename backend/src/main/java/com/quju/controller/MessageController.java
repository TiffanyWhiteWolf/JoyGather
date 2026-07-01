package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.MessageDtos;
import com.quju.service.MessageService;
import com.quju.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/conversations")
public class MessageController {
    private final MessageService messageService;
    private final UserService userService;

    public MessageController(MessageService messageService, UserService userService) {
        this.messageService = messageService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<MessageDtos.ConversationDto>> conversations(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(messageService.conversations(userId));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageDtos.MessageDto>> messages(@PathVariable String id,
                                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(messageService.messages(id, userId));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<MessageDtos.MessageDto> send(@PathVariable String id,
                                                    @RequestBody MessageDtos.SendMessageRequest request,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        String senderId = userService.requireToken(authorization).getId();
        return ApiResponse.success(messageService.send(id, senderId, request));
    }

    @PostMapping("/messages/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id,
                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        messageService.markRead(id, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/messages/{id}/recall")
    public ApiResponse<Void> recall(@PathVariable String id,
                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        messageService.recall(id, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/messages/{id}/forward")
    public ApiResponse<MessageDtos.MessageDto> forward(@PathVariable String id,
                                                       @RequestBody MessageDtos.ForwardRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        return ApiResponse.success(messageService.forward(id, request.getConversationId(), userId));
    }

    @PostMapping("/{id}/pin")
    public ApiResponse<Void> togglePin(@PathVariable String id,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        messageService.togglePin(id, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/mute")
    public ApiResponse<Void> toggleMute(@PathVariable String id,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        messageService.toggleMute(id, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable String id,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        messageService.markConversationRead(id, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/by-user")
    public ApiResponse<Map<String, String>> findOrCreate(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String userId = userService.requireToken(authorization).getId();
        String targetUserId = request.get("userId");
        return ApiResponse.success(messageService.findOrCreateConversation(userId, targetUserId));
    }
}
