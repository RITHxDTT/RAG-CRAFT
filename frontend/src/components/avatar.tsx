"use client";
import type { CSSProperties } from "react";

// Soft, theme-friendly pairs picked deterministically from the person's name.
// Monochrome brand: shades of ink and paper, picked deterministically from the name.
const PALETTE: { bg: string; fg: string }[] = [
  { bg: "#0f172a", fg: "#ffffff" },
  { bg: "#e2e8f0", fg: "#0f172a" },
  { bg: "#334155", fg: "#ffffff" },
  { bg: "#f1f5f9", fg: "#0f172a" },
  { bg: "#64748b", fg: "#ffffff" },
  { bg: "#cbd5e1", fg: "#0f172a" },
  { bg: "#1e293b", fg: "#ffffff" },
  { bg: "#94a3b8", fg: "#0f172a" },
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
