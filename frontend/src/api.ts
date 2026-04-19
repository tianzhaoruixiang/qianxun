export type ChatSession = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};

export type ChatMessage = {
  id: string;
  sessionId: string;
  role: "user" | "assistant" | "system" | string;
  content: string;
  thinkingMode: "quick" | "deep" | null;
  thinkContent: string | null;
  createdAt: string;
};

// ── 用户身份（由外部系统通过 localStorage 注入，后续可替换为 JWT/OAuth）──────

export type CurrentUser = {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  enabled: boolean;
};

const DEFAULT_USER: CurrentUser = {
  id: "1",
  username: "admin",
  displayName: "管理员",
  avatarUrl: null,
  enabled: true,
};

function getStoredUser(): { id: string; name: string; displayName: string } {
  try {
    const id          = localStorage.getItem("qianxun_user_id")          ?? DEFAULT_USER.id;
    const name        = localStorage.getItem("qianxun_user_name")        ?? DEFAULT_USER.username;
    const displayName = localStorage.getItem("qianxun_user_display_name") ?? DEFAULT_USER.displayName;
    return { id, name, displayName };
  } catch {
    return { id: DEFAULT_USER.id, name: DEFAULT_USER.username, displayName: DEFAULT_USER.displayName };
  }
}

/** 从后端获取当前用户信息（后端从请求头中读取，无数据库查询） */
export async function getCurrentUser(): Promise<CurrentUser> {
  try {
    return await http<CurrentUser>("/api/users/me");
  } catch {
    return DEFAULT_USER;
  }
}

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const { id: userId, name: userName, displayName } = getStoredUser();
  const res = await fetch(path, {
    ...init,
    headers: {
      Accept: "application/json",
      "X-User-Id":           encodeURIComponent(userId),
      "X-User-Name":         encodeURIComponent(userName),
      "X-User-Display-Name": encodeURIComponent(displayName),
      ...(init?.headers ?? {}),
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
    },
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `HTTP ${res.status}`);
  }
  return (await res.json()) as T;
}

export async function listSessions(): Promise<ChatSession[]> {
  return http<ChatSession[]>("/api/sessions");
}

export async function createSession(title?: string): Promise<ChatSession> {
  return http<ChatSession>("/api/sessions", {
    method: "POST",
    body: JSON.stringify({ title: title ?? null }),
  });
}

export async function getSession(id: string): Promise<ChatSession> {
  return http<ChatSession>(`/api/sessions/${encodeURIComponent(id)}`);
}

export async function updateSession(id: string, title: string): Promise<ChatSession> {
  return http<ChatSession>(`/api/sessions/${encodeURIComponent(id)}`, {
    method: "PATCH",
    body: JSON.stringify({ title }),
  });
}

export async function deleteSession(id: string): Promise<void> {
  const { id: userId, name: userName, displayName } = getStoredUser();
  const res = await fetch(`/api/sessions/${encodeURIComponent(id)}`, {
    method: "DELETE",
    headers: {
      "X-User-Id":           encodeURIComponent(userId),
      "X-User-Name":         encodeURIComponent(userName),
      "X-User-Display-Name": encodeURIComponent(displayName),
    },
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `HTTP ${res.status}`);
  }
}

export async function listMessages(sessionId: string): Promise<ChatMessage[]> {
  return http<ChatMessage[]>(`/api/sessions/${encodeURIComponent(sessionId)}/messages`);
}

export type NluAnalysis = {
  intent: string;
  scenarioCode: string;
  scenarioName: string;
  agentSkill: string;
  slots: Record<string, unknown>;
  missingRequiredSlots: string[];
  confidence: number;
  reasoning: string;
};

export type SlotDefinition = {
  name: string;
  type: string;
  required: boolean;
  description?: string;
  values?: string[];
};

export type IntentScenario = {
  id: string;
  code: string;
  name: string;
  description: string;
  examples: string[];
  slots: SlotDefinition[];
  agentSkill: string;
  promptTemplate: string;
  extraParams: Record<string, unknown>;
  priority: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export async function listIntentScenarios(enabledOnly = false): Promise<IntentScenario[]> {
  return http<IntentScenario[]>(
    `/api/intent-scenarios${enabledOnly ? "?enabledOnly=true" : ""}`,
  );
}

// ── 反馈 ─────────────────────────────────────────────────────────────────────

export type FeedbackType = "like" | "dislike";

export type MessageFeedback = {
  id: string;
  sessionId: string;
  messageId: string;
  feedbackType: FeedbackType;
  feedbackNote?: string;
  createdAt: string;
};

export async function submitFeedback(
  sessionId: string,
  messageId: string,
  feedbackType: FeedbackType,
  feedbackNote?: string,
): Promise<MessageFeedback> {
  return http<MessageFeedback>(
    `/api/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}/feedback`,
    {
      method: "POST",
      body: JSON.stringify({ feedbackType, feedbackNote: feedbackNote ?? null }),
    },
  );
}

export async function getFeedback(
  sessionId: string,
  messageId: string,
): Promise<MessageFeedback | null> {
  const res = await fetch(
    `/api/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}/feedback`,
    { headers: { Accept: "application/json" } },
  );
  if (res.status === 204) return null;
  if (!res.ok) return null;
  return (await res.json()) as MessageFeedback;
}

export async function deleteFeedback(sessionId: string, messageId: string): Promise<void> {
  await fetch(
    `/api/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}/feedback`,
    { method: "DELETE" },
  );
}

export type StreamHandlers = {
  onToken: (text: string) => void;
  onThinkToken?: (text: string) => void;
  onThinkStart?: () => void;
  onThinkEnd?: (thinkContent: string) => void;
  onDone: (payload: { assistantMessageId: string; sessionId: string }) => void;
  onError: (message: string) => void;
  /** Hermes / NLU：在 token 流开始前下发的意图与槽位 */
  onAnalysis?: (payload: NluAnalysis) => void;
  /** 代理步骤（agent_skill 调用、tool_call 等） */
  onAgentStep?: (step: AgentStep) => void;
};

/** 代理步骤事件（用于展示处理过程中的中间步骤） */
export type AgentStep =
  | { kind: "agent_skill"; label: string; detail: string }
  | { kind: "tool_call"; toolCallId: string; toolName: string; args: string };

export async function streamChat(
  sessionId: string,
  content: string,
  handlers: StreamHandlers,
  thinkingMode: "quick" | "deep" = "quick",
): Promise<void> {
  let sawDone = false;
  let sawError = false;
  const { id: userId, name: userName, displayName } = getStoredUser();
  const res = await fetch(`/api/sessions/${encodeURIComponent(sessionId)}/chat/stream`, {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      "X-User-Id":           encodeURIComponent(userId),
      "X-User-Name":         encodeURIComponent(userName),
      "X-User-Display-Name": encodeURIComponent(displayName),
    },
    body: JSON.stringify({ content, thinkingMode }),
  });

  if (!res.ok || !res.body) {
    const text = await res.text().catch(() => "");
    handlers.onError(text || `HTTP ${res.status}`);
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  const processBlocks = (raw: string) => {
    buffer += raw;
    const parts = buffer.split("\n\n");
    buffer = parts.pop() ?? "";

    for (const block of parts) {
      const lines = block.split("\n").map((l) => l.trimEnd());
      let eventName = "message";
      const dataLines: string[] = [];

      for (const line of lines) {
        if (!line) continue;
        if (line.startsWith("event:")) {
          eventName = line.slice("event:".length).trim();
          continue;
        }
        if (line.startsWith("data:")) {
          dataLines.push(line.slice("data:".length).trim());
        }
      }

      if (dataLines.length === 0) continue;
      const dataText = dataLines.join("\n");

      if (eventName === "token") {
        try {
          const obj = JSON.parse(dataText) as { text?: string };
          if (obj.text) handlers.onToken(obj.text);
        } catch {
          // ignore malformed chunk
        }
        continue;
      }

      if (eventName === "think_token") {
        try {
          const obj = JSON.parse(dataText) as { text?: string };
          if (obj.text && handlers.onThinkToken) handlers.onThinkToken(obj.text);
        } catch {
          // ignore
        }
        continue;
      }

      if (eventName === "think_start") {
        handlers.onThinkStart?.();
        continue;
      }

      if (eventName === "think_end") {
        try {
          const obj = JSON.parse(dataText) as { thinkContent?: string };
          handlers.onThinkEnd?.(obj.thinkContent ?? "");
        } catch {
          handlers.onThinkEnd?.("");
        }
        continue;
      }

      if (eventName === "analysis") {
        if (!handlers.onAnalysis) {
          continue;
        }
        try {
          const obj = JSON.parse(dataText) as Partial<NluAnalysis>;
          if (obj.intent || obj.scenarioCode) {
            handlers.onAnalysis({
              intent: obj.intent ?? obj.scenarioCode ?? "",
              scenarioCode: obj.scenarioCode ?? obj.intent ?? "",
              scenarioName: obj.scenarioName ?? "",
              agentSkill: obj.agentSkill ?? "",
              slots: (obj.slots ?? {}) as Record<string, unknown>,
              missingRequiredSlots: Array.isArray(obj.missingRequiredSlots) ? obj.missingRequiredSlots : [],
              confidence: typeof obj.confidence === "number" ? obj.confidence : 0,
              reasoning: obj.reasoning ?? "",
            });
          }
        } catch {
          // ignore malformed analysis
        }
        continue;
      }

      if (eventName === "agent_step") {
        if (handlers.onAgentStep) {
          try {
            const obj = JSON.parse(dataText) as { type?: string; label?: string; detail?: string };
            if (obj.label) {
              handlers.onAgentStep({ kind: "agent_skill", label: obj.label, detail: obj.detail ?? "" });
            }
          } catch {
            // ignore
          }
        }
        continue;
      }

      if (eventName === "tool_call") {
        if (handlers.onAgentStep) {
          try {
            const obj = JSON.parse(dataText) as { toolCallId?: string; toolName?: string; args?: string };
            if (obj.toolName) {
              handlers.onAgentStep({
                kind: "tool_call",
                toolCallId: obj.toolCallId ?? "",
                toolName: obj.toolName,
                args: obj.args ?? "",
              });
            }
          } catch {
            // ignore
          }
        }
        continue;
      }

      if (eventName === "done") {
        try {
          const obj = JSON.parse(dataText) as { assistantMessageId?: string; sessionId?: string };
          if (obj.assistantMessageId && obj.sessionId) {
            sawDone = true;
            handlers.onDone({ assistantMessageId: obj.assistantMessageId, sessionId: obj.sessionId });
          }
        } catch {
          handlers.onError("无法解析 done 事件");
        }
        continue;
      }

      if (eventName === "error") {
        sawError = true;
        try {
          const obj = JSON.parse(dataText) as { message?: string };
          handlers.onError(obj.message || "流式输出失败");
        } catch {
          handlers.onError(dataText || "流式输出失败");
        }
      }
    }
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      processBlocks(decoder.decode(value, { stream: true }));
    }
    processBlocks(decoder.decode());
    if (buffer.trim()) {
      processBlocks("\n\n");
    }
    if (!sawDone && !sawError) {
      handlers.onError("流已结束，但未收到完成事件（可能是代理中断或后端异常）");
    }
  } catch (e) {
    handlers.onError(e instanceof Error ? e.message : String(e));
  }
}
