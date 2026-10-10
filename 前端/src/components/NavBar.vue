<script setup>
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
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

// 導覽列目前所在頁面的滑動指示條：量測目前啟用連結的位置，讓指示條滑過去，
// 比單純換色更看得出「這是同一條列，只是移到別的位置」。
const navEl = ref(null);
const linkEls = ref([]);
const indicator = ref({ left: 0, width: 0, ready: false });

function isActive(to) {
  return to === "/" ? route.path === "/" : route.path.startsWith(to);
}

function updateIndicator() {
  const activeIdx = links.findIndex((l) => isActive(l.to));
  const el = linkEls.value[activeIdx];
  if (!el || !navEl.value) { indicator.value = { ...indicator.value, ready: false }; return; }
  const navBox = navEl.value.getBoundingClientRect();
  const elBox = el.getBoundingClientRect();
  indicator.value = { left: elBox.left - navBox.left, width: elBox.width, ready: true };
}

watch(() => route.path, () => nextTick(updateIndicator));
onMounted(() => {
  nextTick(updateIndicator);
  window.addEventListener("resize", updateIndicator);
});
onUnmounted(() => window.removeEventListener("resize", updateIndicator));
</script>

<template>
  <header class="sticky top-0 z-50 border-b border-ink-100 bg-white/80 backdrop-blur">
    <nav class="container-x flex h-16 items-center justify-between">
      <RouterLink to="/" class="flex items-center gap-2 text-lg font-extrabold tracking-tight">
        <span class="grid h-8 w-8 place-items-center rounded-xl bg-brand-600 text-white">歐</span>
        <span>歐洲自助遊</span>
      </RouterLink>

      <div ref="navEl" class="relative hidden items-center gap-1 md:flex">
        <span v-if="indicator.ready" aria-hidden="true"
          class="absolute inset-y-1 rounded-full bg-brand-50 transition-all duration-300 ease-out"
          :style="{ left: indicator.left + 'px', width: indicator.width + 'px' }" />
        <RouterLink v-for="(l, i) in links" :key="l.to" :to="l.to" :ref="(el) => (linkEls[i] = el?.$el ?? el)"
          class="relative rounded-full px-4 py-2 text-sm font-medium text-ink-600 transition hover:text-ink-900"
          active-class="!text-brand-700" exact-active-class="!text-brand-700">
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
