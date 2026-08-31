import { createSdkMcpServer, tool } from "@anthropic-ai/claude-agent-sdk";
import { z } from "zod";

function textResult(obj) {
  return { content: [{ type: "text", text: typeof obj === "string" ? obj : JSON.stringify(obj) }] };
}

async function postJson(baseUrl, token, path, body) {
  const url = `${String(baseUrl).replace(/\/$/, "")}${path}`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(body || {}),
  });
  const raw = await res.text();
  let parsed = null;
  try {
    parsed = raw ? JSON.parse(raw) : null;
  } catch {
    parsed = { message: raw };
  }
  if (!res.ok) {
    const msg = parsed && parsed.message ? parsed.message : `HTTP ${res.status}`;
    throw new Error(msg);
  }
  if (parsed && typeof parsed === "object" && "code" in parsed && parsed.code !== 0) {
    throw new Error(parsed.message || "请求失败");
  }
  return parsed && typeof parsed === "object" && "data" in parsed ? parsed.data : parsed;
}

export function buildOfficerMcp(orchestration) {
  const orch = orchestration && typeof orchestration === "object" ? orchestration : null;
  if (!orch || !orch.callbackBaseUrl || !orch.bearerToken || !orch.parentRunId || !orch.parentSessionId) {
    return null;
  }
  const baseUrl = String(orch.callbackBaseUrl).trim();
  const token = String(orch.bearerToken).trim();
  const parentRunId = String(orch.parentRunId).trim();
  const parentSessionId = String(orch.parentSessionId).trim();

  return createSdkMcpServer({
    name: "qianxun-officer",
    version: "1.0.0",
    instructions: "专业任务用 delegate_to_agent，等返回后按 artifact 汇总。",
    tools: [
      tool(
        "delegate_to_agent",
        "委派专业智能体并等待结束。参数：agentCode、完整 message。返回 id/state/artifact。",
        { agentCode: z.string(), message: z.string() },
        async (args) => {
          const data = await postJson(baseUrl, token, "/QianXunService/agent-tasks/submit", {
            agentCode: args.agentCode,
            message: args.message,
            parentRunId,
            parentSessionId,
            wait: true,
          });
          return textResult(data);
        },
      ),
      tool(
        "get_agent_task",
        "查询委派任务状态。",
        { id: z.string() },
        async (args) => {
          const data = await postJson(baseUrl, token, "/QianXunService/agent-tasks/get", { id: args.id });
          return textResult(data);
        },
      ),
      tool(
        "cancel_agent_task",
        "取消委派任务。",
        { id: z.string() },
        async (args) => {
          const data = await postJson(baseUrl, token, "/QianXunService/agent-tasks/cancel", { id: args.id });
          return textResult(data);
        },
      ),
    ],
  });
}

export {
  officerAllowedTools,
  officerBuiltinDelegationTools,
  officerHint,
} from "./officerPolicy.js";
