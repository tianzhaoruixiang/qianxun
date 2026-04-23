/**
 * ClarificationCard — 意图澄清卡片
 *
 * 当 NLU 置信度 < 50% 时，后端下发 `clarification` 事件，
 * 前端渲染此组件供用户从候选场景中选择意图方向。
 */
import { type FC } from "react";
import { HelpCircle, ChevronRight } from "lucide-react";
import { type ClarificationOption } from "../api";

interface Props {
  question: string;
  confidence: number;
  options: ClarificationOption[];
  onSelect: (code: string, name: string) => void;
}

export const ClarificationCard: FC<Props> = ({ question, confidence, options, onSelect }) => {
  const pct = Math.round(confidence * 100);

  return (
    <div
      className="mt-3 rounded-[14px] overflow-hidden animate-fade-in"
      style={{
        background: "rgba(255,245,235,0.85)",
        border: "1px solid rgba(255,149,0,0.22)",
      }}
    >
      {/* 头部 */}
      <div
        className="flex items-start gap-2.5 px-4 py-3"
        style={{ borderBottom: "1px solid rgba(255,149,0,0.14)" }}
      >
        <HelpCircle
          size={15}
          className="shrink-0 mt-px"
          style={{ color: "#FF9F0A" }}
        />
        <div className="flex-1 min-w-0">
          <p className="text-[13px] font-medium leading-snug" style={{ color: "rgba(0,0,0,0.80)" }}>
            {question}
          </p>
          <p className="text-[11px] mt-0.5" style={{ color: "rgba(0,0,0,0.38)" }}>
            意图置信度 {pct}%，请帮我明确一下您的调研方向
          </p>
        </div>
      </div>

      {/* 选项列表 */}
      <div className="px-3 py-2.5 flex flex-col gap-1.5">
        {options.map((opt) => (
          <button
            key={opt.code}
            type="button"
            onClick={() => onSelect(opt.code, opt.name)}
            className="w-full flex items-center gap-2.5 px-3 py-2.5 rounded-[10px] text-left transition-all duration-150 group"
            style={{
              background: opt.detected
                ? "rgba(255,149,0,0.10)"
                : "rgba(255,255,255,0.70)",
              border: opt.detected
                ? "1px solid rgba(255,149,0,0.28)"
                : "1px solid rgba(0,0,0,0.07)",
            }}
            onMouseEnter={(e) => {
              (e.currentTarget as HTMLElement).style.background = opt.detected
                ? "rgba(255,149,0,0.16)"
                : "rgba(0,122,255,0.06)";
              (e.currentTarget as HTMLElement).style.borderColor = opt.detected
                ? "rgba(255,149,0,0.40)"
                : "rgba(0,122,255,0.20)";
            }}
            onMouseLeave={(e) => {
              (e.currentTarget as HTMLElement).style.background = opt.detected
                ? "rgba(255,149,0,0.10)"
                : "rgba(255,255,255,0.70)";
              (e.currentTarget as HTMLElement).style.borderColor = opt.detected
                ? "rgba(255,149,0,0.28)"
                : "rgba(0,0,0,0.07)";
            }}
          >
            {/* 名称 + 候选标记 */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-[13px] font-semibold" style={{ color: "rgba(0,0,0,0.82)" }}>
                  {opt.name}
                </span>
                {opt.detected && (
                  <span
                    className="text-[10px] px-1.5 py-px rounded-full font-medium shrink-0"
                    style={{
                      background: "rgba(255,149,0,0.15)",
                      color: "#FF9F0A",
                      border: "1px solid rgba(255,149,0,0.30)",
                    }}
                  >
                    系统推测
                  </span>
                )}
              </div>
              {opt.description && (
                <p
                  className="text-[11.5px] mt-0.5 truncate"
                  style={{ color: "rgba(0,0,0,0.42)" }}
                  title={opt.description}
                >
                  {opt.description.length > 60 ? opt.description.slice(0, 60) + "…" : opt.description}
                </p>
              )}
            </div>

            <ChevronRight
              size={13}
              className="shrink-0 transition-transform duration-150 group-hover:translate-x-0.5"
              style={{ color: "rgba(0,0,0,0.25)" }}
            />
          </button>
        ))}
      </div>
    </div>
  );
};
