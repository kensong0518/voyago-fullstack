<script setup>
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "../stores/auth";
import GoogleLoginButton from "../components/GoogleLoginButton.vue";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

const email = ref("demo@voyago.com");
const password = ref("password123");
const error = ref("");
const loading = ref(false);

async function submit() {
  error.value = ""; loading.value = true;
  try {
    await auth.login(email.value, password.value);
    router.push(route.query.next || "/dashboard");
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="container-x grid min-h-[70vh] place-items-center py-12">
    <div class="w-full max-w-md">
      <div class="card p-8">
        <h1 class="text-2xl font-extrabold">歡迎回來 👋</h1>
        <p class="mt-1 text-sm text-ink-500">登入以管理你的旅程與訂單。</p>

        <div class="mt-4 rounded-xl bg-brand-50 px-4 py-3 text-xs text-brand-800">
          體驗帳號已預先填入：<b>demo@voyago.com</b> / <b>password123</b>
        </div>

        <form @submit.prevent="submit" class="mt-5 space-y-4">
          <div>
            <label class="label">Email</label>
            <input type="email" required v-model="email" class="input" />
          </div>
          <div>
            <label class="label">密碼</label>
            <input type="password" required v-model="password" class="input" />
          </div>
          <p v-if="error" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{{ error }}</p>
          <button :disabled="loading" class="btn-primary w-full py-3">{{ loading ? "登入中…" : "登入" }}</button>
        </form>

        <div class="my-5 flex items-center gap-3 text-xs text-ink-400">
          <div class="h-px flex-1 bg-ink-100" />或<div class="h-px flex-1 bg-ink-100" />
        </div>
        <GoogleLoginButton />

        <p class="mt-5 text-center text-sm text-ink-500">
          還沒有帳號？<RouterLink to="/register" class="font-semibold text-brand-700 hover:underline">立即註冊</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>
