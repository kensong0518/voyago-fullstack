import { defineStore } from "pinia";

// 行程比較：最多 3 個，存在 localStorage（重新整理、換頁都還在）。
// 只存顯示比較列需要的少量欄位；比較頁會重新向後端取最新資料，不用這裡的舊值。
const KEY = "voyago_compare";
export const COMPARE_MAX = 3;

function load() {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) || "[]");
    return Array.isArray(v) ? v.filter((x) => x && x.slug).slice(0, COMPARE_MAX) : [];
  } catch { return []; }
}
function save(items) {
  try { localStorage.setItem(KEY, JSON.stringify(items)); } catch { /* 無痕模式等情況存不了就算了 */ }
}

export const useCompareStore = defineStore("compare", {
  state: () => ({ items: load(), notice: "" }),
  getters: {
    count: (s) => s.items.length,
    has: (s) => (slug) => s.items.some((x) => x.slug === slug),
    slugs: (s) => s.items.map((x) => x.slug),
  },
  actions: {
    toggle(route) {
      if (this.has(route.slug)) { this.remove(route.slug); return; }
      if (this.items.length >= COMPARE_MAX) {
        this.flash(`最多比較 ${COMPARE_MAX} 個行程，請先移除一個`);
        return;
      }
      this.items.push({ slug: route.slug, name: route.name, imageUrl: route.imageUrl });
      save(this.items);
    },
    remove(slug) {
      this.items = this.items.filter((x) => x.slug !== slug);
      save(this.items);
    },
    clear() { this.items = []; save(this.items); },
    flash(msg) {
      this.notice = msg;
      clearTimeout(this._t);
      this._t = setTimeout(() => (this.notice = ""), 2500);
    },
  },
});
