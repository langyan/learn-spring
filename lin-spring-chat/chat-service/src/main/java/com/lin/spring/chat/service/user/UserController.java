package com.lin.spring.chat.service.user;

import com.lin.spring.chat.service.auth.AuthService;
import com.lin.spring.chat.service.common.ApiException;
import com.lin.spring.chat.service.common.CurrentUser;
import com.lin.spring.chat.service.entity.User;
import com.lin.spring.chat.service.repository.UserRepository;
import com.lin.spring.chat.service.support.PresenceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PresenceStore presenceStore;

    public record UserItem(String id, String username, String nickname, boolean online) {
    }

    /** 我的信息 */
    @GetMapping("/me")
    public AuthService.UserView me() {
        User user = userRepository.findById(CurrentUser.id())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        return new AuthService.UserView(user.getId(), user.getUsername(), user.getNickname());
    }

    /** 搜索用户（发起单聊/邀请入群的入口），默认过滤自己 */
    @GetMapping("/search")
    public List<UserItem> search(@RequestParam("q") String q) {
        String me = CurrentUser.id();
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String kw = q.trim();
        return userRepository
                .findTop20ByUsernameContainingOrNicknameContainingOrderByUsername(kw, kw).stream()
                .filter(u -> !u.getId().equals(me))
                .map(u -> new UserItem(u.getId(), u.getUsername(), u.getNickname(), presenceStore.isOnline(u.getId())))
                .toList();
    }
}
