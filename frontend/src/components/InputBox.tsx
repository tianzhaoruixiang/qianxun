import { type FC, type KeyboardEvent, useRef, useEffect } from "react";
import { SendHorizonal, Square, Zap, Brain } from "lucide-react";
import { cn } from "../lib/utils";

export type ThinkingMode = "quick" | "deep";

interface InputBoxProps {
  value: string;
  onChange: (v: string) => void;
  onSend: () => void;
  onStop?: () => void;
  disabled?: boolean;
  busy?: boolean;
  placeholder?: string;
  thinkingMode: ThinkingMode;
  onThinkingModeChange: (mode: ThinkingMode) => void;
}

export const InputBox: FC<InputBoxProps> = ({
  value,
  onChange,
  onSend,
  disabled,
  busy,
  placeholder = "输入问题…  Enter 发送，Shift+Enter 换行",
  thinkingMode,
  onThinkingModeChange,
}) => {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    const ta = textareaRef.current;
    if (!ta) return;
    ta.style.height = "auto";
    ta.style.height = `${Math.min(ta.scrollHeight, 200)}px`;
  }, [value]);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      if (!busy && value.trim()) onSend();
    }
  };

  const canSend = !disabled && !busy && value.trim().length > 0;
  const isDeep = thinkingMode === "deep";

  return (
    <div className="px-4 pb-5 pt-2 shrink-0">
      <div className="mx-auto max-w-3xl">
        {/* Mode switcher — macOS segmented control */}
        <div className="flex items-center gap-3 mb-2 px-0.5">
          <div
            className="flex items-center p-0.5 rounded-[10px] gap-px"
            style={{ background: "rgba(0,0,0,0.06)" }}
          >
            <SegmentBtn
              active={!isDeep}
              icon={<Zap size={11} />}
              label="快速"
              title="快速模式：即时响应"
              onClick={() => onThinkingModeChange("quick")}
              activeColor="blue"
            />
            <SegmentBtn
              active={isDeep}
              icon={<Brain size={11} />}
              label="深度思考"
              title="深度思考：全面推理后作答"
              onClick={() => onThinkingModeChange("deep")}
              activeColor="purple"
            />
          </div>

          {value.length > 0 && (
            <span className="text-[11px] tabular-nums ml-auto" style={{ color: "rgba(0,0,0,0.28)" }}>
              {value.length} 字
            </span>
          )}
        </div>

        {/* Input container */}
        <div
          className="relative flex items-end gap-2 rounded-[16px] px-4 py-3 transition-all duration-150"
          style={{
            background: "#FFFFFF",
            border: busy
              ? "1px solid rgba(0,122,255,0.28)"
              : "1px solid rgba(0,0,0,0.11)",
            boxShadow: "0 2px 12px rgba(0,0,0,0.07)",
          }}
          onFocus={(e) => {
            if (!busy) (e.currentTarget as HTMLElement).style.borderColor = "rgba(0,122,255,0.36)";
          }}
          onBlur={(e) => {
            if (!busy) (e.currentTarget as HTMLElement).style.borderColor = "rgba(0,0,0,0.11)";
          }}
        >
          <textarea
            ref={textareaRef}
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={disabled}
            placeholder={placeholder}
            rows={1}
            className={cn(
              "flex-1 resize-none bg-transparent text-[14px] outline-none",
              "min-h-[24px] leading-6 py-0.5",
              "disabled:opacity-50 disabled:cursor-not-allowed",
            )}
            style={{
              color: "rgba(0,0,0,0.88)",
              caretColor: "#007AFF",
            }}
          />
          <style>{`textarea::placeholder { color: rgba(0,0,0,0.30); }`}</style>

          {/* Send / Stop button */}
          <button
            type="button"
            disabled={!canSend && !busy}
            onClick={onSend}
            className="shrink-0 w-8 h-8 rounded-full flex items-center justify-center transition-all duration-150 active:scale-90 pb-0.5"
            style={{
              background: canSend
                ? "#007AFF"
                : busy
                  ? "rgba(255,59,48,0.12)"
                  : "rgba(0,0,0,0.06)",
              border: busy ? "1px solid rgba(255,59,48,0.22)" : "none",
              cursor: canSend || busy ? "pointer" : "not-allowed",
            }}
            title={busy ? "停止生成" : "发送"}
          >
            {busy ? (
              <Square size={12} style={{ color: "#FF3B30" }} />
            ) : (
              <SendHorizonal
                size={14}
                style={{ color: canSend ? "#ffffff" : "rgba(0,0,0,0.25)" }}
              />
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

/* ── Segmented control button ──────────────────────────────────────────────── */
interface SegmentBtnProps {
  active: boolean;
  icon: React.ReactNode;
  label: string;
  title: string;
  onClick: () => void;
  activeColor: "blue" | "purple";
}

const SegmentBtn: FC<SegmentBtnProps> = ({ active, icon, label, title, onClick, activeColor }) => {
  const blue = activeColor === "blue";
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-[8px] text-[11.5px] font-medium transition-all duration-150"
      style={{
        background: active
          ? blue ? "rgba(0,122,255,0.12)" : "rgba(175,82,222,0.10)"
          : "transparent",
        border: active
          ? blue ? "1px solid rgba(0,122,255,0.24)" : "1px solid rgba(175,82,222,0.22)"
          : "1px solid transparent",
        color: active
          ? blue ? "#007AFF" : "#AF52DE"
          : "rgba(0,0,0,0.38)",
      }}
    >
      {icon}
      <span>{label}</span>
    </button>
  );
};
