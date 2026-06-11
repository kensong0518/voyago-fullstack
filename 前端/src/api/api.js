// 服務層：優先呼叫真實後端，後端沒開時自動改用前端內建示範資料。
import client from "./client";
import { MOCK_ROUTES, MOCK_USERS } from "../data/mockData";

// 部署示範模式：設 VITE_DEMO_ONLY=true 時，前端完全用內建資料，不打後端
const DEMO_ONLY = import.meta.env.VITE_DEMO_ONLY === "true";

// ---------- 後端在線時走這裡；連不到時 fallback ----------
async function withFallbackAny(real, mock) {
  if (DEMO_ONLY) return mock();
  try { return await real(); }
  catch (e) { if (e.status && e.status >= 400 && e.status < 500 && e.status !== 404) throw e; return mock(); }
}

async function withFallback(real, mock) {
  if (DEMO_ONLY) return mock();
  try {
    return await real();
  } catch (e) {
    // 後端沒開、逾時、或回 5xx（含 Vite 代理連不到後端時的 500）→ 改用內建示範資料
    if (e.isNetwork || !e.status || e.status >= 500) return mock();
    throw e;                          // 後端正常回應的真錯誤（如 401 帳密錯）→ 照常拋出
  }
}

// ---------- 示範模式用的 localStorage 小型資料庫 ----------
const LS = {
  get(k, d) { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } },
  set(k, v) { localStorage.setItem(k, JSON.stringify(v)); },
};
function mockUsers() { return [...MOCK_USERS, ...LS.get("voyago_mock_users", [])]; }
function currentMockUser() { return LS.get("voyago_mock_current", null); }

// 個人資料（訂單/客服訊息/收藏）按登入帳號隔離：key 加上 email 後綴，
// 每個帳號（含不同 Google 帳號）只看得到自己建立的內容。
// 未登入時用共用 key；帳號首次讀取時把舊版全域資料搬給它（向下相容，舊訂單不消失）。
function scopedKey(base) {
  const email = currentMockUser()?.email;
  return email ? base + "::" + email : base;
}
function scopedGet(base, d) {
  const key = scopedKey(base);
  if (key !== base && localStorage.getItem(key) === null && localStorage.getItem(base) !== null) {
    localStorage.setItem(key, localStorage.getItem(base));
    localStorage.removeItem(base);
  }
  return LS.get(key, d);
}
function scopedSet(base, v) { LS.set(scopedKey(base), v); }
function removeScopedData(email) {
  ["voyago_mock_bookings", "voyago_mock_messages", "voyago_favorites"]
    .forEach((b) => localStorage.removeItem(b + "::" + email));
}
function makeToken(email) { return "mock." + btoa(unescape(encodeURIComponent(email))); }
function publicUser(u) { return { id: u.id, name: u.name, email: u.email, role: u.role, provider: u.provider, avatarUrl: u.avatarUrl }; }

function sortRoutes(list, sort) {
  const a = [...list];
  switch (sort) {
    case "price-asc": return a.sort((x, y) => x.price - y.price);
    case "price-desc": return a.sort((x, y) => y.price - x.price);
    case "rating": return a.sort((x, y) => y.rating - x.rating);
    case "days": return a.sort((x, y) => x.days - y.days);
    default: return a.sort((x, y) => (y.featured - x.featured) || (y.reviews - x.reviews));
  }
}

function decodeJwt(token) {
  try {
    const payload = token.split(".")[1];
    return JSON.parse(decodeURIComponent(escape(atob(payload.replace(/-/g, "+").replace(/_/g, "/")))));
  } catch { return {}; }
}

// =================== 對外 API ===================

export const api = {
  // 行程
  getRoutes({ q = "", tag = "", sort = "featured" } = {}) {
    return withFallback(
      async () => (await client.get("/routes", { params: { q, tag, sort } })).data,
      () => {
        let list = MOCK_ROUTES.filter((r) => {
          const okQ = !q || [r.name, r.country, r.location, r.summary].some((f) => f.toLowerCase().includes(q.toLowerCase()));
          const okTag = !tag || r.tags.includes(tag);
          return okQ && okTag;
        });
        return sortRoutes(list, sort);
      }
    );
  },

  getRoute(slug) {
    return withFallback(
      async () => (await client.get(`/routes/${slug}`)).data,
      () => {
        const r = MOCK_ROUTES.find((x) => x.slug === slug);
        if (!r) { const e = new Error("找不到此行程"); e.status = 404; throw e; }
        return r;
      }
    );
  },

  // 驗證
  login(email, password) {
    return withFallback(
      async () => (await client.post("/auth/login", { email, password })).data,
      () => {
        const u = mockUsers().find((x) => x.email === email && x.password === password);
        if (!u) throw new Error("帳號或密碼錯誤");
        LS.set("voyago_mock_current", publicUser(u));
        return { token: makeToken(email), user: publicUser(u) };
      }
    );
  },

  register(payload) {
    return withFallback(
      async () => (await client.post("/auth/register", payload)).data,
      () => {
        if (mockUsers().some((x) => x.email === payload.email)) throw new Error("此 Email 已被註冊");
        const users = LS.get("voyago_mock_users", []);
        const u = { id: Date.now(), name: payload.name, email: payload.email, password: payload.password,
                    role: "MEMBER", provider: "LOCAL", avatarUrl: null };
        users.push(u); LS.set("voyago_mock_users", users);
        LS.set("voyago_mock_current", publicUser(u));
        return { token: makeToken(u.email), user: publicUser(u) };
      }
    );
  },

  // Google 登入：有後端就驗證；沒後端就用前端解碼憑證（示範用）
  googleLogin(credential) {
    return withFallback(
      async () => (await client.post("/auth/google", { credential })).data,
      () => {
        const p = decodeJwt(credential);
        const u = { id: Date.now(), name: p.name || p.email || "Google 使用者", email: p.email || "google@voyago.com",
                    role: "MEMBER", provider: "GOOGLE", avatarUrl: p.picture || null };
        LS.set("voyago_mock_current", u);
        return { token: makeToken(u.email), user: u };
      }
    );
  },

  // 純示範用：沒有設定 Google Client ID 時的「一鍵示範登入」
  demoGoogleLogin() {
    const u = { id: 999, name: "Google 體驗者", email: "google.demo@voyago.com",
                role: "MEMBER", provider: "GOOGLE",
                avatarUrl: "https://lh3.googleusercontent.com/a/default-user=s96-c" };
    LS.set("voyago_mock_current", u);
    return { token: makeToken(u.email), user: u };
  },

  me() {
    return withFallback(
      async () => (await client.get("/auth/me")).data.user,
      () => currentMockUser()
    );
  },

  // 訂單
  getBookings() {
    return withFallback(
      async () => (await client.get("/bookings")).data,
      () => {
        // 取消即刪除：讀取時順手清掉舊版「已取消」的殘留訂單
        const list = scopedGet("voyago_mock_bookings", []);
        const live = list.filter((b) => b.status !== "CANCELLED");
        if (live.length !== list.length) scopedSet("voyago_mock_bookings", live);
        return live;
      }
    );
  },

  createBooking({ routeId, people, travelDate }) {
    return withFallback(
      async () => (await client.post("/bookings", { routeId, people, travelDate })).data,
      () => {
        const route = MOCK_ROUTES.find((r) => r.id === routeId);
        const booking = { id: Date.now(), people, travelDate, status: "CONFIRMED",
                          totalPrice: route.price * people, route };
        const list = scopedGet("voyago_mock_bookings", []);
        list.unshift(booking); scopedSet("voyago_mock_bookings", list);
        return booking;
      }
    );
  },

  // 客服
  getChat() {
    return withFallback(
      async () => (await client.get("/chat")).data,
      () => scopedGet("voyago_mock_messages", [])
    );
  },

  sendChat(content) {
    const AUTO = ["感謝您的訊息！客服專員會盡快為您處理 😊", "好的，已收到您的需求，我們為您查詢後回覆。",
                  "這個行程目前還有名額喔，需要我幫您保留嗎？", "您可以在「會員中心」查看所有訂單狀態唷。"];
    return withFallback(
      async () => (await client.post("/chat", { content })).data,
      () => {
        const list = scopedGet("voyago_mock_messages", []);
        list.push({ id: Date.now(), sender: "MEMBER", content, createdAt: new Date().toISOString() });
        list.push({ id: Date.now() + 1, sender: "STAFF", content: AUTO[Math.floor(Math.random() * AUTO.length)], createdAt: new Date().toISOString() });
        scopedSet("voyago_mock_messages", list);
        return list;
      }
    );
  },

  updateProfile(payload) {
    return withFallbackAny(
      async () => (await client.put("/members/me", payload)).data,
      () => {
        const cur = currentMockUser() || {};
        const updated = { ...cur, name: payload.name ?? cur.name };
        LS.set("voyago_mock_current", updated);
        // 同步更新 mock 使用者清單
        const users = LS.get("voyago_mock_users", []);
        const idx = users.findIndex((u) => u.email === updated.email);
        if (idx >= 0) { users[idx] = { ...users[idx], ...payload }; LS.set("voyago_mock_users", users); }
        return updated;
      }
    );
  },

  cancelBooking(id) {
    return withFallbackAny(
      async () => (await client.delete(`/bookings/${id}`)).data,
      () => {
        // 取消即刪除，訂單列表不再顯示
        const list = scopedGet("voyago_mock_bookings", []).filter((b) => b.id !== id);
        scopedSet("voyago_mock_bookings", list);
        return { ok: true };
      }
    );
  },

  // ========== 會員管理（STAFF 用） ==========
  listMembers({ q = "", page = 0, size = 20 } = {}) {
    return withFallbackAny(
      async () => (await client.get("/members", { params: { q, page, size } })).data,
      () => {
        const all = mockUsers();
        const filtered = q ? all.filter((u) => [u.name, u.email, u.phone].some((f) => (f || "").toLowerCase().includes(q.toLowerCase()))) : all;
        const start = page * size;
        return {
          content: filtered.slice(start, start + size).map(publicUser),
          page, size, totalElements: filtered.length,
          totalPages: Math.ceil(filtered.length / size),
        };
      }
    );
  },

  createMember(payload) {
    return withFallbackAny(
      async () => (await client.post("/members", payload)).data,
      () => {
        const users = LS.get("voyago_mock_users", []);
        if ([...MOCK_USERS, ...users].some((u) => u.email === payload.email))
          throw new Error("此 Email 已被註冊");
        const u = { id: Date.now(), name: payload.name, email: payload.email,
                    phone: payload.phone || null, password: payload.password,
                    role: payload.role || "MEMBER", provider: "LOCAL", avatarUrl: null };
        users.push(u); LS.set("voyago_mock_users", users);
        return publicUser(u);
      }
    );
  },

  deleteMember(id) {
    return withFallbackAny(
      async () => (await client.delete(`/members/${id}`)).data,
      () => {
        const all = LS.get("voyago_mock_users", []);
        const target = all.find((u) => u.id === id);
        if (target) removeScopedData(target.email);
        LS.set("voyago_mock_users", all.filter((u) => u.id !== id));
        return { ok: true };
      }
    );
  },

  // 使用者刪除自己的帳號
  deleteMe() {
    return withFallbackAny(
      async () => (await client.delete("/members/me")).data,
      () => {
        const cur = currentMockUser();
        if (cur) {
          const users = LS.get("voyago_mock_users", []).filter((u) => u.email !== cur.email);
          LS.set("voyago_mock_users", users);
          removeScopedData(cur.email);
        }
        localStorage.removeItem("voyago_mock_current");
        return { ok: true };
      }
    );
  },

  clearMockSession() { localStorage.removeItem("voyago_mock_current"); },
};

export default api;
