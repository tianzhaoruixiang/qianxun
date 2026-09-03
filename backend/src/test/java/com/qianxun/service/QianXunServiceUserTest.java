package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.domain.AppUser;
import com.qianxun.repo.AppUserRepository;
import com.qianxun.security.UserRoles;
import com.qianxun.web.dto.CreateUserRequest;
import com.qianxun.web.dto.UserResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QianXunServiceUserTest {

    @Mock
    private AppUserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private QianXunServiceUser service;

    @BeforeEach
    void setUp() {
        service = new QianXunServiceUser(userRepository, passwordEncoder);
        UserContext.set("1", "admin", "管理员", UserRoles.ADMIN);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createFunctionalUser_asAdmin_shouldPersistHashedPasswordAndNumericId() {
        when(userRepository.findByUsername("operator")).thenReturn(Optional.empty());
        when(userRepository.nextNumericUserId()).thenReturn("2");

        UserResponse created = service.createFunctionalUser(new CreateUserRequest("operator", "secret", "业务员"));

        assertThat(created.username()).isEqualTo("operator");
        assertThat(created.displayName()).isEqualTo("业务员");
        assertThat(created.role()).isEqualTo(UserRoles.FUNCTIONAL);
        assertThat(created.id()).isEqualTo("2");
        assertThat(created.id()).matches("^\\d+$");

        ArgumentCaptor<AppUser> cap = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).insert(cap.capture());
        AppUser stored = cap.getValue();
        assertThat(stored.passwordHash()).isNotEqualTo("secret");
        assertThat(passwordEncoder.matches("secret", stored.passwordHash())).isTrue();
        assertThat(stored.role()).isEqualTo(UserRoles.FUNCTIONAL);
        assertThat(stored.id()).isEqualTo("2");
    }

    @Test
    void createFunctionalUser_asFunctional_shouldForbidden() {
        UserContext.set("u2", "operator", "业务员", UserRoles.FUNCTIONAL);

        assertThatThrownBy(() -> service.createFunctionalUser(new CreateUserRequest("n2", "p", "n")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(403);
        verify(userRepository, never()).insert(any());
    }

    @Test
    void createFunctionalUser_duplicateUsername_shouldConflict() {
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(existing("operator")));

        assertThatThrownBy(() -> service.createFunctionalUser(new CreateUserRequest("operator", "p", "n")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(409);
        verify(userRepository, never()).insert(any());
    }

    @Test
    void createFunctionalUser_duplicateKeyExhausted_shouldConflict() {
        when(userRepository.findByUsername("operator")).thenReturn(Optional.empty());
        when(userRepository.nextNumericUserId()).thenReturn("2", "3", "4");
        doThrow(new DuplicateKeyException("dup")).when(userRepository).insert(any());

        assertThatThrownBy(() -> service.createFunctionalUser(new CreateUserRequest("operator", "p", "n")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void createFunctionalUser_idConflictThenRetry_shouldSucceed() {
        when(userRepository.findByUsername("operator")).thenReturn(Optional.empty());
        when(userRepository.nextNumericUserId()).thenReturn("2", "3");
        doThrow(new DuplicateKeyException("id"))
                .doNothing()
                .when(userRepository).insert(any());

        UserResponse created = service.createFunctionalUser(new CreateUserRequest("operator", "secret", "业务员"));

        assertThat(created.id()).isEqualTo("3");
        verify(userRepository, times(2)).insert(any());
        verify(userRepository, times(2)).nextNumericUserId();
    }

    @Test
    void createFunctionalUser_emptyPassword_shouldBadRequest() {
        assertThatThrownBy(() -> service.createFunctionalUser(new CreateUserRequest("operator", "  ", "n")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
        assertThatThrownBy(() -> service.createFunctionalUser(new CreateUserRequest("operator", "", "n")))
                .isInstanceOf(ResponseStatusException.class);
        verify(userRepository, never()).insert(any());
    }

    @Test
    void authenticate_shouldMatchBcrypt() {
        String hash = passwordEncoder.encode("admin123");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(
                new AppUser("1", "admin", "管理员", hash, UserRoles.ADMIN, true, Instant.now(), Instant.now())
        ));
        assertThat(service.authenticate("admin", "admin123")).isPresent();
        assertThat(service.authenticate("admin", "wrong")).isEmpty();
    }

    private static AppUser existing(String username) {
        return new AppUser("x", username, username, "hash", UserRoles.FUNCTIONAL, true, Instant.now(), Instant.now());
    }
}
