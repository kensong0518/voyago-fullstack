import { defineStore } from "pinia";

const KEY = "voyago_favorites";

export const useFavoritesStore = defineStore("favorites", {
  state: () => ({
    ids: JSON.parse(localStorage.getItem(KEY) || "[]"),
  }),
  getters: {
    count: (s) => s.ids.length,
    has: (s) => (id) => s.ids.includes(id),
  },
  actions: {
    toggle(id) {
      const i = this.ids.indexOf(id);
      if (i >= 0) this.ids.splice(i, 1);
      else this.ids.push(id);
      localStorage.setItem(KEY, JSON.stringify(this.ids));
    },
  },
});
