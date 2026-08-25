package com.qianxun.service;

import com.qianxun.context.UserContext;
import com.qianxun.llm.HermesAgentClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class McpPluginService {

    private final HermesAgentClient hermes;

    public McpPluginService(HermesAgentClient hermes) {
        this.hermes = hermes;
    }

    public List<HermesAgentClient.McpServerInfo> listMcp(String profile) {
        return hermes.listMcpServers(UserContext.getCurrentUserId(), profile);
    }

    public HermesAgentClient.McpWriteResult upsertMcp(String profile, String name, String command,
            List<String> args, Map<String, String> env, boolean enabled, String description,
            String transport, String url) {
        return hermes.upsertMcpServer(UserContext.getCurrentUserId(), profile, name, command,
                args, env, enabled, description, transport, url);
    }

    public HermesAgentClient.McpWriteResult toggleMcp(String profile, String name, boolean enabled) {
        return hermes.toggleMcpServer(UserContext.getCurrentUserId(), profile, name, enabled);
    }

    public HermesAgentClient.McpWriteResult deleteMcp(String profile, String name) {
        return hermes.deleteMcpServer(UserContext.getCurrentUserId(), profile, name);
    }

    public List<HermesAgentClient.PluginInfo> listPlugins(String profile) {
        return hermes.listPlugins(UserContext.getCurrentUserId(), profile);
    }

    public HermesAgentClient.PluginWriteResult upsertPlugin(String profile, String name, String path,
            String version, boolean enabled, String description, Map<String, Object> manifest) {
        return hermes.upsertPlugin(UserContext.getCurrentUserId(), profile, name, path,
                version, enabled, description, manifest);
    }

    public HermesAgentClient.PluginWriteResult deletePlugin(String profile, String name) {
        return hermes.deletePlugin(UserContext.getCurrentUserId(), profile, name);
    }

    public HermesAgentClient.PluginWriteResult togglePlugin(String profile, String name, boolean enabled) {
        return hermes.togglePlugin(UserContext.getCurrentUserId(), profile, name, enabled);
    }
}
