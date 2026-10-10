// 即時客服聊天：STOMP over 原生 WebSocket（不用 SockJS）。
// 設計原則與 api.js 一致——連得上就用 WS 即時推送，連不上（demo 模式、
// 沒登入、網路問題、後端沒開 WS）就讓呼叫端安靜地 fallback 回原本的 REST 輪詢，
// 不丟錯誤中斷畫面。
import { Client } from "@stomp/stompjs";

const DEMO_ONLY = import.meta.env.VITE_DEMO_ONLY === "true";

function buildUrl(token) {
  const proto = window.location.protocol === "https:" ? "wss:" : "ws:";
  const base = import.meta.env.VITE_WS_BASE || `${proto}//${window.location.host}`;
  return `${base}/ws-chat?token=${encodeURIComponent(token)}`;
}

/**
 * 建立聊天用的 STOMP 連線。
 * @param {Object} handlers
 * @param {(messages: Array) => void} handlers.onMessages 收到完整訊息列表時呼叫
 * @param {() => void} [handlers.onUnavailable] 連不上 / demo 模式時呼叫一次，呼叫端應改用 REST
 * @param {() => void} [handlers.onReady] 第一次連線成功時呼叫一次
 * @returns {{ send: (content: string) => boolean, disconnect: () => void }}
 */
export function createChatSocket({ onMessages, onUnavailable, onReady }) {
  const token = localStorage.getItem("voyago_token");

  if (DEMO_ONLY || !token) {
    onUnavailable?.();
    return { send: () => false, disconnect: () => {} };
  }

  let ready = false;          // 目前這條連線是否可以送訊息
  let everConnected = false;  // 曾經連線成功過一次（之後斷線交給 reconnectDelay 自動重連，不再 fallback）
  let gaveUp = false;

  const client = new Client({
    brokerURL: buildUrl(token),
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      ready = true;
      everConnected = true;
      onReady?.();
      client.subscribe("/user/queue/chat", (frame) => {
        try {
          onMessages(JSON.parse(frame.body));
        } catch {
          // 忽略無法解析的訊息
        }
      });
    },
    onStompError: () => fail(),
    onWebSocketError: () => fail(),
    onWebSocketClose: () => {
      ready = false;
      if (!everConnected) fail();
    },
  });

  function fail() {
    if (gaveUp) return;
    gaveUp = true;
    onUnavailable?.();
  }

  client.activate();

  return {
    send(content) {
      if (!ready) return false;
      client.publish({ destination: "/app/chat.send", body: JSON.stringify({ content }) });
      return true;
    },
    disconnect() {
      client.deactivate();
    },
  };
}
