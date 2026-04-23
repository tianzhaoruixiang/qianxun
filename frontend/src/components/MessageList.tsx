import { type FC, useRef, useEffect, useState, useCallback, useMemo, type ComponentPropsWithoutRef } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {
  User, BrainCircuit, ThumbsUp, ThumbsDown, RotateCcw,
  Brain, ChevronDown, ChevronUp, Zap, Wrench,
} from "lucide-react";
import {
  type AgentStep, type ChatMessage, type EntityCard, type FeedbackType, type ClarificationPayload,
  submitFeedback, deleteFeedback,
} from "../api";
import { formatFullTime, cn } from "../lib/utils";
import { stripEntityBlockForDisplay } from "../lib/entityExtractor";
import { EntityCardPanel } from "./EntityCardPanel";
import { ClarificationCard } from "./ClarificationCard";

/* ── Markdown renderer ─────────────────────────────────────────────────────── */
const Markdown: FC<{ children: string; streaming?: boolean }> = ({ children, streaming }) => (
  <div className={cn("prose", streaming && "streaming-cursor")}>
    <ReactMarkdown
      remarkPlugins={[remarkGfm]}
      components={{
        table: ({ children, ...props }) => (
          <div className="prose-table-wrap">
            <table {...props}>{children}</table>
          </div>
        ),
        a: ({ href, children, ...props }: ComponentPropsWithoutRef<"a">) => (
          <a href={href} target="_blank" rel="noopener noreferrer" {...props}>
            {children}
          </a>
        ),
      }}
    >
      {children}
    </ReactMarkdown>
  </div>
);

/* ── AgentSteps ─────────────────────────────────────────────────────────────── */
const AgentSteps: FC<{ steps: AgentStep[] }> = ({ steps }) => {
  if (steps.length === 0) return null;
  return (
    <div className="mb-2 flex flex-col gap-1">
      {steps.map((step, i) => {
        if (step.kind === "agent_skill") {
          return (
            <div
              key={i}
              className="flex items-center gap-2 px-3 py-1.5 rounded-[10px] text-[11.5px] animate-fade-in"
              style={{
                maxWidth: "88%",
                background: "rgba(255,149,0,0.08)",
                border: "1px solid rgba(255,149,0,0.20)",
                color: "rgba(160,90,0,0.90)",
              }}
            >
              <Zap size={10} className="shrink-0" />
              <span className="font-medium">{step.label}</span>
              {step.detail && (
                <>
                  <span style={{ color: "rgba(160,90,0,0.35)" }}>·</span>
                  <span className="truncate" style={{ color: "rgba(160,90,0,0.65)" }}>{step.detail}</span>
                </>
              )}
            </div>
          );
        }
        if (step.kind === "tool_call") {
          const raw = step.args ?? "";
          let argsPreview = "";
          try {
            const parsed = JSON.parse(raw) as Record<string, unknown>;
            const keyInfo = [
              typeof parsed.label === "string" ? `label: ${parsed.label}` : "",
              typeof parsed.emoji === "string" ? `emoji: ${parsed.emoji}` : "",
              typeof parsed.result === "string" ? `result: ${parsed.result}` : "",
              typeof parsed.output === "string" ? `output: ${parsed.output}` : "",
            ].filter(Boolean);
            if (keyInfo.length > 0) {
              argsPreview = keyInfo.join("\n").slice(0, 360);
            } else {
              argsPreview = JSON.stringify(parsed, null, 2).slice(0, 360);
            }
          } catch {
            argsPreview = raw.replace(/[{}"]/g, "").slice(0, 360);
          }
          return (
            <ToolCallStep
              key={`${step.toolCallId}-${i}`}
              toolName={step.toolName}
              argsPreview={argsPreview}
            />
          );
        }
        return null;
      })}
    </div>
  );
};

const ToolCallStep: FC<{ toolName: string; argsPreview: string }> = ({ toolName, argsPreview }) => {
  const [collapsed, setCollapsed] = useState(true);
  return (
    <div
      className="px-3 py-1.5 rounded-[10px] text-[11.5px] animate-fade-in"
      style={{
        maxWidth: "88%",
        background: "rgba(0,122,255,0.08)",
        border: "1px solid rgba(0,122,255,0.18)",
        color: "rgba(0,90,200,0.90)",
      }}
    >
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        className="w-full flex items-center gap-2 text-left"
        style={{ color: "inherit" }}
      >
        <Wrench size={10} className="shrink-0" />
        <span className="font-medium">{toolName}</span>
        {argsPreview && (
          <span className="ml-auto text-[10px]" style={{ color: "rgba(0,90,200,0.62)" }}>
            {collapsed ? "展开结果" : "收起结果"}
          </span>
        )}
      </button>
      {!collapsed && argsPreview && (
        <pre
          className="mt-1 whitespace-pre-wrap break-words text-[11px] leading-relaxed"
          style={{ color: "rgba(0,90,200,0.72)", fontFamily: "inherit", marginBottom: 0 }}
        >
          {argsPreview}
        </pre>
      )}
    </div>
  );
};

/* ── ThinkBlock ────────────────────────────────────────────────────────────── */
interface ThinkBlockProps {
  content: string;
  streaming?: boolean;
  defaultCollapsed?: boolean;
}

const ThinkBlock: FC<ThinkBlockProps> = ({ content, streaming = false, defaultCollapsed = true }) => {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);

  return (
    <div className="mb-2" style={{ maxWidth: "88%" }}>
      <button
        type="button"
        onClick={() => setCollapsed((c) => !c)}
        className="flex items-center gap-2 w-full px-3 py-2 rounded-[12px] text-[11.5px] font-medium transition-all duration-150"
        style={{
          background: "rgba(175,82,222,0.07)",
          border: "1px solid rgba(175,82,222,0.18)",
          color: "rgba(130,50,180,0.88)",
        }}
      >
        <Brain size={11} className={cn("shrink-0", streaming && "animate-pulse")} />
        <span className="flex-1 text-left">
          {streaming ? "深度思考中…" : "查看思考过程"}
        </span>
        {collapsed ? <ChevronDown size={10} /> : <ChevronUp size={10} />}
      </button>

      {!collapsed && content && (
        <div
          className={cn(
            "mt-1 px-3 py-2.5 rounded-[12px] text-[12px] leading-relaxed whitespace-pre-wrap",
            streaming && "streaming-cursor",
          )}
          style={{
            background: "rgba(175,82,222,0.04)",
            border: "1px solid rgba(175,82,222,0.10)",
            color: "rgba(0,0,0,0.48)",
          }}
        >
          {content}
        </div>
      )}
    </div>
  );
};

/* ── FeedbackBar ───────────────────────────────────────────────────────────── */
interface FeedbackBarProps { sessionId: string; messageId: string }

const FeedbackBar: FC<FeedbackBarProps> = ({ sessionId, messageId }) => {
  const [current, setCurrent] = useState<FeedbackType | null>(null);
  const [loading, setLoading] = useState(false);

  const handleFeedback = useCallback(async (type: FeedbackType) => {
    if (loading) return;
    if (current === type) {
      setLoading(true);
      try { await deleteFeedback(sessionId, messageId); setCurrent(null); }
      finally { setLoading(false); }
      return;
    }
    setLoading(true);
    try {
      const r = await submitFeedback(sessionId, messageId, type);
      setCurrent(r.feedbackType as FeedbackType);
    } finally { setLoading(false); }
  }, [sessionId, messageId, current, loading]);

  return (
    <div className="flex items-center gap-1 mt-1.5">
      {(["like", "dislike"] as FeedbackType[]).map((t) => {
        const isLike = t === "like";
        const isActive = current === t;
        return (
          <button
            key={t}
            type="button"
            onClick={() => void handleFeedback(t)}
            disabled={loading}
            title={isActive ? "撤销" : isLike ? "有帮助" : "需改进"}
            className="flex items-center gap-1 px-2 py-0.5 rounded-[7px] text-[11px] transition-all duration-150 disabled:opacity-40"
            style={{
              background: isActive
                ? isLike ? "rgba(52,199,89,0.10)" : "rgba(255,59,48,0.08)"
                : "transparent",
              border: isActive
                ? isLike ? "1px solid rgba(52,199,89,0.28)" : "1px solid rgba(255,59,48,0.22)"
                : "1px solid transparent",
              color: isActive
                ? isLike ? "#34C759" : "#FF3B30"
                : "rgba(0,0,0,0.22)",
            }}
            onMouseEnter={(e) => {
              if (!isActive) (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.50)";
            }}
            onMouseLeave={(e) => {
              if (!isActive) (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.22)";
            }}
          >
            {isLike ? <ThumbsUp size={11} /> : <ThumbsDown size={11} />}
          </button>
        );
      })}
      {current && (
        <button
          type="button"
          onClick={() => void handleFeedback(current)}
          disabled={loading}
          title="撤销反馈"
          className="w-5 h-5 flex items-center justify-center rounded-md transition-colors"
          style={{ color: "rgba(0,0,0,0.20)" }}
          onMouseEnter={(e) => (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.45)"}
          onMouseLeave={(e) => (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.20)"}
        >
          <RotateCcw size={10} />
        </button>
      )}
    </div>
  );
};

/* ── MessageItem ───────────────────────────────────────────────────────────── */
const MessageItem: FC<{ message: ChatMessage; sessionId: string }> = ({ message, sessionId }) => {
  const isUser = message.role === "user";
  const isDeep = message.thinkingMode === "deep";

  const entityCards = useMemo(
    () => (isUser ? [] : (message.entityCards ?? [])),
    [isUser, message.entityCards],
  );

  return (
    <div className={cn("flex gap-3 animate-fade-in", isUser ? "flex-row-reverse" : "flex-row")}>
      {/* Avatar */}
      <div
        className="w-[28px] h-[28px] rounded-full shrink-0 flex items-center justify-center mt-0.5"
        style={{
          background: isUser ? "#007AFF" : isDeep ? "rgba(175,82,222,0.16)" : "rgba(0,0,0,0.06)",
          border: isUser ? "none" : isDeep ? "1px solid rgba(175,82,222,0.22)" : "1px solid rgba(0,0,0,0.08)",
          flexShrink: 0,
        }}
      >
        {isUser ? (
          <User size={13} className="text-white" />
        ) : isDeep ? (
          <Brain size={13} style={{ color: "#AF52DE" }} />
        ) : (
          <BrainCircuit size={13} style={{ color: "rgba(0,0,0,0.45)" }} />
        )}
      </div>

      {/* Content column */}
      <div className={cn("flex-1 min-w-0", isUser && "flex flex-col items-end")}>
        {/* Header row */}
        <div
          className={cn("flex items-center gap-2 mb-1.5", isUser ? "flex-row-reverse" : "flex-row")}
        >
          <span
            className="text-[12px] font-medium"
            style={{ color: isUser ? "rgba(0,122,255,0.85)" : "rgba(0,0,0,0.50)" }}
          >
            {isUser ? "你" : "千寻"}
          </span>
          {!isUser && isDeep && (
            <span
              className="flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-[6px] font-medium"
              style={{
                background: "rgba(175,82,222,0.09)",
                border: "1px solid rgba(175,82,222,0.20)",
                color: "#AF52DE",
              }}
            >
              <Brain size={8} />
              深度思考
            </span>
          )}
          <span className="text-[10.5px]" style={{ color: "rgba(0,0,0,0.24)" }}>
            {formatFullTime(message.createdAt)}
          </span>
        </div>

        {/* Think block (historical) */}
        {!isUser && isDeep && message.thinkContent && (
          <ThinkBlock content={message.thinkContent} defaultCollapsed />
        )}

        {/* Bubble */}
        <div
          className="rounded-[18px] px-4 py-2.5 text-[14px] leading-relaxed"
          style={{
            maxWidth: "88%",
            background: isUser ? "#007AFF" : "#FFFFFF",
            color: isUser ? "#ffffff" : "rgba(0,0,0,0.85)",
            boxShadow: isUser ? "none" : "0 1px 4px rgba(0,0,0,0.06)",
            border: isUser ? "none" : "1px solid rgba(0,0,0,0.07)",
            ...(isUser
              ? { borderBottomRightRadius: "6px" }
              : { borderBottomLeftRadius: "6px" }),
          }}
        >
          {isUser ? (
            <p className="whitespace-pre-wrap m-0">{message.content}</p>
          ) : (
            <Markdown>{message.content}</Markdown>
          )}
        </div>

        {/* Feedback */}
        {!isUser && (
          <FeedbackBar sessionId={sessionId} messageId={message.id} />
        )}

        {/* Entity cards panel — 机构/人物实体卡片（>= 3 条时自动显示） */}
        {!isUser && entityCards.length > 0 && (
          <EntityCardPanel cards={entityCards} threshold={1} />
        )}
      </div>
    </div>
  );
};

/* ── StreamingMessage ──────────────────────────────────────────────────────── */
const StreamingMessage: FC<{
  text: string;
  thinkText: string;
  isThinking: boolean;
  steps: AgentStep[];
  /** 后端 entities 事件下发的结构化实体 */
  streamingEntities?: EntityCard[];
  clarification?: ClarificationPayload | null;
  onSelectClarification?: (code: string, name: string) => void;
}> = ({ text, thinkText, isThinking, steps, streamingEntities = [], clarification, onSelectClarification }) => {
  const hasDeep = isThinking || thinkText.length > 0;
  const displayText = useMemo(() => stripEntityBlockForDisplay(text), [text]);

  return (
    <div className="flex gap-3 animate-fade-in">
      <div
        className="w-[28px] h-[28px] rounded-full shrink-0 flex items-center justify-center mt-0.5"
        style={{
          flexShrink: 0,
          background: hasDeep ? "rgba(175,82,222,0.16)" : "rgba(0,0,0,0.06)",
          border: hasDeep ? "1px solid rgba(175,82,222,0.22)" : "1px solid rgba(0,0,0,0.08)",
        }}
      >
        {hasDeep
          ? <Brain size={13} style={{ color: "#AF52DE" }} />
          : <BrainCircuit size={13} style={{ color: "rgba(0,0,0,0.45)" }} />
        }
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1.5">
          <span className="text-[12px] font-medium" style={{ color: "rgba(0,0,0,0.50)" }}>千寻</span>
          {hasDeep && (
            <span
              className="flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-[6px] font-medium"
              style={{
                background: "rgba(175,82,222,0.09)",
                border: "1px solid rgba(175,82,222,0.20)",
                color: "#AF52DE",
              }}
            >
              <Brain size={8} />
              深度思考
            </span>
          )}
          <div className="flex items-center gap-1" style={{ color: "rgba(0,0,0,0.32)" }}>
            <div
              className="w-1.5 h-1.5 rounded-full pulse-dot"
              style={{ background: hasDeep ? "#AF52DE" : "#007AFF" }}
            />
            <span className="text-[10.5px]">{isThinking ? "深度思考中" : "生成中"}</span>
          </div>
        </div>

        {/* Agent steps — 意图路由 / 工具调用 */}
        <AgentSteps steps={steps} />

        {/* Streaming think content */}
        {(thinkText || isThinking) && (
          <ThinkBlock content={thinkText} streaming={isThinking} defaultCollapsed={false} />
        )}

        {/* Streaming answer */}
        {text ? (
          <div
            className="rounded-[18px] px-4 py-2.5 text-[14px] leading-relaxed mt-1"
            style={{
              maxWidth: "88%",
              background: "#FFFFFF",
              color: "rgba(0,0,0,0.85)",
              boxShadow: "0 1px 4px rgba(0,0,0,0.06)",
              border: "1px solid rgba(0,0,0,0.07)",
              borderBottomLeftRadius: "6px",
            }}
          >
            <Markdown streaming>{displayText}</Markdown>
          </div>
        ) : !thinkText && !isThinking ? (
          /* Loading dots */
          <div
            className="rounded-[18px] px-4 py-3 inline-flex items-center gap-1.5"
            style={{
              background: "#FFFFFF",
              border: "1px solid rgba(0,0,0,0.07)",
              boxShadow: "0 1px 4px rgba(0,0,0,0.06)",
              borderBottomLeftRadius: "6px",
            }}
          >
            {[0, 150, 300].map((delay) => (
              <div
                key={delay}
                className="w-1.5 h-1.5 rounded-full animate-bounce"
                style={{ background: "rgba(0,0,0,0.28)", animationDelay: `${delay}ms` }}
              />
            ))}
          </div>
        ) : null}

        {streamingEntities.length > 0 && (
          <EntityCardPanel cards={streamingEntities} threshold={1} />
        )}

        {/* 意图澄清卡片（置信度低时显示） */}
        {clarification && onSelectClarification && (
          <ClarificationCard
            question={clarification.question}
            confidence={clarification.confidence}
            options={clarification.options}
            onSelect={onSelectClarification}
          />
        )}
      </div>
    </div>
  );
};

/* ── MessageList ───────────────────────────────────────────────────────────── */
interface MessageListProps {
  messages: ChatMessage[];
  streamingText: string;
  streamingThinkText: string;
  isThinking: boolean;
  busy: boolean;
  sessionId: string;
  streamingSteps: AgentStep[];
  streamingEntities?: EntityCard[];
  pendingClarification?: ClarificationPayload | null;
  onSelectClarification?: (code: string, name: string) => void;
}

export const MessageList: FC<MessageListProps> = ({
  messages, streamingText, streamingThinkText, isThinking, busy, sessionId, streamingSteps,
  streamingEntities, pendingClarification, onSelectClarification,
}) => {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, streamingText, streamingThinkText, busy]);

  return (
    <div className="flex-1 overflow-y-auto px-4 py-6">
      <div className="mx-auto max-w-4xl space-y-6">
        {messages.map((m) => (
          <MessageItem key={m.id} message={m} sessionId={sessionId} />
        ))}
        {(busy || streamingText || streamingThinkText || pendingClarification) && (
          <StreamingMessage
            text={streamingText}
            thinkText={streamingThinkText}
            isThinking={isThinking}
            steps={streamingSteps}
            streamingEntities={streamingEntities}
            clarification={pendingClarification}
            onSelectClarification={onSelectClarification}
          />
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  );
};
