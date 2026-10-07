"use client";
import { useEffect, useRef } from "react";

/**
 * Animated monochrome ribbon landscape, drawn on a fixed full-screen canvas.
 * Colors follow the `.dark` class on <html>. Visual only; no application state.
 */
export function RibbonCanvas() {
  const ref = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let width = 0;
    let height = 0;
    let mouseX = window.innerWidth / 2;
    let mouseY = window.innerHeight / 2;
    let targetX = mouseX;
    let targetY = mouseY;
    let time = 0;
    let frame = 0;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    const resize = () => {
      width = canvas.width = window.innerWidth;
      height = canvas.height = window.innerHeight;
    };
    const move = (event: MouseEvent) => {
      targetX = event.clientX;
      targetY = event.clientY;
    };

    const render = () => {
      ctx.clearRect(0, 0, width, height);
      mouseX += (targetX - mouseX) * 0.04;
      mouseY += (targetY - mouseY) * 0.04;
      time += reduceMotion ? 0 : 0.007;

      const dark = document.documentElement.classList.contains("dark");
      const tone = dark ? 255 : 30;
      const lines = 36;
      const horizon = height * 0.46;

      for (let i = 0; i < lines; i++) {
        ctx.beginPath();
        const progress = i / lines;
        const alpha = (0.03 + progress * 0.075) * (dark ? 1 : 0.65);
        ctx.strokeStyle = `rgba(${tone}, ${tone}, ${tone}, ${alpha})`;
        ctx.lineWidth = 1 + progress * 0.6;
        const baseY = horizon + Math.pow(progress, 1.8) * (height - horizon);
        for (let x = 0; x <= width; x += 18) {
          const normalized = (x - width / 2) / (width / 2);
          const sag = (1 - normalized * normalized) * 45 * Math.sin(progress * Math.PI);
          const wave = Math.sin(x * 0.004 + time + i * 0.2) * (10 + progress * 25);
          const distance = Math.hypot(x - mouseX, baseY - mouseY);
          const bulge = Math.max(0, 1 - distance / 380) * 22;
          const y = baseY + sag + wave + bulge;
          if (x === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();
      }

      const glow = ctx.createRadialGradient(width / 2, horizon * 0.95, 10, width / 2, horizon * 0.95, Math.min(width * 0.45, 450));
      if (dark) {
        glow.addColorStop(0, "rgba(255, 255, 255, 0.12)");
        glow.addColorStop(0.5, "rgba(255, 255, 255, 0.03)");
      } else {
        glow.addColorStop(0, "rgba(15, 23, 42, 0.08)");
        glow.addColorStop(0.5, "rgba(15, 23, 42, 0.02)");
      }
      glow.addColorStop(1, "rgba(0, 0, 0, 0)");
      ctx.fillStyle = glow;
      ctx.fillRect(0, 0, width, height);

      frame = requestAnimationFrame(render);
    };

    resize();
    window.addEventListener("resize", resize);
    window.addEventListener("mousemove", move);
    frame = requestAnimationFrame(render);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", resize);
      window.removeEventListener("mousemove", move);
    };
  }, []);

  return <canvas ref={ref} className="fixed inset-0 h-full w-full pointer-events-none z-0" aria-hidden="true" />;
}
