import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";

const routes = [
  { path: "/", name: "home", component: () => import("../views/HomeView.vue") },
  { path: "/tours", name: "tours", component: () => import("../views/ToursView.vue") },
  { path: "/tours/:slug", name: "tour-detail", component: () => import("../views/TourDetailView.vue") },
  { path: "/login", name: "login", component: () => import("../views/LoginView.vue") },
  { path: "/register", name: "register", component: () => import("../views/RegisterView.vue") },
  { path: "/dashboard", name: "dashboard", component: () => import("../views/DashboardView.vue"), meta: { auth: true } },
  { path: "/checkout/:id", name: "checkout", component: () => import("../views/CheckoutView.vue"), meta: { auth: true } },
  { path: "/chat", name: "chat", component: () => import("../views/ChatView.vue"), meta: { auth: true } },
  { path: "/:pathMatch(.*)*", name: "not-found", component: () => import("../views/NotFoundView.vue") },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() { return { top: 0 }; },
});

// 路由守衛：受保護頁面需登入
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!auth.ready) await auth.fetchMe();
  if (to.meta.auth && !auth.isLoggedIn) {
    return { name: "login", query: { next: to.fullPath } };
  }
});

export default router;
