package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.web.dto.UserResponse;
import org.springframework.stereotype.Service;

/**
 * 用户服务。
 * 用户信息由外部系统通过请求头注入，本服务不查询数据库，直接从 UserContext 中读取。
 */
@Service
public class QianXunUserService {

    public UserResponse getCurrentUser() {
        return new UserResponse(
                UserContext.getCurrentUserId(),
                UserContext.getCurrentUserName(),
                UserContext.getCurrentDisplayName(),
                null,
                true
        );
    }
}
