import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  MoreHorizontal, Pencil, Trash2, Layers, AlertCircle, X,
  PanelLeftClose, PanelLeft,
} from "lucide-react";
import {
  type AgentStep, type ChatMessage, type ChatSession, type CurrentUser,
  type IntentScenario, type NluAnalysis,
  createSession, deleteSession, getCurrentUser,
  listIntentScenarios, listMessages, listSessions,
  streamChat, updateSession,
} from "./api";
import { Sidebar } from "./components/Sidebar";
import { MessageList } from "./components/MessageList";
import { InputBox, type ThinkingMode } from "./components/InputBox";
import { NluAnalysisCard } from "./components/NluAnalysisCard";
import { ScenariosModal } from "./components/ScenariosModal";
import { WelcomeScreen } from "./components/WelcomeScreen";
import { cn } from "./lib/utils";

export function App() {
  const [sessions,            setSessions          ] = useState<ChatSession[]>([]);
  const [activeId,            setActiveId          ] = useState<string | null>(null);
  const [messages,            setMessages          ] = useState<ChatMessage[]>([]);
  const [draft,               setDraft             ] = useState("");
  const [busy,                setBusy              ] = useState(false);
  const [error,               setError             ] = useState<string | null>(null);
  const [streamingText,       setStreamingText      ] = useState("");
  const [streamingThinkText,  setStreamingThinkText ] = useState("");
  const [isThinking,          setIsThinking         ] = useState(false);
  const [thinkingMode,        setThinkingMode       ] = useState<ThinkingMode>("quick");
  const [lastAnalysis,        setLastAnalysis       ] = useState<NluAnalysis | null>(null);
  const [streamingSteps,      setStreamingSteps     ] = useState<AgentStep[]>([]);
  const [scenarios,           setScenarios          ] = useState<IntentScenario[]>([]);
  const [scenariosOpen,       setScenariosOpen      ] = useState(false);
  const [sidebarOpen,         setSidebarOpen        ] = useState(true);
  const [menuOpen,            setMenuOpen           ] = useState(false);
  const [currentUser,         setCurrentUser        ] = useState<CurrentUser | null>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const activeSession = useMemo(
    () => sessions.find((s) => s.id === activeId) ?? null,
    [sessions, activeId],
  );

  const refreshSessions = useCallback(async () => {
    const data = await listSessions();
    setSessions(data);
    return data;
  }, []);

  const refreshScenarios = useCallback(async () => {
    try { setScenarios(await listIntentScenarios(true)); }
    catch { setScenarios([]); }
  }, []);

  useEffect(() => { void refreshScenarios(); }, [refreshScenarios]);
  useEffect(() => { getCurrentUser().then(setCurrentUser).catch(() => {}); }, []);

  useEffect(() => {
    (async () => {
      try {
        setError(null);
        const data = await listSessions();
        setSessions(data);
        if (data.length > 0) setActiveId((cur) => cur ?? data[0].id);
      } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    })();
  }, []);

  useEffect(() => {
    if (!activeId) { setMessages([]); return; }
    (async () => {
      try {
        setError(null);
        setMessages(await listMessages(activeId));
        setLastAnalysis(null);
      } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
    })();
  }, [activeId]);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const onNewChat = async () => {
    try {
      setError(null);
      const s = await createSession();
      await refreshSessions();
      setActiveId(s.id);
      setMessages([]);
      setDraft("");
      setLastAnalysis(null);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const onDeleteSession = async (id: string) => {
    if (!window.confirm("确认删除这个对话？")) return;
    try {
      setError(null);
      await deleteSession(id);
      const next = await refreshSessions();
      if (id === activeId) {
        setActiveId(next[0]?.id ?? null);
        setMessages([]);
        setLastAnalysis(null);
      }
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const onRename = async () => {
    if (!activeId) return;
    setMenuOpen(false);
    const title = window.prompt("修改对话标题", activeSession?.title ?? "");
    if (title == null) return;
    const trimmed = title.trim();
    if (!trimmed) return;
    try {
      setError(null);
      await updateSession(activeId, trimmed);
      await refreshSessions();
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const onDeleteActive = async () => {
    if (!activeId) return;
    setMenuOpen(false);
    await onDeleteSession(activeId);
  };

  const onSend = async () => {
    if (!activeId) {
      try {
        const s = await createSession();
        await refreshSessions();
        setActiveId(s.id);
        setMessages([]);
        void doSend(s.id, draft.trim());
        setDraft("");
      } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
      return;
    }
    const text = draft.trim();
    if (!text || busy) return;
    setDraft("");
    await doSend(activeId, text);
  };

  const doSend = async (sessionId: string, text: string) => {
    const localUser: ChatMessage = {
      id: `local-${Date.now()}`,
      sessionId,
      role: "user",
      content: text,
      thinkingMode: null,
      thinkContent: null,
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, localUser]);
    setBusy(true);
    setStreamingText("");
    setStreamingThinkText("");
    setIsThinking(false);
    setLastAnalysis(null);
    setStreamingSteps([]);
    setError(null);

    try {
      await streamChat(sessionId, text, {
        onAnalysis: (a) => {
          setLastAnalysis(a);
          // 将 NLU 分析结果也作为一个步骤推送到消息流里
          if (a.scenarioName || a.scenarioCode) {
            setStreamingSteps((prev) => [
              ...prev,
              { kind: "agent_skill", label: a.agentSkill || a.scenarioCode, detail: a.scenarioName },
            ]);
          }
        },
        onAgentStep: (step) => setStreamingSteps((prev) => {
          // 工具调用按 toolCallId 去重（同一工具的 args 更新只保留最新）
          if (step.kind === "tool_call") {
            const idx = prev.findIndex(
              (s) => s.kind === "tool_call" && s.toolCallId === step.toolCallId,
            );
            if (idx >= 0) {
              const next = [...prev];
              next[idx] = step;
              return next;
            }
          }
          return [...prev, step];
        }),
        onThinkStart: () => setIsThinking(true),
        onThinkToken: (t) => setStreamingThinkText((cur) => cur + t),
        onThinkEnd: () => setIsThinking(false),
        onToken: (t) => setStreamingText((cur) => cur + t),
        onDone: async () => {
          setStreamingText(""); setStreamingThinkText(""); setIsThinking(false); setBusy(false);
          setStreamingSteps([]);
          setMessages(await listMessages(sessionId));
          await refreshSessions();
        },
        onError: (msg) => {
          setBusy(false); setStreamingText(""); setStreamingThinkText(""); setIsThinking(false);
          setStreamingSteps([]);
          setError(msg);
        },
      }, thinkingMode);
    } catch (e) {
      setBusy(false); setStreamingText(""); setStreamingThinkText(""); setIsThinking(false);
      setStreamingSteps([]);
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="flex h-screen overflow-hidden" style={{ background: "#F2F2F7" }}>
      {/* ── Sidebar ── */}
      <div className={cn(
        "transition-all duration-300 ease-in-out overflow-hidden shrink-0",
        sidebarOpen ? "w-64" : "w-0",
      )}>
        <Sidebar
          sessions={sessions}
          activeId={activeId}
          onNewChat={() => void onNewChat()}
          onSelectSession={(id) => setActiveId(id)}
          onDeleteSession={(id) => void onDeleteSession(id)}
        />
      </div>

      {/* ── Main ── */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">

        {/* Toolbar */}
        <header
          className="flex items-center gap-2 px-3 h-12 shrink-0"
          style={{
            borderBottom: "1px solid rgba(0,0,0,0.08)",
            background: "rgba(242,242,247,0.85)",
            backdropFilter: "blur(20px)",
          }}
        >
          {/* Sidebar toggle */}
          <button
            type="button"
            onClick={() => setSidebarOpen((v) => !v)}
            title={sidebarOpen ? "收起侧边栏" : "展开侧边栏"}
            className="w-8 h-8 rounded-[8px] flex items-center justify-center transition-all duration-150"
            style={{ color: "rgba(0,0,0,0.40)" }}
            onMouseEnter={(e) => {
              (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.06)";
              (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.72)";
            }}
            onMouseLeave={(e) => {
              (e.currentTarget as HTMLElement).style.background = "transparent";
              (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.40)";
            }}
          >
            {sidebarOpen ? <PanelLeftClose size={16} /> : <PanelLeft size={16} />}
          </button>

          {/* Session title */}
          <div className="flex-1 min-w-0 px-1">
            {activeSession ? (
              <h1
                className="text-[13.5px] font-medium truncate"
                style={{ color: "rgba(0,0,0,0.82)", letterSpacing: "-0.01em" }}
              >
                {activeSession.title}
              </h1>
            ) : (
              <h1 className="text-[13px]" style={{ color: "rgba(0,0,0,0.32)" }}>
                选择或新建一个对话
              </h1>
            )}
          </div>

          {/* Right actions */}
          <div className="flex items-center gap-1.5 shrink-0">
            {/* User info */}
            {currentUser && (
              <div className="flex items-center gap-2 pl-1 pr-2.5 py-1 rounded-full" style={{ background: "rgba(0,0,0,0.05)" }}>
                <div
                  className="w-[22px] h-[22px] rounded-full shrink-0 flex items-center justify-center text-white text-[10px] font-semibold"
                  style={{ background: "#007AFF" }}
                >
                  {currentUser.displayName?.[0]?.toUpperCase() ?? currentUser.username?.[0]?.toUpperCase() ?? "U"}
                </div>
                <span className="text-[12px] hidden sm:inline" style={{ color: "rgba(0,0,0,0.60)" }}>
                  {currentUser.displayName || currentUser.username}
                </span>
              </div>
            )}
            {/* Busy indicator */}
            {busy && (
              <div
                className="flex items-center gap-1.5 text-[11.5px] rounded-full px-3 py-1"
                style={{
                  background: "rgba(0,122,255,0.10)",
                  border: "1px solid rgba(0,122,255,0.20)",
                  color: "#007AFF",
                }}
              >
                <div className="w-1.5 h-1.5 rounded-full pulse-dot" style={{ background: "#007AFF" }} />
                生成中
              </div>
            )}

            {/* Scenarios */}
            <button
              type="button"
              onClick={() => { setScenariosOpen(true); void refreshScenarios(); }}
              className="flex items-center gap-1.5 h-7 px-2.5 rounded-[8px] text-[12px] font-medium transition-all duration-150"
              style={{ color: "rgba(0,0,0,0.42)" }}
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.06)";
                (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.72)";
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLElement).style.background = "transparent";
                (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.42)";
              }}
            >
              <Layers size={13} />
              <span className="hidden sm:inline">意图场景</span>
              {scenarios.length > 0 && (
                <span
                  className="text-[10px] rounded-full px-1.5 py-px leading-none"
                  style={{
                    background: "rgba(0,122,255,0.12)",
                    color: "#007AFF",
                  }}
                >
                  {scenarios.length}
                </span>
              )}
            </button>

            {/* More menu */}
            {activeId && (
              <div className="relative" ref={menuRef}>
                <button
                  type="button"
                  onClick={() => setMenuOpen((v) => !v)}
                  className="w-8 h-8 rounded-[8px] flex items-center justify-center transition-all duration-150"
                  style={{ color: "rgba(0,0,0,0.40)" }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.06)";
                    (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.72)";
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLElement).style.background = "transparent";
                    (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.40)";
                  }}
                >
                  <MoreHorizontal size={15} />
                </button>

                {menuOpen && (
                  <div
                    className="absolute right-0 top-full mt-1.5 w-44 rounded-[12px] overflow-hidden animate-fade-in z-40"
                    style={{
                      background: "#FFFFFF",
                      border: "1px solid rgba(0,0,0,0.09)",
                      boxShadow: "0 8px 32px rgba(0,0,0,0.12)",
                    }}
                  >
                    <button
                      type="button"
                      onClick={() => void onRename()}
                      className="w-full flex items-center gap-2.5 px-3.5 py-2.5 text-[13px] transition-colors"
                      style={{ color: "rgba(0,0,0,0.75)" }}
                      onMouseEnter={(e) => (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.04)"}
                      onMouseLeave={(e) => (e.currentTarget as HTMLElement).style.background = "transparent"}
                    >
                      <Pencil size={13} />
                      重命名对话
                    </button>
                    <div style={{ height: 1, background: "rgba(0,0,0,0.07)", margin: "0 12px" }} />
                    <button
                      type="button"
                      onClick={() => void onDeleteActive()}
                      className="w-full flex items-center gap-2.5 px-3.5 py-2.5 text-[13px] transition-colors"
                      style={{ color: "#FF3B30" }}
                      onMouseEnter={(e) => (e.currentTarget as HTMLElement).style.background = "rgba(255,59,48,0.06)"}
                      onMouseLeave={(e) => (e.currentTarget as HTMLElement).style.background = "transparent"}
                    >
                      <Trash2 size={13} />
                      删除对话
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </header>

        {/* Error bar */}
        {error && (
          <div
            className="flex items-center gap-3 mx-4 mt-3 px-4 py-2.5 rounded-[12px] animate-fade-in"
            style={{
              background: "rgba(255,59,48,0.07)",
              border: "1px solid rgba(255,59,48,0.18)",
            }}
          >
            <AlertCircle size={14} style={{ color: "#FF3B30", flexShrink: 0 }} />
            <span className="flex-1 text-[12.5px]" style={{ color: "rgba(200,0,0,0.88)" }}>{error}</span>
            <button
              type="button"
              onClick={() => setError(null)}
              style={{ color: "rgba(200,0,0,0.50)", flexShrink: 0 }}
              onMouseEnter={(e) => (e.currentTarget as HTMLElement).style.color = "#FF3B30"}
              onMouseLeave={(e) => (e.currentTarget as HTMLElement).style.color = "rgba(200,0,0,0.50)"}
            >
              <X size={13} />
            </button>
          </div>
        )}

        {/* Chat area */}
        <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
          {!activeId ? (
            <WelcomeScreen onNewChat={() => void onNewChat()} />
          ) : (
            <>
              <div className="flex-1 overflow-y-auto">
                {lastAnalysis && (
                  <div className="mx-auto max-w-3xl px-4 pt-5">
                    <NluAnalysisCard analysis={lastAnalysis} />
                  </div>
                )}
                <MessageList
                  messages={messages}
                  streamingText={streamingText}
                  streamingThinkText={streamingThinkText}
                  isThinking={isThinking}
                  busy={busy}
                  sessionId={activeId}
                  streamingSteps={streamingSteps}
                />
              </div>
              <InputBox
                value={draft}
                onChange={setDraft}
                onSend={() => void onSend()}
                busy={busy}
                disabled={false}
                thinkingMode={thinkingMode}
                onThinkingModeChange={setThinkingMode}
              />
            </>
          )}
        </div>
      </div>

      {scenariosOpen && (
        <ScenariosModal scenarios={scenarios} onClose={() => setScenariosOpen(false)} />
      )}
    </div>
  );
}
