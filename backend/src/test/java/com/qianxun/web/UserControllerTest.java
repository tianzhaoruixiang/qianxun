package com.qianxun.web;

import com.qianxun.security.JwtService;
import com.qianxun.security.UserRoles;
import com.qianxun.service.QianXunServiceUser;
import com.qianxun.web.dto.CreateUserRequest;
import com.qianxun.web.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import(JwtService.class)
@TestPropertySource(properties = "qianxun.auth.enabled=false")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QianXunServiceUser userService;

    @Test
    void create_asAdmin_shouldReturnUserWithoutPassword() throws Exception {
        when(userService.createFunctionalUser(any())).thenReturn(
                new UserResponse("abc", "operator", "业务员", null, true, UserRoles.FUNCTIONAL)
        );

        mockMvc.perform(post("/QianXunService/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"username\":\"operator\",\"password\":\"secret\",\"displayName\":\"业务员\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("operator"))
                .andExpect(jsonPath("$.data.role").value("functional"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void create_asNonAdmin_shouldForbidden() throws Exception {
        when(userService.createFunctionalUser(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可创建功能用户"));

        mockMvc.perform(post("/QianXunService/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"username\":\"x\",\"password\":\"p\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        verify(userService).createFunctionalUser(any(CreateUserRequest.class));
    }

    @Test
    void create_duplicateUsername_shouldConflict() throws Exception {
        when(userService.createFunctionalUser(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在"));

        mockMvc.perform(post("/QianXunService/users/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{\"username\":\"operator\",\"password\":\"p\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void me_shouldIncludeRole() throws Exception {
        when(userService.getCurrentUser()).thenReturn(
                new UserResponse("1", "admin", "管理员", null, true, UserRoles.ADMIN)
        );
        mockMvc.perform(post("/QianXunService/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonArg\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"));
        verify(userService, never()).createFunctionalUser(any());
    }
}
