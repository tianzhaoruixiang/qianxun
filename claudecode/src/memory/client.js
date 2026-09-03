/**
 * Mem0 HTTP 客户端。
 * - Phase 1：search
 * - Phase 2：add（异步固化）
 * - platform：/v1/memories/search/、/v1/memories/ + Authorization: Token
 * - oss：/search、/memories（本地 compose），可选 X-API-Key
 */

/**
 * @param {object} config - loadMemoryConfig() 结果
 * @param {{ fetchImpl?: typeof fetch }} [deps]
 */
export function createMemoryClient(config, deps = {}) {
  const fetchImpl = deps.fetchImpl || globalThis.fetch;
  const mode = config?.mode === "oss" ? "oss" : "platform";
  if (!config?.enabled) {
    return {
      enabled: false,
      mode,
      async search() {
        return [];
      },
      async add() {
        return null;
      },
    };
  }
  if (mode === "platform" && !config?.apiKey) {
    return {
      enabled: false,
      mode,
      async search() {
        return [];
      },
      async add() {
        return null;
      },
    };
  }

  return {
    enabled: true,
    mode,
    /**
     * @param {string} query
     * @param {{ filters: object, topK?: number }} opts
     * @returns {Promise<Array<{ id?: string, memory: string, score?: number }>>}
     */
    async search(query, opts = {}) {
      const q = String(query || "").trim();
      if (!q || !opts.filters) {
        return [];
      }
      const topK = opts.topK || config.topK || 5;
      const filters = { ...opts.filters };
      if (!config.includeAppId) {
        delete filters.app_id;
      }

      const url = mode === "oss"
        ? `${config.baseUrl}/search`
        : `${config.baseUrl}/v1/memories/search/`;

      const headers = authHeaders(mode, config.apiKey);
      const body = mode === "oss"
        ? {
          query: q,
          filters,
          top_k: topK,
          user_id: filters.user_id,
          agent_id: filters.agent_id,
          run_id: filters.run_id,
        }
        : {
          query: q,
          filters,
          top_k: topK,
        };

      const data = await requestJson(fetchImpl, url, {
        method: "POST",
        headers,
        body,
        timeoutMs: config.timeoutMs || 250,
      });
      return normalizeSearchResults(data);
    },

    /**
     * @param {Array<{ role: string, content: string }>} messages
     * @param {{ user_id?: string, agent_id?: string, run_id?: string, metadata?: object }} params
     */
    async add(messages, params = {}) {
      const list = Array.isArray(messages)
        ? messages
          .map((m) => ({
            role: String(m?.role || "").trim(),
            content: String(m?.content || "").trim(),
          }))
          .filter((m) => m.role && m.content)
        : [];
      if (!list.length) {
        return null;
      }
      if (!params.user_id && !params.agent_id && !params.run_id) {
        throw new Error("mem0 add requires user_id, agent_id, or run_id");
      }

      const url = mode === "oss"
        ? `${config.baseUrl}/memories`
        : `${config.baseUrl}/v1/memories/`;

      const headers = authHeaders(mode, config.apiKey);
      const body = {
        messages: list,
        user_id: params.user_id || undefined,
        agent_id: params.agent_id || undefined,
        run_id: params.run_id || undefined,
        metadata: params.metadata || undefined,
      };

      return requestJson(fetchImpl, url, {
        method: "POST",
        headers,
        body,
        timeoutMs: config.writeTimeoutMs || 30_000,
      });
    },
  };
}

function authHeaders(mode, apiKey) {
  const headers = { "Content-Type": "application/json" };
  if (mode === "platform") {
    headers.Authorization = `Token ${apiKey}`;
  } else if (apiKey && apiKey !== "oss-local") {
    headers["X-API-Key"] = apiKey;
  }
  return headers;
}

async function requestJson(fetchImpl, url, { method, headers, body, timeoutMs }) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetchImpl(url, {
      method,
      headers,
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`mem0 ${method} ${url} HTTP ${res.status}: ${text.slice(0, 200)}`);
    }
    if (res.status === 204) {
      return null;
    }
    const ct = String(res.headers?.get?.("content-type") || "");
    if (ct.includes("application/json") || typeof res.json === "function") {
      try {
        return await res.json();
      } catch {
        return null;
      }
    }
    return null;
  } finally {
    clearTimeout(timer);
  }
}

export function normalizeSearchResults(data) {
  const raw = Array.isArray(data)
    ? data
    : (Array.isArray(data?.results) ? data.results : (Array.isArray(data?.memories) ? data.memories : []));
  return raw.map((item) => ({
    id: item?.id,
    memory: String(item?.memory ?? item?.text ?? item?.data?.memory ?? "").trim(),
    score: Number(item?.score) || 0,
  })).filter((x) => x.memory);
}
