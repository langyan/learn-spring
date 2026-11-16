package com.lin.spring.chat.service.conversation;

import com.lin.spring.chat.service.common.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    public record DirectBody(@NotBlank String targetUserId) {
    }

    public record GroupBody(@NotBlank String name, @NotEmpty List<String> memberIds) {
    }

    public record InviteBody(@NotEmpty List<String> userIds) {
    }

    public record RenameBody(@NotBlank String name) {
    }

    /** 会话列表（未读数/预览/在线状态/最后消息排序） */
    @GetMapping
    public List<ConversationService.ConversationView> list() {
        return conversationService.list(CurrentUser.id());
    }

    /** 发起或复用单聊 */
    @PostMapping("/direct")
    public ConversationService.ConversationView direct(@RequestBody DirectBody body) {
        return conversationService.createOrGetDirect(CurrentUser.id(), body.targetUserId());
    }

    /** 建群 */
    @PostMapping("/group")
    public ConversationService.ConversationView createGroup(@RequestBody GroupBody body) {
        return conversationService.createGroup(CurrentUser.id(), body.name(), body.memberIds());
    }

    /** 会话详情（群成员列表 + 在线状态） */
    @GetMapping("/{id}")
    public ConversationService.ConversationView detail(@PathVariable String id) {
        return conversationService.detail(CurrentUser.id(), id);
    }

    /** 群改名 */
    @PatchMapping("/{id}")
    public void rename(@PathVariable String id, @RequestBody RenameBody body) {
        conversationService.rename(CurrentUser.id(), id, body.name());
    }

    /** 邀请成员入群 */
    @PostMapping("/{id}/members")
    public ConversationService.ConversationView invite(@PathVariable String id, @RequestBody InviteBody body) {
        return conversationService.invite(CurrentUser.id(), id, body.userIds());
    }

    /** 群主移除成员 */
    @DeleteMapping("/{id}/members/{userId}")
    public void removeMember(@PathVariable String id, @PathVariable String userId) {
        conversationService.removeMember(CurrentUser.id(), id, userId);
    }

    /** 退群 */
    @PostMapping("/{id}/leave")
    public void leave(@PathVariable String id) {
        conversationService.leave(CurrentUser.id(), id);
    }

    /** 解散群 */
    @DeleteMapping("/{id}")
    public void dissolve(@PathVariable String id) {
        conversationService.dissolve(CurrentUser.id(), id);
    }
}
