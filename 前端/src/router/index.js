import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../stores/auth";
import { SITE_NAME } from "../config/site";

const routes = [
  { path: "/", name: "home", component: () => import("../views/HomeView.vue") },
  { path: "/tours", name: "tours", component: () => import("../views/ToursView.vue"), meta: { title: "探索行程" } },
  { path: "/tours/:slug", name: "tour-detail", component: () => import("../views/TourDetailView.vue") },
  { path: "/compare", name: "compare", component: () => import("../views/CompareView.vue"), meta: { title: "行程比較" } },
  { path: "/login", name: "login", component: () => import("../views/LoginView.vue"), meta: { title: "登入" } },
  { path: "/register", name: "register", component: () => import("../views/RegisterView.vue"), meta: { title: "免費註冊" } },
  { path: "/dashboard", name: "dashboard", component: () => import("../views/DashboardView.vue"), meta: { auth: true, title: "會員中心" } },
  { path: "/checkout/:id", name: "checkout", component: () => import("../views/CheckoutView.vue"), meta: { auth: true, title: "結帳" } },
  { path: "/chat", name: "chat", component: () => import("../views/ChatView.vue"), meta: { auth: true, title: "線上客服" } },
  { path: "/admin", name: "admin", component: () => import("../views/AdminView.vue"), meta: { auth: true, staff: true, title: "管理中心" } },
  { path: "/:pathMatch(.*)*", name: "not-found", component: () => import("../views/NotFoundView.vue"), meta: { title: "找不到頁面" } },
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
  // 後台只給管理員；真正的權限仍由後端 @PreAuthorize 把關，這裡只是不讓一般會員看到空殼畫面
  if (to.meta.staff && !auth.isStaff) {
    return { name: "dashboard" };
  }
});

// 分頁標題跟著頁面變；行程詳情頁載入後再自己換成行程名稱
router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title}｜${SITE_NAME}` : `${SITE_NAME} — 你的歐洲旅程從這裡開始`;
});

export default router;
