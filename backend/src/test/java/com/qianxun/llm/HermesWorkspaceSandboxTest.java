package com.qianxun.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HermesWorkspaceSandboxTest {

    @Test
    void resolveUserWorkspace() {
        assertThat(HermesWorkspaceSandbox.resolve("jicai-agent", "user123def456"))
                .isEqualTo("/opt/data/user123def456/workspace");
    }

    @Test
    void resolveDefaultProfileSameWorkspace() {
        assertThat(HermesWorkspaceSandbox.resolve("default", "user-01"))
                .isEqualTo("/opt/data/user-01/workspace");
        assertThat(HermesWorkspaceSandbox.resolve("", "user-01"))
                .isEqualTo("/opt/data/user-01/workspace");
    }

    @Test
    void profileHomeUnderUserProfilesTree() {
        assertThat(HermesWorkspaceSandbox.profileHome("user-01", "jicai-agent"))
                .isEqualTo("/opt/data/user-01/profiles/jicai-agent");
        assertThat(HermesWorkspaceSandbox.profileHome("user-01", "default"))
                .isEqualTo("/opt/data/user-01/profiles/default");
    }

    @Test
    void sameUserSharesWorkspaceAcrossSessions() {
        assertThat(HermesWorkspaceSandbox.resolve("jicai-agent", "user-01"))
                .isEqualTo(HermesWorkspaceSandbox.resolve("default", "user-01"));
    }

    @Test
    void differentUsersGetDifferentWorkspaces() {
        assertThat(HermesWorkspaceSandbox.resolve("jicai-agent", "user-01"))
                .isNotEqualTo(HermesWorkspaceSandbox.resolve("jicai-agent", "user-02"));
    }

    @Test
    void rejectsUnsafeOwnerId() {
        assertThat(HermesWorkspaceSandbox.sanitizeOwnerId("../etc")).isEmpty();
        assertThat(HermesWorkspaceSandbox.sanitizeOwnerId("a/b")).isEmpty();
        assertThatThrownBy(() -> HermesWorkspaceSandbox.resolve("jicai-agent", "../x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HermesWorkspaceSandbox.resolve("jicai-agent", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
