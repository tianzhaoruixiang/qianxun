package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.domain.AppUser;
import com.qianxun.repo.AppUserRepository;
import com.qianxun.security.UserRoles;
import com.qianxun.web.dto.CreateUserRequest;
import com.qianxun.web.dto.UserResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

@Service
public class QianXunServiceUser {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public QianXunServiceUser(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse getCurrentUser() {
        return new UserResponse(
                UserContext.getCurrentUserId(),
                UserContext.getCurrentUserName(),
                UserContext.getCurrentDisplayName(),
                null,
                true,
                UserContext.getCurrentRole()
        );
    }

    public Optional<AppUser> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return Optional.empty();
        }
        Optional<AppUser> found = userRepository.findByUsername(username.trim());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AppUser user = found.get();
        if (!user.enabled() || user.passwordHash() == null || user.passwordHash().isBlank()) {
            return Optional.empty();
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public UserResponse createFunctionalUser(CreateUserRequest request) {
        if (!UserContext.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可创建功能用户");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        String username = request.username() == null ? "" : request.username().trim();
        String password = request.password() == null ? "" : request.password();
        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        if (username.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        }
        if (username.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名过长");
        }
        if (password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }
        if (password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码过长");
        }
        if (displayName.isEmpty()) {
            displayName = username;
        }
        if (displayName.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "显示名过长");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
        Instant now = Instant.now();
        String passwordHash = passwordEncoder.encode(password);
        final int maxAttempts = 3;
        DuplicateKeyException lastDup = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String id = userRepository.nextNumericUserId();
            AppUser user = new AppUser(
                    id,
                    username,
                    displayName,
                    passwordHash,
                    UserRoles.FUNCTIONAL,
                    true,
                    now,
                    now
            );
            try {
                userRepository.insert(user);
                return toResponse(user);
            } catch (DuplicateKeyException ex) {
                lastDup = ex;
                // id 或 username 唯一冲突：重新取号再试；预检已挡住绝大多数 username 冲突
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在", lastDup);
    }

    static UserResponse toResponse(AppUser user) {
        return new UserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                null,
                user.enabled(),
                UserRoles.normalize(user.role())
        );
    }
}
