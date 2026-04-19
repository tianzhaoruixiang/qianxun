import { type FC } from "react";
import { SparklesIcon } from "lucide-react";

interface WelcomeScreenProps {
  onNewChat: () => void;
}

const suggestions = [
  { q: "帮我调研一家上市公司的基本情况", tag: "研究" },
  { q: "分析某位关键人物的背景与履历", tag: "分析" },
  { q: "查询最新的行业市场趋势报告", tag: "趋势" },
  { q: "对比分析两家公司的财务数据", tag: "对比" },
];

export const WelcomeScreen: FC<WelcomeScreenProps> = ({ onNewChat }) => {
  return (
    <div className="flex-1 flex flex-col items-center justify-center px-6 py-16 select-none">
      {/* App icon */}
      <div
        className="w-16 h-16 rounded-[22px] flex items-center justify-center mb-5"
        style={{
          background: "linear-gradient(145deg, #007AFF 0%, #5856D6 100%)",
          boxShadow: "0 8px 28px rgba(0,122,255,0.24), 0 2px 8px rgba(0,122,255,0.14)",
        }}
      >
        <SparklesIcon size={28} className="text-white" strokeWidth={1.8} />
      </div>

      <h1
        className="text-[26px] font-semibold tracking-tight mb-2"
        style={{ color: "rgba(0,0,0,0.88)", letterSpacing: "-0.03em" }}
      >
        你好，我是<span style={{ color: "#007AFF" }}>千寻</span>
      </h1>
      <p
        className="text-[14px] text-center max-w-sm mb-10 leading-relaxed"
        style={{ color: "rgba(0,0,0,0.45)" }}
      >
        基于智能体的数据分析问答助手，帮你寻找高价值的数据洞察
      </p>

      {/* Suggestion chips */}
      <div className="w-full max-w-lg space-y-2">
        <p className="text-[11.5px] mb-3 pl-1" style={{ color: "rgba(0,0,0,0.32)" }}>
          你可以这样问我
        </p>
        {suggestions.map(({ q, tag }) => (
          <button
            key={q}
            type="button"
            onClick={onNewChat}
            className="w-full flex items-center gap-3 text-left px-4 py-3 rounded-[14px] transition-all duration-150 active:scale-[0.99]"
            style={{
              background: "#FFFFFF",
              border: "1px solid rgba(0,0,0,0.08)",
              color: "rgba(0,0,0,0.65)",
              boxShadow: "0 1px 4px rgba(0,0,0,0.04)",
            }}
            onMouseEnter={(e) => {
              const el = e.currentTarget as HTMLElement;
              el.style.background = "#F5F5F7";
              el.style.borderColor = "rgba(0,0,0,0.12)";
              el.style.color = "rgba(0,0,0,0.82)";
            }}
            onMouseLeave={(e) => {
              const el = e.currentTarget as HTMLElement;
              el.style.background = "#FFFFFF";
              el.style.borderColor = "rgba(0,0,0,0.08)";
              el.style.color = "rgba(0,0,0,0.65)";
            }}
          >
            <span
              className="text-[10px] font-medium px-1.5 py-0.5 rounded-[5px] shrink-0"
              style={{
                background: "rgba(0,122,255,0.10)",
                border: "1px solid rgba(0,122,255,0.20)",
                color: "#007AFF",
              }}
            >
              {tag}
            </span>
            <span className="text-[13.5px]">{q}</span>
          </button>
        ))}
      </div>

      <button
        type="button"
        onClick={onNewChat}
        className="mt-8 flex items-center gap-2 h-9 px-5 rounded-[10px] text-[14px] font-medium transition-all duration-150 active:scale-[0.97]"
        style={{ background: "#007AFF", color: "#ffffff" }}
        onMouseEnter={(e) => (e.currentTarget as HTMLElement).style.background = "rgba(0,122,255,0.88)"}
        onMouseLeave={(e) => (e.currentTarget as HTMLElement).style.background = "#007AFF"}
      >
        开始对话
      </button>
    </div>
  );
};
