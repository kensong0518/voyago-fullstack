<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const open = ref(false);

const links = [
  { to: "/", label: "首頁" },
  { to: "/tours", label: "探索行程" },
  { to: "/chat", label: "線上客服" },
];

function logout() {
  auth.logout();
  open.value = false;
  router.push("/");
}
</script>

<template>
  <header class="sticky top-0 z-50 border-b border-ink-100 bg-white/80 backdrop-blur">
    <nav class="container-x flex h-16 items-center justify-between">
      <RouterLink to="/" class="flex items-center gap-2 text-lg font-extrabold tracking-tight">
        <span class="grid h-8 w-8 place-items-center rounded-xl bg-brand-600 text-white">歐</span>
        <span>歐洲自助遊</span>
      </RouterLink>

      <div class="hidden items-center gap-1 md:flex">
        <RouterLink v-for="l in links" :key="l.to" :to="l.to"
          class="rounded-full px-4 py-2 text-sm font-medium text-ink-600 transition hover:text-ink-900"
          active-class="bg-brand-50 !text-brand-700" exact-active-class="bg-brand-50 !text-brand-700">
          {{ l.label }}
        </RouterLink>
      </div>

      <div class="hidden items-center gap-2 md:flex">
        <template v-if="auth.isLoggedIn">
          <RouterLink to="/dashboard" class="btn-ghost">嗨，{{ auth.user.name }}</RouterLink>
          <button class="btn-outline" @click="logout">登出</button>
        </template>
        <template v-else>
          <RouterLink to="/login" class="btn-ghost">登入</RouterLink>
          <RouterLink to="/register" class="btn-primary">免費註冊</RouterLink>
        </template>
      </div>

      <button class="md:hidden rounded-lg p-2 text-ink-700" @click="open = !open"
        :aria-label="open ? '關閉選單' : '開啟選單'" :aria-expanded="open" aria-controls="mobile-menu">
        <svg width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path v-if="!open" d="M4 7h16M4 12h16M4 17h16" />
          <path v-else d="M6 6l12 12M6 18L18 6" />
        </svg>
      </button>
    </nav>

    <div v-if="open" id="mobile-menu" class="border-t border-ink-100 bg-white md:hidden">
      <div class="container-x flex flex-col gap-1 py-3">
        <RouterLink v-for="l in links" :key="l.to" :to="l.to" @click="open = false"
          class="rounded-lg px-3 py-2 text-sm font-medium text-ink-700 hover:bg-ink-50">{{ l.label }}</RouterLink>
        <div class="my-2 h-px bg-ink-100" />
        <template v-if="auth.isLoggedIn">
          <RouterLink to="/dashboard" @click="open = false" class="rounded-lg px-3 py-2 text-sm font-medium text-ink-700 hover:bg-ink-50">會員中心</RouterLink>
          <button @click="logout" class="rounded-lg px-3 py-2 text-left text-sm font-medium text-ink-700 hover:bg-ink-50">登出</button>
        </template>
        <template v-else>
          <RouterLink to="/login" @click="open = false" class="rounded-lg px-3 py-2 text-sm font-medium text-ink-700 hover:bg-ink-50">登入</RouterLink>
          <RouterLink to="/register" @click="open = false" class="rounded-lg px-3 py-2 text-sm font-semibold text-brand-700 hover:bg-brand-50">免費註冊</RouterLink>
        </template>
      </div>
    </div>
  </header>
</template>
