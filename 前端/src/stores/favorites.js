import { defineStore } from "pinia";

// 收藏按帳號隔離：登入後用 voyago_favorites::<email>，未登入用共用 key（訪客）。
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
    },
    toggle(id) {
      const i = this.ids.indexOf(id);
      if (i >= 0) this.ids.splice(i, 1);
      else this.ids.push(id);
      localStorage.setItem(this._key, JSON.stringify(this.ids));
    },
  },
});
