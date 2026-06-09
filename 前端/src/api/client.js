import axios from "axios";

// AJAX 用的 axios 實例 —— 所有前端對後端的請求都走這裡
const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || "/api",
  headers: { "Content-Type": "application/json" },
  timeout: 10000,
});

// 請求攔截器：自動帶上 JWT
client.interceptors.request.use((config) => {
  const token = localStorage.getItem("voyago_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 回應攔截器：401 自動登出、統一錯誤訊息、標記網路錯誤
client.interceptors.response.use(
  (res) => res,
  (err) => {
    // 使用者主動取消（unmount 觸發 AbortController）不視為錯誤
    if (axios.isCancel(err) || err.code === "ERR_CANCELED") {
      const e = new Error("已取消");
      e.canceled = true;
      return Promise.reject(e);
    }

    const status = err.response?.status;

    // 401：token 失效 → 清掉本地 token 並導去登入頁（避免在登入頁本身觸發循環）
    if (status === 401 && typeof window !== "undefined") {
      const onAuthPage = /^\/(login|register)/.test(window.location.pathname);
      localStorage.removeItem("voyago_token");
      if (!onAuthPage) {
        const next = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.assign(`/login?next=${next}`);
      }
    }

    const e = new Error(err.response?.data?.error || "連線發生問題，請稍後再試");
    e.isNetwork = !err.response;
    e.status = status;
    return Promise.reject(e);
  }
);

export default client;
