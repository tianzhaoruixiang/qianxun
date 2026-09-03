/**
 * Mem0 HTTP 客户端（Phase 1 仅 search）。
 * - platform：POST /v1/memories/search/ + Authorization: Token
 * - oss：POST /search（本地 compose），可选 X-API-Key
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
    };
  }
  if (mode === "platform" && !config?.apiKey) {
    return {
      enabled: false,
      mode,
      async search() {
        return [];
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

      const headers = { "Content-Type": "application/json" };
      if (mode === "platform") {
        headers.Authorization = `Token ${config.apiKey}`;
      } else if (config.apiKey && config.apiKey !== "oss-local") {
        headers["X-API-Key"] = config.apiKey;
      }

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

      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), config.timeoutMs || 250);
      try {
        const res = await fetchImpl(url, {
          method: "POST",
          headers,
          body: JSON.stringify(body),
          signal: controller.signal,
        });
        if (!res.ok) {
          const text = await res.text().catch(() => "");
          throw new Error(`mem0 search HTTP ${res.status}: ${text.slice(0, 200)}`);
        }
        const data = await res.json();
        return normalizeSearchResults(data);
      } finally {
        clearTimeout(timer);
      }
    },
  };
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
