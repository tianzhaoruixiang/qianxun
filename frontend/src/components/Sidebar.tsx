import { type FC } from "react";
import { MessageSquare, Plus, Trash2, BrainCircuit } from "lucide-react";
import { type ChatSession } from "../api";
import { formatTime, cn } from "../lib/utils";

interface SidebarProps {
  sessions: ChatSession[];
  activeId: string | null;
  onNewChat: () => void;
  onSelectSession: (id: string) => void;
  onDeleteSession: (id: string) => void;
}

export const Sidebar: FC<SidebarProps> = ({
  sessions,
  activeId,
  onNewChat,
  onSelectSession,
  onDeleteSession,
}) => {
  return (
    <aside
      className="flex flex-col h-full w-64 shrink-0 overflow-hidden"
      style={{ background: "#FFFFFF", borderRight: "1px solid rgba(0,0,0,0.08)" }}
    >
      {/* Brand */}
      <div className="flex items-center gap-3 px-4 pt-5 pb-4">
        <div
          className="w-8 h-8 rounded-[10px] flex items-center justify-center shrink-0"
          style={{ background: "#007AFF" }}
        >
          <BrainCircuit size={15} className="text-white" />
        </div>
        <div className="min-w-0">
          <div
            className="text-[14px] font-semibold tracking-tight"
            style={{ color: "rgba(0,0,0,0.82)", letterSpacing: "-0.02em" }}
          >
            千寻
          </div>
          <div className="text-[10.5px] mt-0.5 truncate" style={{ color: "rgba(0,0,0,0.40)" }}>
            智能体驱动数据问答
          </div>
        </div>
      </div>

      {/* New chat */}
      <div className="px-3 pb-3">
        <button
          type="button"
          onClick={onNewChat}
          className="w-full flex items-center justify-center gap-2 h-8 rounded-[10px] text-[13px] font-medium transition-all duration-150 active:scale-[0.97]"
          style={{
            background: "#007AFF",
            color: "#ffffff",
          }}
        >
          <Plus size={14} />
          新建对话
        </button>
      </div>

      <div style={{ height: 1, background: "rgba(0,0,0,0.07)", margin: "0 12px" }} />

      {/* Sessions */}
      <nav className="flex-1 overflow-y-auto px-2 py-2 space-y-px">
        {sessions.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-10 gap-2" style={{ color: "rgba(0,0,0,0.25)" }}>
            <MessageSquare size={24} strokeWidth={1.5} />
            <span className="text-xs">暂无对话</span>
          </div>
        ) : (
          sessions.map((s) => (
            <SessionItem
              key={s.id}
              session={s}
              active={s.id === activeId}
              onSelect={() => onSelectSession(s.id)}
              onDelete={() => onDeleteSession(s.id)}
            />
          ))
        )}
      </nav>

      {/* Footer — Doris 连接状态指示 */}
      <div style={{ borderTop: "1px solid rgba(0,0,0,0.07)" }} className="px-4 py-2.5">
        <div className="flex items-center gap-1.5">
          <div className="w-1.5 h-1.5 rounded-full shrink-0" style={{ background: "#34C759" }} />
          <span className="text-[10.5px]" style={{ color: "rgba(0,0,0,0.30)" }}>持久化至 Doris</span>
        </div>
      </div>
    </aside>
  );
};

interface SessionItemProps {
  session: ChatSession;
  active: boolean;
  onSelect: () => void;
  onDelete: () => void;
}

const SessionItem: FC<SessionItemProps> = ({ session, active, onSelect, onDelete }) => {
  return (
    <div
      className={cn(
        "group relative flex items-center gap-2.5 rounded-[10px] px-2.5 py-2 cursor-pointer transition-all duration-100",
      )}
      style={{
        background: active ? "rgba(0,122,255,0.10)" : "transparent",
      }}
      onMouseEnter={(e) => {
        if (!active) (e.currentTarget as HTMLElement).style.background = "rgba(0,0,0,0.04)";
      }}
      onMouseLeave={(e) => {
        if (!active) (e.currentTarget as HTMLElement).style.background = "transparent";
      }}
      onClick={onSelect}
    >
      <MessageSquare
        size={12}
        strokeWidth={1.8}
        style={{ color: active ? "#007AFF" : "rgba(0,0,0,0.30)", flexShrink: 0 }}
      />
      <div className="flex-1 min-w-0">
        <div
          className="text-[12.5px] leading-tight truncate transition-colors"
          style={{ color: active ? "rgba(0,0,0,0.88)" : "rgba(0,0,0,0.60)", fontWeight: active ? 500 : 400 }}
        >
          {session.title}
        </div>
        <div className="text-[10px] mt-0.5" style={{ color: "rgba(0,0,0,0.30)" }}>
          {formatTime(session.updatedAt)}
        </div>
      </div>

      <button
        type="button"
        onClick={(e) => { e.stopPropagation(); onDelete(); }}
        className={cn(
          "shrink-0 w-5 h-5 rounded-md flex items-center justify-center transition-all duration-100",
          "opacity-0 group-hover:opacity-100",
        )}
        style={{ color: "rgba(0,0,0,0.28)" }}
        onMouseEnter={(e) => {
          (e.currentTarget as HTMLElement).style.color = "#FF3B30";
          (e.currentTarget as HTMLElement).style.background = "rgba(255,59,48,0.10)";
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLElement).style.color = "rgba(0,0,0,0.28)";
          (e.currentTarget as HTMLElement).style.background = "transparent";
        }}
        title="删除对话"
      >
        <Trash2 size={11} />
      </button>
    </div>
  );
};
