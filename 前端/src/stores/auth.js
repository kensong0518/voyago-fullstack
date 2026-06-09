import { defineStore } from "pinia";
import api from "../api/api";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    user: null,
    token: localStorage.getItem("voyago_token") || null,
    ready: false,
  }),
  getters: {
    isLoggedIn: (s) => !!s.user,
    isStaff: (s) => s.user?.role === "STAFF",
  },
  actions: {
    setSession(token, user) {
      this.token = token;
      this.user = user;
      localStorage.setItem("voyago_token", token);
    },
    async fetchMe() {
      if (!this.token) { this.ready = true; return; }
      try {
        this.user = await api.me();
      } catch {
        this.logout();
      } finally {
        this.ready = true;
      }
    },
    async login(email, password) {
      const data = await api.login(email, password);
      this.setSession(data.token, data.user);
    },
    async register(payload) {
      const data = await api.register(payload);
      this.setSession(data.token, data.user);
    },
    async googleLogin(credential) {
      const data = await api.googleLogin(credential);
      this.setSession(data.token, data.user);
    },
    async demoGoogleLogin() {
      const data = api.demoGoogleLogin();
      this.setSession(data.token, data.user);
    },
    logout() {
      this.user = null;
      this.token = null;
      localStorage.removeItem("voyago_token");
      api.clearMockSession();
    },
    async deleteAccount() {
      await api.deleteMe();
      this.logout();
    },
  },
});
