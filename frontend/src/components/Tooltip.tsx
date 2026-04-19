import { type FC, type ReactNode } from "react";
import { cn } from "../lib/utils";

interface TooltipProps {
  content: ReactNode;
  children: ReactNode;
  className?: string;
}

export const Tooltip: FC<TooltipProps> = ({ content, children, className }) => {
  return (
    <div className={cn("relative group", className)}>
      {children}
      <div className="absolute z-50 bottom-full left-1/2 -translate-x-1/2 mb-2 pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity duration-150">
        <div className="bg-[#1e2435] border border-white/10 rounded-lg px-3 py-2 text-xs text-white/80 whitespace-pre max-w-xs shadow-xl">
          {content}
        </div>
        <div className="w-2 h-2 bg-[#1e2435] border-r border-b border-white/10 rotate-45 mx-auto -mt-1" />
      </div>
    </div>
  );
};
