import { type FC, useState } from "react";
import { Brain, ChevronDown, ChevronUp, Target, Zap, Layers, AlertTriangle } from "lucide-react";
import { type NluAnalysis } from "../api";

interface NluAnalysisCardProps {
  analysis: NluAnalysis;
}

export const NluAnalysisCard: FC<NluAnalysisCardProps> = ({ analysis }) => {
  const [expanded, setExpanded] = useState(false);

  const hasSlots = Object.keys(analysis.slots).length > 0;
  const hasMissing = analysis.missingRequiredSlots.length > 0;
  const confidencePct = Math.round(analysis.confidence * 100);

  const confidenceColor =
    analysis.confidence >= 0.7 ? "#34C759" :
    analysis.confidence >= 0.4 ? "#FF9500" : "#FF3B30";

  return (
    <div className="animate-fade-in mb-4 mx-auto w-full max-w-3xl">
      <div
        className="rounded-[14px] overflow-hidden"
        style={{
          background: "rgba(255,149,0,0.05)",
          border: "1px solid rgba(255,149,0,0.18)",
        }}
      >
        {/* Header */}
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="w-full flex items-center gap-3 px-4 py-3 transition-colors text-left"
          style={{ color: "inherit" }}
          onMouseEnter={(e) => (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.02)"}
          onMouseLeave={(e) => (e.currentTarget as HTMLElement).style.background = "transparent"}
        >
          <div
            className="w-6 h-6 rounded-[8px] flex items-center justify-center shrink-0"
            style={{ background: "rgba(255,149,0,0.14)" }}
          >
            <Brain size={13} style={{ color: "#FF9500" }} />
          </div>

          <div className="flex-1 text-left min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-[11.5px] font-medium" style={{ color: "#FF9500" }}>意图识别</span>
              <span style={{ color: "rgba(0,0,0,0.22)" }}>·</span>
              <span className="text-[12.5px] font-medium" style={{ color: "rgba(0,0,0,0.78)" }}>
                {analysis.scenarioName || analysis.scenarioCode || "通用问答"}
              </span>
              {analysis.agentSkill && (
                <>
                  <span style={{ color: "rgba(0,0,0,0.18)" }}>·</span>
                  <span className="flex items-center gap-1 text-[11px]" style={{ color: "rgba(0,100,220,0.85)" }}>
                    <Zap size={10} />{analysis.agentSkill}
                  </span>
                </>
              )}
            </div>
            <div className="mt-0.5 text-[11px] truncate" style={{ color: "rgba(0,0,0,0.40)" }}>
              {analysis.reasoning || "已完成意图分析与槽位提取"}
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <span className="text-[12px] font-semibold tabular-nums" style={{ color: confidenceColor }}>
              {confidencePct}%
            </span>
            {hasMissing && <AlertTriangle size={13} style={{ color: "#FF9500" }} />}
            {expanded
              ? <ChevronUp size={13} style={{ color: "rgba(0,0,0,0.28)" }} />
              : <ChevronDown size={13} style={{ color: "rgba(0,0,0,0.28)" }} />
            }
          </div>
        </button>

        {/* Expanded */}
        {expanded && (
          <div
            className="px-4 py-3 space-y-3 animate-fade-in"
            style={{ borderTop: "1px solid rgba(255,149,0,0.10)" }}
          >
            {analysis.scenarioCode && (
              <div className="flex items-start gap-2.5">
                <Target size={13} style={{ color: "rgba(0,0,0,0.28)", marginTop: 2, flexShrink: 0 }} />
                <div>
                  <div className="text-[10.5px] mb-1" style={{ color: "rgba(0,0,0,0.38)" }}>匹配场景</div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <span
                      className="text-[11.5px] font-mono px-2 py-0.5 rounded-[6px]"
                      style={{
                        background: "rgba(0,122,255,0.09)",
                        border: "1px solid rgba(0,122,255,0.18)",
                        color: "#007AFF",
                      }}
                    >
                      {analysis.scenarioCode}
                    </span>
                    {analysis.scenarioName && (
                      <span className="text-[12px]" style={{ color: "rgba(0,0,0,0.62)" }}>
                        {analysis.scenarioName}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            )}

            {hasSlots && (
              <div className="flex items-start gap-2.5">
                <Layers size={13} style={{ color: "rgba(0,0,0,0.28)", marginTop: 2, flexShrink: 0 }} />
                <div className="flex-1 min-w-0">
                  <div className="text-[10.5px] mb-1.5" style={{ color: "rgba(0,0,0,0.38)" }}>提取槽位</div>
                  <div className="grid grid-cols-2 gap-1.5">
                    {Object.entries(analysis.slots).map(([key, val]) => (
                      <div
                        key={key}
                        className="flex items-center gap-2 px-2.5 py-1.5 rounded-[8px]"
                        style={{
                          background: "rgba(0,0,0,0.03)",
                          border: "1px solid rgba(0,0,0,0.07)",
                        }}
                      >
                        <span className="text-[11px] font-mono shrink-0" style={{ color: "rgba(0,0,0,0.38)" }}>{key}</span>
                        <span className="text-[11px] truncate" style={{ color: "rgba(0,0,0,0.70)" }}>{String(val)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {hasMissing && (
              <div className="flex items-start gap-2.5">
                <AlertTriangle size={13} style={{ color: "#FF9500", marginTop: 2, flexShrink: 0 }} />
                <div>
                  <div className="text-[10.5px] mb-1" style={{ color: "rgba(0,0,0,0.38)" }}>缺少必填槽位</div>
                  <div className="flex flex-wrap gap-1.5">
                    {analysis.missingRequiredSlots.map((s) => (
                      <span
                        key={s}
                        className="text-[11px] px-2 py-0.5 rounded-[6px]"
                        style={{
                          background: "rgba(255,149,0,0.09)",
                          border: "1px solid rgba(255,149,0,0.22)",
                          color: "#FF9500",
                        }}
                      >
                        {s}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
