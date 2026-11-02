// 服務層：優先呼叫真實後端，後端沒開時自動改用前端內建示範資料。
import client from "./client";
import { MOCK_ROUTES, MOCK_USERS } from "../data/mockData";

// 部署示範模式：設 VITE_DEMO_ONLY=true 時，前端完全用內建資料，不打後端
const DEMO_ONLY = import.meta.env.VITE_DEMO_ONLY === "true";
// 後端出錯時是否偷偷改用內建資料：本機開發沒開後端時方便，但正式串接後端的站台必須關掉
// （VITE_MOCK_FALLBACK=false），否則後端一慢就會變成假登入、假訂單，看起來像有串接其實沒有。
const MOCK_FALLBACK = DEMO_ONLY || import.meta.env.VITE_MOCK_FALLBACK !== "false";

// ---------- 後端在線時走這裡；連不到時 fallback ----------
async function withFallbackAny(real, mock) {
  if (DEMO_ONLY) return mock();
  if (!MOCK_FALLBACK) return real();
  try { return await real(); }
  catch (e) { if (e.status && e.status >= 400 && e.status < 500 && e.status !== 404) throw e; return mock(); }
}

async function withFallback(real, mock) {
  if (DEMO_ONLY) return mock();
  if (!MOCK_FALLBACK) return real();
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

  // 相似行程推薦：標籤重疊數（權重 2）＋同國家（權重 1），demo 模式在前端算同一套邏輯
  getSimilarRoutes(slug, limit = 3) {
    return withFallbackAny(
      async () => (await client.get(`/routes/${slug}/similar`, { params: { limit } })).data,
      () => {
        const base = MOCK_ROUTES.find((x) => x.slug === slug);
        if (!base) return [];
        const splitTags = (s) => new Set(String(s || "").split(",").map((t) => t.trim()).filter(Boolean));
        const baseTags = splitTags(base.tags);
        return MOCK_ROUTES
          .filter((r) => r.slug !== slug)
          .map((r) => {
            const tags = splitTags(r.tags);
            let score = [...tags].filter((t) => baseTags.has(t)).length * 2;
            if (r.country && base.country && r.country.toLowerCase() === base.country.toLowerCase()) score += 1;
            return { r, score };
          })
          .filter((x) => x.score > 0)
          .sort((a, b) => b.score - a.score || b.r.rating - a.r.rating)
          .slice(0, limit)
          .map((x) => x.r);
      }
    );
  },

  // 評價：只有付過款的會員能寫；平均分數由後端從評價表重算
  getReviews(slug) {
    return withFallbackAny(
      async () => (await client.get(`/routes/${slug}/reviews`)).data,
      () => ({ average: 0, count: 0, distribution: [0, 0, 0, 0, 0], items: [] })
    );
  },
  getReviewEligibility(slug) {
    return withFallbackAny(
      async () => (await client.get(`/routes/${slug}/reviews/eligibility`)).data,
      () => ({ canReview: false, reason: "示範模式不支援評價", myRating: null, myContent: null })
    );
  },
  async saveMyReview(slug, { rating, content }) {
    return (await client.put(`/routes/${slug}/reviews/mine`, { rating, content })).data;
  },
  async deleteMyReview(slug) {
    return (await client.delete(`/routes/${slug}/reviews/mine`)).data;
  },
  getLatestReviews(limit = 3) {
    return withFallbackAny(
      async () => (await client.get("/reviews/latest", { params: { limit } })).data,
      () => []
    );
  },

  // 優惠碼：結帳頁套用到待付款訂單，折抵金額一律由後端計算
  async applyCoupon(bookingId, code) {
    return (await client.put(`/bookings/${bookingId}/coupon`, { code })).data;
  },
  async removeCoupon(bookingId) {
    return (await client.delete(`/bookings/${bookingId}/coupon`)).data;
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
        // 新訂單為「待付款」，要走結帳流程付款後才變「已付款」
        const booking = { id: Date.now(), people, travelDate, status: "PENDING",
                          totalPrice: route.price * people, route };
        const list = scopedGet("voyago_mock_bookings", []);
        list.unshift(booking); scopedSet("voyago_mock_bookings", list);
        return booking;
      }
    );
  },

  // 收藏：優先打後端持久化，離線 / demo 時退回 localStorage（跟 favorites store 共用同一套 key）
  getFavorites() {
    return withFallback(
      async () => (await client.get("/favorites")).data,
      () => scopedGet("voyago_favorites", []).map((routeId) => ({ routeId }))
    );
  },

  addFavorite(routeId) {
    return withFallback(
      async () => (await client.post(`/favorites/${routeId}`)).data,
      () => {
        const ids = scopedGet("voyago_favorites", []);
        if (!ids.includes(routeId)) ids.push(routeId);
        scopedSet("voyago_favorites", ids);
        return { routeId };
      }
    );
  },

  removeFavorite(routeId) {
    return withFallback(
      async () => (await client.delete(`/favorites/${routeId}`)).data,
      () => {
        scopedSet("voyago_favorites", scopedGet("voyago_favorites", []).filter((id) => id !== routeId));
        return { ok: true };
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

  // 結帳付款（示範模式為模擬付款，不會真的扣款）
  payBooking(id, method = "CARD") {
    return withFallbackAny(
      async () => (await client.post(`/bookings/${id}/pay`, { method })).data,
      () => {
        const list = scopedGet("voyago_mock_bookings", []);
        const b = list.find((x) => x.id === id);
        if (!b) { const e = new Error("找不到訂單"); e.status = 404; throw e; }
        b.status = "CONFIRMED";
        b.paymentMethod = method;
        b.paidAt = new Date().toISOString();
        scopedSet("voyago_mock_bookings", list);
        return b;
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
                    role: payload.role || "MEMBER", provider: "LOCAL", avatarUrl: null,
                    createdAt: new Date().toISOString() };
        users.push(u); LS.set("voyago_mock_users", users);
        return publicUser(u);
      }
    );
  },

  // 後台儀表板統計（STAFF 用）。demo 模式沒有真的跨帳號資料庫，
  // 改成掃這個瀏覽器裡所有帳號的 mock 訂單資料做彙總，至少能呈現真實的統計邏輯。
  getAdminStats() {
    return withFallbackAny(
      async () => (await client.get("/admin/stats")).data,
      () => {
        const allBookings = [];
        for (let i = 0; i < localStorage.length; i++) {
          const key = localStorage.key(i);
          if (key && key.startsWith("voyago_mock_bookings")) {
            allBookings.push(...LS.get(key, []));
          }
        }
        const byStatus = (s) => allBookings.filter((b) => b.status === s).length;
        const revenue = allBookings.filter((b) => b.status === "CONFIRMED").reduce((sum, b) => sum + (b.totalPrice || 0), 0);
        const now = new Date();
        const newThisMonth = LS.get("voyago_mock_users", []).filter((u) => {
          if (!u.createdAt) return false;
          const d = new Date(u.createdAt);
          return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth();
        }).length;
        return {
          totalMembers: mockUsers().length,
          newMembersThisMonth: newThisMonth,
          pendingBookings: byStatus("PENDING"),
          confirmedBookings: byStatus("CONFIRMED"),
          cancelledBookings: byStatus("CANCELLED"),
          totalRevenue: revenue,
        };
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

  // ========== 後台資料管理（STAFF 用）：只打真實後端，示範模式沒有可寫入的資料庫 ==========
  adminCreateRoute(payload) {
    return adminOnly(async () => (await client.post("/admin/routes", payload)).data);
  },
  adminUpdateRoute(id, payload) {
    return adminOnly(async () => (await client.put(`/admin/routes/${id}`, payload)).data);
  },
  adminDeleteRoute(id) {
    return adminOnly(async () => (await client.delete(`/admin/routes/${id}`)).data);
  },
  adminListBookings({ status = "", page = 0, size = 10 } = {}) {
    return adminOnly(async () => (await client.get("/admin/bookings", { params: { status, page, size } })).data);
  },
  adminUpdateBookingStatus(id, status) {
    return adminOnly(async () => (await client.put(`/admin/bookings/${id}/status`, { status })).data);
  },

  // ========== AI 行程顧問：一律打真實後端（示範模式沒有 AI，不用假資料冒充） ==========
  aiStatus() {
    if (DEMO_ONLY) return Promise.resolve({ enabled: false, dailyLimit: 0 });
    return client.get("/ai/status").then((r) => r.data);
  },
  askAdvisor(query) {
    if (DEMO_ONLY) return Promise.reject(new Error("示範模式沒有連接 AI 服務"));
    // AI 回答可能要 10–20 秒，加上免費主機剛喚醒，逾時給寬一點
    return client.post("/ai/advisor", { query }, { timeout: 90000 }).then((r) => r.data);
  },
};

function adminOnly(real) {
  if (DEMO_ONLY) return Promise.reject(new Error("示範模式沒有連接後端資料庫，無法管理資料"));
  return real();
}

export default api;
