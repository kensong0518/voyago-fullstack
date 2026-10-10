import { defineStore } from "pinia";
import api from "../api/api";

// 收藏按帳號隔離：登入後用 voyago_favorites::<email>，未登入用共用 key（訪客）。
// localStorage 永遠是立刻可用的本地副本（離線、demo 模式都靠它），登入後再背景跟後端同步，
// 讓收藏真的能跨裝置——跟專案本來的「優雅降級」精神一致：連不上後端完全不影響操作。
const BASE = "voyago_favorites";
const keyFor = (email) => (email ? `${BASE}::${email}` : BASE);
const load = (key) => { try { return JSON.parse(localStorage.getItem(key) || "[]"); } catch { return []; } };

export const useFavoritesStore = defineStore("favorites", {
  state: () => ({
    ids: load(BASE),
    _key: BASE,
  }),
  getters: {
    count: (s) => s.ids.length,
    has: (s) => (id) => s.ids.includes(id),
  },
  actions: {
    // 登入/登出時切換到該帳號自己的收藏清單（App.vue 監聽 auth 變化呼叫）。
    // 首次登入會把訪客時期的收藏帶進帳號，避免登入後收藏「消失」。
    bindUser(email) {
      const key = keyFor(email);
      if (key === this._key) return;
      if (email && localStorage.getItem(key) === null && localStorage.getItem(BASE) !== null) {
        localStorage.setItem(key, localStorage.getItem(BASE));
        localStorage.removeItem(BASE);
      }
      this._key = key;
      this.ids = load(key);

      // 登入時背景跟後端對一次：後端有資料就用後端的（換裝置也看得到），
      // 連不上就維持本地副本——不等待、不擋畫面。
      if (email) {
        api.getFavorites()
          .then((list) => {
            this.ids = list.map((f) => f.routeId ?? f.route?.id);
            localStorage.setItem(this._key, JSON.stringify(this.ids));
          })
          .catch(() => {}); // 離線 / demo 模式：維持本地副本即可
      }
    },
    toggle(id) {
      const i = this.ids.indexOf(id);
      const adding = i < 0;
      if (adding) this.ids.push(id);
      else this.ids.splice(i, 1);
      localStorage.setItem(this._key, JSON.stringify(this.ids));

      // 畫面已經樂觀更新了，背景同步到後端；失敗也不影響這次操作的結果
      // （下次登入 bindUser 重新對一次，或 withFallback 本身就會退回本地資料）。
      const sync = adding ? api.addFavorite(id) : api.removeFavorite(id);
      sync.catch(() => {});
    },
  },
});
