"use client";
import type { CSSProperties } from "react";

// Soft, theme-friendly pairs picked deterministically from the person's name.
const PALETTE: { bg: string; fg: string }[] = [
  { bg: "#e0f2f1", fg: "#0f766e" },
  { bg: "#e0e7ff", fg: "#4338ca" },
  { bg: "#fef3c7", fg: "#b45309" },
  { bg: "#dbeafe", fg: "#1d4ed8" },
  { bg: "#fce7f3", fg: "#be185d" },
  { bg: "#dcfce7", fg: "#15803d" },
  { bg: "#ede9fe", fg: "#6d28d9" },
  { bg: "#ffedd5", fg: "#c2410c" },
];

function hash(value: string) {
  let total = 0;
  for (const char of value) total = (total * 31 + char.charCodeAt(0)) >>> 0;
  return total;
}

/** First visible character of the name, or of the email when no name is set. */
export function avatarInitial(name?: string, email?: string) {
  const source = (name || "").trim() || (email || "").trim();
  return source ? source.charAt(0).toUpperCase() : "?";
}

export function Avatar({
  name,
  email,
  image,
  size = 32,
  radius,
  className = "",
  title,
}: {
  name?: string;
  email?: string;
  image?: string;
  size?: number;
  radius?: number | string;
  className?: string;
  title?: string;
}) {
  const label = name || email || "";
  const color = PALETTE[hash(label) % PALETTE.length];
  const style: CSSProperties = {
    width: size,
    height: size,
    fontSize: Math.max(11, Math.round(size * 0.42)),
    borderRadius: radius ?? Math.round(size * 0.3),
    ...(image
      ? { backgroundImage: `url(${image})` }
      : { background: color.bg, color: color.fg }),
  };
  return (
    <span
      className={`rc-avatar ${image ? "has-image" : ""} ${className}`}
      style={style}
      role="img"
      aria-label={title || label}
      title={title || label}
    >
      {!image && avatarInitial(name, email)}
    </span>
  );
}
