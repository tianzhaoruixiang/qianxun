/**
 * EntityCardPanel — 将提取到的实体以卡片网格展示。
 *
 * 六类实体：人物 | 时间 | 地点 | 组织 | 事件 | 物项
 * 特性：折叠/展开、实时搜索、每页 20 条分页（支持 100+）
 */
import { type FC, useState, useMemo } from "react";
import {
  User, Clock, MapPin, Building2, Zap, Package,
  ChevronDown, ChevronUp, Search,
} from "lucide-react";
import { type EntityCard, type EntityCategory } from "../lib/entityExtractor";
import { cn } from "../lib/utils";

const PAGE_SIZE = 20;

/* ── 六类样式 ─────────────────────────────────────────────────────────────── */
const CAT_STYLE: Record<
  EntityCategory,
  { bg: string; border: string; text: string; label: string; Icon: React.FC<{ size?: number; style?: React.CSSProperties }> }
> = {
  person: {
    bg:     "rgba(52,199,89,0.07)",
    border: "rgba(52,199,89,0.22)",
    text:   "#34C759",
    label:  "人物",
    Icon:   ({ size = 12, style }) => <User size={size} style={style} />,
  },
  time: {
    bg:     "rgba(255,159,10,0.07)",
    border: "rgba(255,159,10,0.22)",
    text:   "#FF9F0A",
    label:  "时间",
    Icon:   ({ size = 12, style }) => <Clock size={size} style={style} />,
  },
  location: {
    bg:     "rgba(50,173,230,0.07)",
    border: "rgba(50,173,230,0.22)",
    text:   "#32ADE6",
    label:  "地点",
    Icon:   ({ size = 12, style }) => <MapPin size={size} style={style} />,
  },
  org: {
    bg:     "rgba(0,122,255,0.07)",
    border: "rgba(0,122,255,0.20)",
    text:   "#007AFF",
    label:  "组织",
    Icon:   ({ size = 12, style }) => <Building2 size={size} style={style} />,
  },
  event: {
    bg:     "rgba(255,69,58,0.07)",
    border: "rgba(255,69,58,0.20)",
    text:   "#FF453A",
    label:  "事件",
    Icon:   ({ size = 12, style }) => <Zap size={size} style={style} />,
  },
  thing: {
    bg:     "rgba(175,82,222,0.07)",
    border: "rgba(175,82,222,0.20)",
    text:   "#AF52DE",
    label:  "物项",
    Icon:   ({ size = 12, style }) => <Package size={size} style={style} />,
  },
};

/* ── 头部统计徽标的展示顺序 ──────────────────────────────────────────────── */
const STAT_ORDER: EntityCategory[] = ["person", "org", "event", "location", "thing", "time"];

/* ── 单张卡片 ─────────────────────────────────────────────────────────────── */
const EntityCardItem: FC<{ card: EntityCard }> = ({ card }) => {
  const s = CAT_STYLE[card.category];

  return (
    <div
      className="rounded-[12px] p-3 flex flex-col gap-1.5 animate-fade-in"
      style={{
        background: "#FFFFFF",
        border: "1px solid rgba(0,0,0,0.07)",
        boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
        minHeight: "68px",
      }}
    >
      {/* 名称行 */}
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-1.5 min-w-0">
          <s.Icon size={12} style={{ color: s.text, flexShrink: 0, marginTop: "1px" }} />
          <span
            className="text-[13px] font-semibold truncate"
            style={{ color: "rgba(0,0,0,0.85)" }}
            title={card.name}
          >
            {card.name}
          </span>
        </div>
        {/* 类型标签 */}
        <span
          className="shrink-0 text-[10px] font-medium px-1.5 py-0.5 rounded-full whitespace-nowrap"
          style={{ background: s.bg, border: `1px solid ${s.border}`, color: s.text }}
        >
          {card.type || s.label}
        </span>
      </div>

      {/* 英文名 */}
      {card.nameEn && (
        <span className="text-[11px] truncate" style={{ color: "rgba(0,0,0,0.36)" }}>
          {card.nameEn}
        </span>
      )}

      {/* 描述 */}
      {card.description && (
        <p
          className="text-[11.5px] leading-relaxed"
          style={{
            color: "rgba(0,0,0,0.50)",
            display: "-webkit-box",
            WebkitLineClamp: 2,
            WebkitBoxOrient: "vertical",
            overflow: "hidden",
          }}
          title={card.description}
        >
          {card.description}
        </p>
      )}
    </div>
  );
};

/* ── 面板主体 ─────────────────────────────────────────────────────────────── */
interface EntityCardPanelProps {
  cards: EntityCard[];
  /** 最少达到多少条才显示面板，默认 3 */
  threshold?: number;
}

export const EntityCardPanel: FC<EntityCardPanelProps> = ({ cards, threshold = 3 }) => {
  const [collapsed, setCollapsed] = useState(false);
  const [query, setQuery]         = useState("");
  const [page, setPage]           = useState(0);
  const [activeFilter, setActiveFilter] = useState<EntityCategory | null>(null);

  // 按类别统计（头部显示）
  const catCounts = useMemo(() => {
    const m: Partial<Record<EntityCategory, number>> = {};
    for (const c of cards) m[c.category] = (m[c.category] ?? 0) + 1;
    return m;
  }, [cards]);

  // 过滤：类别 + 搜索关键词
  const filtered = useMemo(() => {
    let list = activeFilter ? cards.filter((c) => c.category === activeFilter) : cards;
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      list = list.filter(
        (c) =>
          c.name.toLowerCase().includes(q) ||
          (c.nameEn?.toLowerCase().includes(q) ?? false) ||
          (c.type?.toLowerCase().includes(q) ?? false) ||
          (c.description?.toLowerCase().includes(q) ?? false),
      );
    }
    return list;
  }, [cards, query, activeFilter]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const pageCards  = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  if (cards.length < threshold) return null;

  const handleFilter = (cat: EntityCategory) => {
    setActiveFilter((prev) => (prev === cat ? null : cat));
    setPage(0);
  };

  return (
    <div
      className="mt-3 rounded-[14px] overflow-hidden"
      style={{ background: "rgba(248,248,252,0.90)", border: "1px solid rgba(0,0,0,0.08)" }}
    >
      {/* ── 头部 ─────────────────────────────────────────────────────────── */}
      <div
        className="flex items-center gap-2 px-3 py-2.5 flex-wrap"
        style={{ borderBottom: collapsed ? "none" : "1px solid rgba(0,0,0,0.07)" }}
      >
        {/* 折叠按钮 */}
        <button
          type="button"
          onClick={() => setCollapsed((c) => !c)}
          className="shrink-0"
          title={collapsed ? "展开" : "收起"}
        >
          {collapsed
            ? <ChevronDown size={13} style={{ color: "rgba(0,0,0,0.40)" }} />
            : <ChevronUp   size={13} style={{ color: "rgba(0,0,0,0.40)" }} />}
        </button>

        {/* 总数 */}
        <span className="text-[11.5px] font-medium shrink-0" style={{ color: "rgba(0,0,0,0.55)" }}>
          提取到{" "}
          <span style={{ color: "rgba(0,0,0,0.80)" }}>{cards.length}</span>{" "}
          个实体
        </span>

        {/* 类别徽标（可点击过滤） */}
        <div className="flex items-center gap-1 flex-wrap">
          {STAT_ORDER.filter((cat) => (catCounts[cat] ?? 0) > 0).map((cat) => {
            const s = CAT_STYLE[cat];
            const active = activeFilter === cat;
            return (
              <button
                key={cat}
                type="button"
                onClick={() => handleFilter(cat)}
                className="text-[10px] px-1.5 py-px rounded-full transition-all"
                style={{
                  background: active ? s.text : s.bg,
                  color: active ? "#fff" : s.text,
                  border: `1px solid ${s.border}`,
                  fontWeight: active ? 600 : 400,
                }}
              >
                {s.label} {catCounts[cat]}
              </button>
            );
          })}
        </div>

        {/* 弹性占位 */}
        <div className="flex-1 min-w-0" />

        {/* 搜索框 */}
        {!collapsed && (
          <div className="flex items-center gap-1.5 shrink-0">
            <Search size={11} style={{ color: "rgba(0,0,0,0.30)" }} />
            <input
              type="text"
              value={query}
              onChange={(e) => { setQuery(e.target.value); setPage(0); }}
              placeholder="搜索…"
              className="text-[11.5px] outline-none bg-transparent"
              style={{ width: "90px", color: "rgba(0,0,0,0.70)", caretColor: "#007AFF" }}
            />
            {query && (
              <button
                type="button"
                onClick={() => { setQuery(""); setPage(0); }}
                className="text-[10px]"
                style={{ color: "rgba(0,0,0,0.30)" }}
              >
                ✕
              </button>
            )}
          </div>
        )}
      </div>

      {/* ── 卡片网格 ─────────────────────────────────────────────────────── */}
      {!collapsed && (
        <div className="p-3">
          {pageCards.length === 0 ? (
            <p className="text-[12px] text-center py-4" style={{ color: "rgba(0,0,0,0.35)" }}>
              未找到匹配的实体
            </p>
          ) : (
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))",
                gap: "8px",
              }}
            >
              {pageCards.map((card) => <EntityCardItem key={card.id} card={card} />)}
            </div>
          )}

          {/* ── 分页 ─────────────────────────────────────────────────── */}
          {totalPages > 1 && (
            <div
              className={cn("flex items-center justify-between mt-3 pt-2.5")}
              style={{ borderTop: "1px solid rgba(0,0,0,0.07)" }}
            >
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => p - 1)}
                className="px-3 py-1 rounded-[8px] text-[11.5px] font-medium transition-all disabled:opacity-30"
                style={{
                  background: page === 0 ? "transparent" : "rgba(0,122,255,0.07)",
                  color: "#007AFF", border: "1px solid rgba(0,122,255,0.18)",
                }}
              >
                上一页
              </button>
              <span className="text-[11px]" style={{ color: "rgba(0,0,0,0.40)" }}>
                第 {page + 1} / {totalPages} 页
                <span className="ml-1.5" style={{ color: "rgba(0,0,0,0.28)" }}>
                  （共 {filtered.length} 条）
                </span>
              </span>
              <button
                type="button"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="px-3 py-1 rounded-[8px] text-[11.5px] font-medium transition-all disabled:opacity-30"
                style={{
                  background: page >= totalPages - 1 ? "transparent" : "rgba(0,122,255,0.07)",
                  color: "#007AFF", border: "1px solid rgba(0,122,255,0.18)",
                }}
              >
                下一页
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
