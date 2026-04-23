/**
 * 实体卡片：数据来自助手约定的结构化围栏（由后端解析入库），
 * 不再使用正文关键词启发式提取。
 */

import { QIANXUN_ENTITIES_FENCE } from "./qianxunConstants";

export type EntityCategory = "person" | "time" | "location" | "org" | "event" | "thing";

export interface EntityCard {
  id: string;
  name: string;
  nameEn?: string;
  type?: string;
  description?: string;
  category: EntityCategory;
}

const ALLOWED = new Set<EntityCategory>(["person", "time", "location", "org", "event", "thing"]);

const ENTITY_FENCE = "```" + QIANXUN_ENTITIES_FENCE;

/** 流式展示时隐藏尚未完成或已完成的实体围栏，避免用户看到机器块 */
export function stripEntityBlockForDisplay(text: string): string {
  if (!text) return "";
  const lower = text.toLowerCase();
  const idx = lower.lastIndexOf(ENTITY_FENCE.toLowerCase());
  if (idx < 0) return text;
  return text.slice(0, idx).trimEnd();
}

/** 解析模型 / 后端下发的 JSON 数组 */
export function parseStructuredEntityCards(data: unknown): EntityCard[] {
  if (!Array.isArray(data)) return [];
  const out: EntityCard[] = [];
  let i = 0;
  for (const el of data) {
    if (!el || typeof el !== "object") continue;
    const o = el as Record<string, unknown>;
    const name = String(o.name ?? "").trim();
    if (!name) continue;
    let cat = String(o.category ?? "thing").toLowerCase() as EntityCategory;
    if (!ALLOWED.has(cat)) cat = "thing";
    const nameEn = o.nameEn != null && String(o.nameEn).trim() ? String(o.nameEn).trim() : undefined;
    const type = o.type != null && String(o.type).trim() ? String(o.type).trim() : undefined;
    const descRaw = o.description != null ? String(o.description).trim() : "";
    out.push({
      id: `agent-${i++}-${name.slice(0, 24)}`,
      name,
      category: cat,
      nameEn,
      type,
      description: descRaw ? descRaw.slice(0, 180) : undefined,
    });
  }
  return out;
}
