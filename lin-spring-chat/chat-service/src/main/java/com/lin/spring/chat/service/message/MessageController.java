package com.lin.spring.chat.service.message;

import com.lin.spring.chat.common.dto.MessageDto;
import com.lin.spring.chat.service.common.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    public record SendBody(@NotBlank String content) {
    }

    public record ReadBody(@NotBlank String lastReadMessageId) {
    }

    /** 发送消息（历史/实时投递走 Kafka 链路） */
    @PostMapping("/conversations/{id}/messages")
    public MessageDto send(@PathVariable String id, @RequestBody SendBody body) {
        return messageService.send(CurrentUser.id(), id, body.content());
    }

    /** 历史消息 keyset 分页：before=上一页最早消息毫秒时间戳，beforeId=该消息 id */
    @GetMapping("/conversations/{id}/messages")
    public List<MessageDto> history(@PathVariable String id,
                                    @RequestParam(required = false) Long before,
                                    @RequestParam(required = false) String beforeId,
                                    @RequestParam(defaultValue = "30") int size) {
        return messageService.history(CurrentUser.id(), id, before, beforeId, size);
    }

    /** 上报已读位置 */
    @PostMapping("/conversations/{id}/read")
    public void markRead(@PathVariable String id, @RequestBody ReadBody body) {
        messageService.markRead(CurrentUser.id(), id, body.lastReadMessageId());
    }

    /** 某条消息的已读状态（群已读数/成员） */
    @GetMapping("/conversations/{id}/messages/{messageId}/read-status")
    public MessageService.ReadStatusView readStatus(@PathVariable String id, @PathVariable String messageId) {
        return messageService.readStatus(CurrentUser.id(), id, messageId);
    }

    /** 撤回消息（2 分钟内） */
    @PostMapping("/messages/{messageId}/recall")
    public void recall(@PathVariable String messageId) {
        messageService.recall(CurrentUser.id(), messageId);
    }

    /** 上线后拉取离线期间未送达消息 */
    @GetMapping("/messages/undelivered")
    public List<MessageDto> undelivered() {
        return messageService.pullUndelivered(CurrentUser.id());
    }
}
