import assert from "node:assert/strict";
import { test } from "node:test";
import { loadMemoryConfig, PREFS_AGENT_ID } from "./config.js";
import { normalizeSearchResults } from "./client.js";
import { formatMemoryAppend, mergeMemoryHits } from "./format.js";
import { buildLaneFilters, resolveMemoryScope } from "./scope.js";
import { memoryRecall } from "./recall.js";

test("loadMemoryConfig respects flag and body override", () => {
  const off = loadMemoryConfig({}, { QIANXUN_MEM0_ENABLED: "false", MEM0_API_KEY: "k" });
  assert.equal(off.enabled, false);

  const on = loadMemoryConfig({}, { QIANXUN_MEM0_ENABLED: "1", MEM0_API_KEY: "k" });
  assert.equal(on.enabled, true);
  assert.equal(on.mode, "platform");
  assert.equal(on.appId, "qianxun");
  assert.equal(on.timeoutMs, 250);

  const bodyOff = loadMemoryConfig(
    { memoryEnabled: false },
    { QIANXUN_MEM0_ENABLED: "1", MEM0_API_KEY: "k" },
  );
  assert.equal(bodyOff.enabled, false);

  const bodyOn = loadMemoryConfig(
    { memoryEnabled: true },
    { QIANXUN_MEM0_ENABLED: "0", MEM0_API_KEY: "secret" },
  );
  assert.equal(bodyOn.enabled, true);
});

test("loadMemoryConfig oss mode defaults to local mem0", () => {
  const cfg = loadMemoryConfig({}, {
    QIANXUN_MEM0_ENABLED: "1",
    QIANXUN_MEM0_MODE: "oss",
  });
  assert.equal(cfg.enabled, true);
  assert.equal(cfg.mode, "oss");
  assert.equal(cfg.baseUrl, "http://mem0:8000");
  assert.equal(cfg.includeAppId, false);
});

test("createMemoryClient oss posts to /search", async () => {
  const { createMemoryClient } = await import("./client.js");
  let seen;
  const client = createMemoryClient({
    enabled: true,
    mode: "oss",
    apiKey: "oss-local",
    baseUrl: "http://mem0:8000",
    includeAppId: false,
    timeoutMs: 500,
    topK: 3,
  }, {
    fetchImpl: async (url, init) => {
      seen = { url, body: JSON.parse(init.body), headers: init.headers };
      return {
        ok: true,
        async json() {
          return { results: [{ memory: "本地事实", score: 0.8 }] };
        },
      };
    },
  });
  const hits = await client.search("hello", {
    filters: { user_id: "u1", agent_id: "user_prefs", app_id: "qianxun" },
  });
  assert.equal(seen.url, "http://mem0:8000/search");
  assert.equal(seen.body.query, "hello");
  assert.equal(seen.body.filters.app_id, undefined);
  assert.equal(seen.headers.Authorization, undefined);
  assert.deepEqual(hits, [{ id: undefined, memory: "本地事实", score: 0.8 }]);
});

test("resolveMemoryScope skips agent lane for officer", () => {
  const scope = resolveMemoryScope({
    userId: "u1",
    body: { agentInstanceId: "billing" },
    officer: true,
    appId: "qianxun",
  });
  assert.equal(scope.includePrefsLane, true);
  assert.equal(scope.includeAgentLane, false);
  assert.equal(scope.agentId, "");
  assert.equal(scope.prefsAgentId, PREFS_AGENT_ID);
});

test("resolveMemoryScope uses agentInstanceId for pro agents", () => {
  const scope = resolveMemoryScope({
    userId: "u1",
    body: { agentInstanceId: "code-agent" },
    officer: false,
  });
  assert.equal(scope.includeAgentLane, true);
  assert.equal(scope.agentId, "code-agent");
  assert.deepEqual(buildLaneFilters(scope, "prefs"), {
    user_id: "u1",
    app_id: "qianxun",
    agent_id: PREFS_AGENT_ID,
  });
  assert.deepEqual(buildLaneFilters(scope, "agent"), {
    user_id: "u1",
    app_id: "qianxun",
    agent_id: "code-agent",
  });
});

test("formatMemoryAppend dedupes and caps length", () => {
  const text = formatMemoryAppend([
    { memory: "喜欢 TypeScript", lane: "prefs" },
    { memory: "喜欢 TypeScript", lane: "agent" },
    { memory: "用 Go 写服务", lane: "agent" },
  ], { maxChars: 4_000 });
  assert.match(text, /【长期记忆】/);
  assert.match(text, /喜欢 TypeScript/);
  assert.match(text, /用 Go 写服务/);
  assert.equal((text.match(/喜欢 TypeScript/g) || []).length, 1);

  const long = formatMemoryAppend(
    [{ memory: "x".repeat(200) }, { memory: "y".repeat(200) }],
    { maxChars: 80 },
  );
  assert.ok(long.length <= 80);
});

test("mergeMemoryHits sorts by score and respects topK", () => {
  const merged = mergeMemoryHits(
    [{ memory: "a", score: 0.2 }],
    [{ memory: "b", score: 0.9 }, { memory: "a", score: 0.8 }],
    { topK: 2 },
  );
  assert.equal(merged.length, 2);
  assert.equal(merged[0].memory, "b");
  assert.equal(merged[0].lane, "agent");
  assert.equal(merged[1].memory, "a");
});

test("normalizeSearchResults accepts results wrapper", () => {
  const hits = normalizeSearchResults({
    results: [{ id: "1", memory: "事实", score: 0.5 }],
  });
  assert.deepEqual(hits, [{ id: "1", memory: "事实", score: 0.5 }]);
});

test("memoryRecall injects append via dual-lane client", async () => {
  const calls = [];
  const client = {
    enabled: true,
    async search(query, opts) {
      calls.push({ query, filters: opts.filters });
      if (opts.filters.agent_id === PREFS_AGENT_ID) {
        return [{ memory: "偏好深色主题", score: 0.7 }];
      }
      return [{ memory: "账单用 CSV 导出", score: 0.9 }];
    },
  };
  const result = await memoryRecall({
    userId: "u1",
    prompt: "导出账单",
    body: { agentInstanceId: "billing", memoryEnabled: true },
    officer: false,
    config: {
      enabled: true,
      apiKey: "k",
      baseUrl: "http://example",
      appId: "qianxun",
      timeoutMs: 250,
      topK: 5,
      maxChars: 4_000,
      prefsAgentId: PREFS_AGENT_ID,
      includeAppId: true,
    },
    client,
  });
  assert.equal(calls.length, 2);
  assert.match(result.append, /账单用 CSV 导出/);
  assert.match(result.append, /偏好深色主题/);
  assert.equal(result.degraded, false);
  assert.equal(result.reason, "ok");
});

test("memoryRecall officer only hits prefs lane", async () => {
  const calls = [];
  const client = {
    async search(_q, opts) {
      calls.push(opts.filters.agent_id);
      return [{ memory: "偏好简体中文", score: 1 }];
    },
  };
  const result = await memoryRecall({
    userId: "u1",
    prompt: "帮我调度",
    body: { agentInstanceId: "billing" },
    officer: true,
    config: {
      enabled: true,
      apiKey: "k",
      baseUrl: "http://example",
      appId: "qianxun",
      timeoutMs: 250,
      topK: 5,
      maxChars: 4_000,
      prefsAgentId: PREFS_AGENT_ID,
    },
    client,
  });
  assert.deepEqual(calls, [PREFS_AGENT_ID]);
  assert.match(result.append, /偏好简体中文/);
});

test("memoryRecall degrades on client failure", async () => {
  const client = {
    async search() {
      throw new Error("boom");
    },
  };
  const result = await memoryRecall({
    userId: "u1",
    prompt: "hello",
    body: {},
    officer: false,
    config: {
      enabled: true,
      apiKey: "k",
      baseUrl: "http://example",
      appId: "qianxun",
      timeoutMs: 250,
      topK: 5,
      maxChars: 4_000,
      prefsAgentId: PREFS_AGENT_ID,
    },
    client,
  });
  assert.equal(result.append, "");
  assert.equal(result.degraded, true);
});

test("redactSecrets masks keys emails and phones", async () => {
  const { redactSecrets } = await import("./redact.js");
  const out = redactSecrets("key=sk-abcdefghijklmnopqrstuvwxyz phone=13812345678 mail=a@b.com");
  assert.match(out, /\[REDACTED\]/);
  assert.doesNotMatch(out, /sk-abcdefghijklmnopqrstuvwxyz/);
  assert.doesNotMatch(out, /13812345678/);
  assert.doesNotMatch(out, /a@b\.com/);
});

test("accumulateAssistantText prefers stream over result", async () => {
  const { accumulateAssistantText } = await import("./extract.js");
  const state = { text: "", fromStream: false };
  accumulateAssistantText(state, {
    type: "stream_event",
    event: { delta: { type: "text_delta", text: "你好" } },
  });
  accumulateAssistantText(state, {
    type: "assistant",
    message: { content: [{ type: "text", text: "世界" }] },
  });
  accumulateAssistantText(state, {
    type: "result",
    result: "整轮摘要不应覆盖",
  });
  assert.equal(state.text, "你好世界");
});

test("createMemoryClient oss posts to /memories on add", async () => {
  const { createMemoryClient } = await import("./client.js");
  let seen;
  const client = createMemoryClient({
    enabled: true,
    mode: "oss",
    apiKey: "oss-local",
    baseUrl: "http://mem0:8000",
    writeTimeoutMs: 5_000,
  }, {
    fetchImpl: async (url, init) => {
      seen = { url, body: JSON.parse(init.body), method: init.method };
      return {
        ok: true,
        headers: { get: () => "application/json" },
        async json() {
          return { results: [] };
        },
      };
    },
  });
  await client.add(
    [{ role: "user", content: "我喜欢深色主题" }, { role: "assistant", content: "已记下" }],
    { user_id: "u1", agent_id: PREFS_AGENT_ID, metadata: { lane: "prefs" } },
  );
  assert.equal(seen.url, "http://mem0:8000/memories");
  assert.equal(seen.method, "POST");
  assert.equal(seen.body.user_id, "u1");
  assert.equal(seen.body.agent_id, PREFS_AGENT_ID);
  assert.equal(seen.body.messages.length, 2);
});

test("memoryPersist enqueues prefs lane and calls add", async () => {
  const { memoryPersist } = await import("./persist.js");
  const { createMemoryQueue } = await import("./queue.js");
  const adds = [];
  const queue = createMemoryQueue({ retryDelayMs: 0 });
  const client = {
    enabled: true,
    async add(messages, params) {
      adds.push({ messages, params });
    },
  };
  const result = await memoryPersist({
    userId: "u1",
    prompt: "我喜欢用 TypeScript 严格模式",
    assistantText: "好的，已记住你的偏好。",
    body: {},
    officer: true,
    ok: true,
    sessionId: "sess-1",
    config: {
      enabled: true,
      writeEnabled: true,
      apiKey: "k",
      baseUrl: "http://example",
      appId: "qianxun",
      prefsAgentId: PREFS_AGENT_ID,
      includeAppId: true,
      writeMaxChars: 4_000,
      writeMaxAttempts: 2,
    },
    client,
    queue,
  });
  assert.equal(result.enqueued, true);
  assert.equal(result.jobs, 1);
  // 等待队列消费
  for (let i = 0; i < 40 && queue._size() > 0; i += 1) {
    await new Promise((r) => setTimeout(r, 10));
  }
  assert.equal(adds.length, 1);
  assert.equal(adds[0].params.agent_id, PREFS_AGENT_ID);
  assert.equal(adds[0].params.user_id, "u1");
  assert.equal(adds[0].messages[0].role, "user");
});

test("memoryPersist dual-lane for pro agent", async () => {
  const { memoryPersist } = await import("./persist.js");
  const { createMemoryQueue } = await import("./queue.js");
  const adds = [];
  const queue = createMemoryQueue({ retryDelayMs: 0 });
  const client = {
    enabled: true,
    async add(_messages, params) {
      adds.push(params.agent_id);
    },
  };
  const result = await memoryPersist({
    userId: "u1",
    prompt: "账单导出用 CSV",
    assistantText: "好的，以后账单默认 CSV。",
    body: { agentInstanceId: "billing" },
    officer: false,
    ok: true,
    sessionId: "sess-2",
    config: {
      enabled: true,
      writeEnabled: true,
      apiKey: "k",
      baseUrl: "http://example",
      appId: "qianxun",
      prefsAgentId: PREFS_AGENT_ID,
      includeAppId: false,
      writeMaxChars: 4_000,
      writeMaxAttempts: 1,
    },
    client,
    queue,
  });
  assert.equal(result.jobs, 2);
  for (let i = 0; i < 40 && queue._size() > 0; i += 1) {
    await new Promise((r) => setTimeout(r, 10));
  }
  assert.deepEqual(adds.sort(), ["billing", PREFS_AGENT_ID].sort());
});

test("memoryPersist skips when write disabled or empty assistant", async () => {
  const { memoryPersist } = await import("./persist.js");
  const queue = { enqueue() { throw new Error("should not enqueue"); } };
  const disabled = await memoryPersist({
    userId: "u1",
    prompt: "hi",
    assistantText: "hello",
    ok: true,
    config: { enabled: true, writeEnabled: false },
    queue,
  });
  assert.equal(disabled.reason, "disabled");

  const empty = await memoryPersist({
    userId: "u1",
    prompt: "hi",
    assistantText: "",
    ok: true,
    config: { enabled: true, writeEnabled: true, prefsAgentId: PREFS_AGENT_ID },
    queue,
  });
  assert.equal(empty.reason, "empty_assistant");
});

test("loadMemoryConfig writeEnabled follows flag", () => {
  const on = loadMemoryConfig({}, {
    QIANXUN_MEM0_ENABLED: "1",
    QIANXUN_MEM0_MODE: "oss",
  });
  assert.equal(on.writeEnabled, true);
  assert.equal(on.writeTimeoutMs, 30_000);

  const writeOff = loadMemoryConfig({}, {
    QIANXUN_MEM0_ENABLED: "1",
    QIANXUN_MEM0_MODE: "oss",
    QIANXUN_MEM0_WRITE_ENABLED: "false",
  });
  assert.equal(writeOff.enabled, true);
  assert.equal(writeOff.writeEnabled, false);

  const bodyOff = loadMemoryConfig(
    { memoryWriteEnabled: false },
    { QIANXUN_MEM0_ENABLED: "1", QIANXUN_MEM0_MODE: "oss" },
  );
  assert.equal(bodyOff.writeEnabled, false);
});
