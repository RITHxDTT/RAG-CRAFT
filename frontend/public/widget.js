(function () {
  "use strict";
  var script = document.currentScript;
  var id = script && script.getAttribute("data-widget-id");
  if (!script || !id || !/^[A-Za-z0-9_-]{20,64}$/.test(id)) return;
  var origin = new URL(script.src).origin;
  var color = script.getAttribute("data-color") || "#0d9488";
  if (!/^#[0-9a-f]{6}$/i.test(color)) color = "#0d9488";
  var left = script.getAttribute("data-position") === "BOTTOM_LEFT";
  var icon = script.getAttribute("data-icon") || "💬";
  var host = document.createElement("div");
  var shadow = host.attachShadow({ mode: "closed" });
  var style = document.createElement("style");
  style.textContent =
    ":host{all:initial;position:fixed;bottom:20px;right:20px;z-index:2147483000}button{border:0;border-radius:100px;padding:16px 22px;background:#0d9488;color:white;font:600 15px system-ui;cursor:pointer;box-shadow:0 6px 24px #0003}button:focus-visible{outline:3px solid #5eead4}iframe{display:none;position:absolute;bottom:66px;right:0;width:min(390px,calc(100vw - 32px));height:min(600px,calc(100dvh - 110px));border:1px solid #e2e8f0;border-radius:18px;background:white;box-shadow:0 12px 50px #0003}";
  var button = document.createElement("button");
  button.type = "button";
  button.textContent = icon + " Chat with us";
  button.style.background = color;
  if (left) { host.style.left = "20px"; host.style.right = "auto"; }
  button.setAttribute("aria-expanded", "false");
  var frame = document.createElement("iframe");
  frame.title = "Knowledge assistant";
  frame.src = origin + "/widget/" + encodeURIComponent(id);
  frame.referrerPolicy = "no-referrer";
  if (left) { frame.style.left = "0"; frame.style.right = "auto"; }
  var open = false;
  button.addEventListener("click", function () {
    open = !open;
    frame.style.display = open ? "block" : "none";
    button.textContent = open ? "Close chat" : icon + " Chat with us";
    button.setAttribute("aria-expanded", String(open));
  });
  shadow.append(style, frame, button);
  document.body.appendChild(host);
})();
