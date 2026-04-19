import { type FC, type ReactNode } from "react";
import { cn } from "../lib/utils";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size    = "xs" | "sm" | "md" | "lg" | "icon";

interface ButtonProps {
  children?: ReactNode;
  variant?: Variant;
  size?: Size;
  onClick?: () => void;
  disabled?: boolean;
  className?: string;
  title?: string;
  type?: "button" | "submit" | "reset";
}

export const Button: FC<ButtonProps> = ({
  children,
  variant = "secondary",
  size = "md",
  onClick,
  disabled,
  className,
  title,
  type = "button",
}) => {
  const base = "inline-flex items-center justify-center font-medium select-none transition-all duration-150 cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed";

  const variants: Record<Variant, string> = {
    primary:   "bg-[#0A84FF] text-white hover:bg-[#0A84FF]/85 active:scale-[0.97] rounded-[10px]",
    secondary: "bg-white/[0.07] text-white/80 border border-white/[0.10] hover:bg-white/[0.11] hover:text-white active:scale-[0.97] rounded-[10px]",
    ghost:     "text-white/55 hover:text-white/85 hover:bg-white/[0.06] rounded-[8px]",
    danger:    "text-[#FF453A] hover:bg-[#FF453A]/10 border border-transparent hover:border-[#FF453A]/20 rounded-[8px]",
  };

  const sizes: Record<Size, string> = {
    xs:   "h-6 px-2.5 text-[11px] gap-1",
    sm:   "h-7 px-3 text-[12.5px] gap-1.5",
    md:   "h-8 px-4 text-[13px] gap-2",
    lg:   "h-9 px-5 text-[14px] gap-2",
    icon: "h-8 w-8 text-[13px]",
  };

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      title={title}
      className={cn(base, variants[variant], sizes[size], className)}
    >
      {children}
    </button>
  );
};
