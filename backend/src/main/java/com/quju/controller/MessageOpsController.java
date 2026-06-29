package com.quju.controller;

import com.quju.common.ApiResponse;
import com.quju.dto.MessageDtos;
import com.quju.service.MessageService;
import com.quju.service.UserService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/messages")
public class MessageOpsController {
    private final MessageService messageService;
    private final UserService userService;

    public MessageOpsController(MessageService messageService, UserService userService) {
        this.messageService = messageService;
        this.userService = userService;
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id,
                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        messageService.markRead(id, userService.requireToken(authorization).getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/recall")
    public ApiResponse<Void> recall(@PathVariable String id,
                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        messageService.recall(id, userService.requireToken(authorization).getId());
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/forward")
    public ApiResponse<MessageDtos.MessageDto> forward(@PathVariable String id,
                                                       @RequestBody MessageDtos.ForwardRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(messageService.forward(id, request.getConversationId(), userService.requireToken(authorization).getId()));
    }
}
