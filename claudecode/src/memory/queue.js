/**
 * 进程内记忆写入队列（Phase 2）。
 * 请求线程只入队；后台串行消费，失败有限次重试，不阻断对话。
 */

/**
 * @typedef {{
 *   id: string,
 *   attempts: number,
 *   maxAttempts: number,
 *   run: () => Promise<void>,
 *   label?: string,
 * }} MemoryJob
 */

/**
 * @param {{
 *   concurrency?: number,
 *   maxAttempts?: number,
 *   retryDelayMs?: number,
 *   logger?: { warn?: Function, info?: Function },
 * }} [opts]
 */
export function createMemoryQueue(opts = {}) {
  const concurrency = Math.max(1, Number(opts.concurrency) || 1);
  const defaultMaxAttempts = Math.max(1, Number(opts.maxAttempts) || 3);
  const retryDelayMs = Math.max(0, Number(opts.retryDelayMs) || 750);
  const logger = opts.logger || console;

  /** @type {MemoryJob[]} */
  const pending = [];
  let active = 0;
  let seq = 0;
  const recentIds = new Map(); // id -> ts，简单幂等
  const RECENT_TTL_MS = 10 * 60 * 1000;

  function pruneRecent(now = Date.now()) {
    for (const [k, ts] of recentIds) {
      if (now - ts > RECENT_TTL_MS) {
        recentIds.delete(k);
      }
    }
  }

  /**
   * @param {{ id?: string, run: () => Promise<void>, label?: string, maxAttempts?: number }} job
   * @returns {{ enqueued: boolean, reason?: string, id: string }}
   */
  function enqueue(job) {
    if (!job || typeof job.run !== "function") {
      return { enqueued: false, reason: "invalid_job", id: "" };
    }
    pruneRecent();
    const id = String(job.id || `memjob-${++seq}`);
    if (recentIds.has(id)) {
      return { enqueued: false, reason: "duplicate", id };
    }
    recentIds.set(id, Date.now());
    pending.push({
      id,
      attempts: 0,
      maxAttempts: Math.max(1, Number(job.maxAttempts) || defaultMaxAttempts),
      run: job.run,
      label: job.label || id,
    });
    pump();
    return { enqueued: true, id };
  }

  function pump() {
    while (active < concurrency && pending.length) {
      const job = pending.shift();
      active += 1;
      void runOne(job).finally(() => {
        active -= 1;
        pump();
      });
    }
  }

  async function runOne(job) {
    job.attempts += 1;
    try {
      await job.run();
      logger.info?.(`[memory] write ok id=${job.id} label=${job.label} attempt=${job.attempts}`);
    } catch (err) {
      const msg = err?.message || String(err);
      if (job.attempts < job.maxAttempts) {
        logger.warn?.(
          `[memory] write retry id=${job.id} label=${job.label} attempt=${job.attempts}/${job.maxAttempts}: ${msg}`,
        );
        if (retryDelayMs > 0) {
          await sleep(retryDelayMs * job.attempts);
        }
        pending.push(job);
      } else {
        logger.warn?.(
          `[memory] write dead-letter id=${job.id} label=${job.label} attempts=${job.attempts}: ${msg}`,
        );
      }
    }
  }

  return {
    enqueue,
    /** 测试用 */
    _size: () => pending.length + active,
    _pending: () => pending.length,
    _active: () => active,
    _reset() {
      pending.length = 0;
      active = 0;
      recentIds.clear();
    },
  };
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 单例队列，供 persist 默认使用 */
export const defaultMemoryQueue = createMemoryQueue();
