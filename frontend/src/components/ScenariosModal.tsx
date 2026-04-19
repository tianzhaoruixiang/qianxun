import { type FC, useState } from "react";
import { X, Tag, AlertCircle, ChevronRight, Zap, CheckCircle } from "lucide-react";
import { type IntentScenario } from "../api";

interface ScenariosModalProps {
  scenarios: IntentScenario[];
  onClose: () => void;
}

export const ScenariosModal: FC<ScenariosModalProps> = ({ scenarios, onClose }) => {
  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 sm:p-6">
      {/* Backdrop */}
      <div
        className="absolute inset-0"
        style={{ background: "rgba(0,0,0,0.36)", backdropFilter: "blur(6px)" }}
        onClick={onClose}
      />

      {/* Sheet */}
      <div
        className="relative w-full max-w-2xl max-h-[80vh] rounded-[20px] flex flex-col animate-fade-in overflow-hidden"
        style={{
          background: "#FFFFFF",
          border: "1px solid rgba(0,0,0,0.09)",
          boxShadow: "0 24px 64px rgba(0,0,0,0.16), 0 4px 16px rgba(0,0,0,0.08)",
        }}
      >
        {/* Header */}
        <div
          className="flex items-center justify-between px-5 py-4 shrink-0"
          style={{ borderBottom: "1px solid rgba(0,0,0,0.07)" }}
        >
          <div>
            <h2 className="text-[14px] font-semibold" style={{ color: "rgba(0,0,0,0.88)", letterSpacing: "-0.02em" }}>
              意图场景
            </h2>
            <p className="text-[11.5px] mt-0.5" style={{ color: "rgba(0,0,0,0.42)" }}>
              已启用场景 · 按优先级排序
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-full flex items-center justify-center transition-all duration-150"
            style={{ background: "rgba(0,0,0,0.06)", color: "rgba(0,0,0,0.50)" }}
            onMouseEnter={(e) => (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.10)"}
            onMouseLeave={(e) => (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.06)"}
          >
            <X size={13} />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-4">
          {scenarios.length === 0 ? (
            <div
              className="flex flex-col items-center gap-3 py-12"
              style={{ color: "rgba(0,0,0,0.28)" }}
            >
              <AlertCircle size={28} strokeWidth={1.5} />
              <span className="text-[13px]">暂无可用场景</span>
            </div>
          ) : (
            <div className="space-y-2">
              {scenarios.map((s) => (
                <ScenarioCard key={s.id} scenario={s} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

const ScenarioCard: FC<{ scenario: IntentScenario }> = ({ scenario }) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div
      className="rounded-[12px] overflow-hidden transition-all duration-150"
      style={{
        background: "rgba(0,0,0,0.025)",
        border: "1px solid rgba(0,0,0,0.08)",
      }}
    >
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="w-full flex items-center gap-3 px-4 py-3 text-left transition-colors"
        onMouseEnter={(e) => (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.02)"}
        onMouseLeave={(e) => (e.currentTarget as HTMLElement).style.background = "transparent"}
      >
        {/* Priority badge */}
        <div
          className="w-6 h-6 rounded-[7px] flex items-center justify-center shrink-0 text-[10px] font-bold"
          style={{
            background: "rgba(0,122,255,0.10)",
            border: "1px solid rgba(0,122,255,0.20)",
            color: "#007AFF",
          }}
        >
          P{scenario.priority}
        </div>

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span
              className="text-[11px] font-mono px-1.5 py-0.5 rounded-[5px]"
              style={{
                background: "rgba(0,122,255,0.08)",
                border: "1px solid rgba(0,122,255,0.16)",
                color: "rgba(0,100,220,0.85)",
              }}
            >
              {scenario.code}
            </span>
            <span className="text-[13px] font-medium" style={{ color: "rgba(0,0,0,0.82)" }}>
              {scenario.name}
            </span>
            {scenario.agentSkill && (
              <span className="flex items-center gap-1 text-[10.5px]" style={{ color: "rgba(0,0,0,0.40)" }}>
                <Zap size={10} />{scenario.agentSkill}
              </span>
            )}
          </div>
          {scenario.description && (
            <p className="text-[11.5px] mt-0.5 truncate" style={{ color: "rgba(0,0,0,0.42)" }}>
              {scenario.description}
            </p>
          )}
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {scenario.enabled
            ? <CheckCircle size={13} style={{ color: "#34C759" }} />
            : <AlertCircle size={13} style={{ color: "rgba(0,0,0,0.22)" }} />
          }
          <ChevronRight
            size={13}
            style={{
              color: "rgba(0,0,0,0.22)",
              transform: expanded ? "rotate(90deg)" : "none",
              transition: "transform 0.15s",
            }}
          />
        </div>
      </button>

      {expanded && (
        <div
          className="px-4 pb-3 pt-1 space-y-2.5 animate-fade-in"
          style={{ borderTop: "1px solid rgba(0,0,0,0.06)" }}
        >
          {scenario.description && (
            <p className="text-[12.5px] leading-relaxed" style={{ color: "rgba(0,0,0,0.55)" }}>
              {scenario.description}
            </p>
          )}

          {scenario.slots && scenario.slots.length > 0 && (
            <div>
              <div className="flex items-center gap-1.5 mb-1.5">
                <Tag size={10} style={{ color: "rgba(0,0,0,0.30)" }} />
                <span className="text-[10.5px]" style={{ color: "rgba(0,0,0,0.36)" }}>槽位定义</span>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {scenario.slots.map((slot) => (
                  <div
                    key={slot.name}
                    title={slot.description}
                    className="flex items-center gap-1 text-[11px] px-2 py-0.5 rounded-full"
                    style={{
                      background: slot.required ? "rgba(52,199,89,0.08)" : "rgba(0,0,0,0.04)",
                      border: slot.required ? "1px solid rgba(52,199,89,0.22)" : "1px solid rgba(0,0,0,0.08)",
                      color: slot.required ? "#34C759" : "rgba(0,0,0,0.50)",
                    }}
                  >
                    <span className="font-medium">{slot.name}</span>
                    <span style={{ opacity: 0.6 }}>:{slot.type}</span>
                    {slot.required && <span style={{ color: "#34C759", fontWeight: "bold" }}>*</span>}
                  </div>
                ))}
              </div>
            </div>
          )}

          {scenario.examples && scenario.examples.length > 0 && (
            <div>
              <div className="text-[10.5px] mb-1" style={{ color: "rgba(0,0,0,0.36)" }}>示例话术</div>
              <div className="space-y-1">
                {scenario.examples.slice(0, 3).map((ex, i) => (
                  <div
                    key={i}
                    className="text-[12px] px-3 py-1.5 rounded-[8px]"
                    style={{
                      background: "rgba(0,0,0,0.025)",
                      border: "1px solid rgba(0,0,0,0.06)",
                      color: "rgba(0,0,0,0.55)",
                    }}
                  >
                    "{ex}"
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
