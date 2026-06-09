<script setup>
import { onMounted, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const btn = ref(null);
const error = ref("");
const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
const configured = clientId && !clientId.startsWith("YOUR_");

function goNext() {
  router.push(route.query.next || "/dashboard");
}

async function handleCredential(response) {
  error.value = "";
  try {
    await auth.googleLogin(response.credential);
    goNext();
  } catch (e) { error.value = e.message; }
}

async function demoLogin() {
  await auth.demoGoogleLogin();
  goNext();
}

onMounted(() => {
  if (!configured) return;
  const init = () => {
    if (!window.google?.accounts?.id) return setTimeout(init, 200);
    window.google.accounts.id.initialize({ client_id: clientId, callback: handleCredential });
    window.google.accounts.id.renderButton(btn.value, {
      theme: "outline", size: "large", width: 320, text: "continue_with", shape: "pill",
    });
  };
  init();
});
</script>

<template>
  <div>
    <!-- 已設定 Google Client ID：顯示官方 Google 按鈕 -->
    <div v-if="configured" ref="btn" class="flex justify-center"></div>

    <!-- 尚未設定：提供示範用的 Google 登入按鈕，讓流程可立即體驗 -->
    <button v-else type="button" @click="demoLogin"
      class="flex w-full items-center justify-center gap-3 rounded-full border border-ink-200 bg-white px-5 py-2.5 text-sm font-semibold text-ink-700 transition hover:bg-ink-50">
      <svg width="18" height="18" viewBox="0 0 48 48">
        <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.4 29.3 35 24 35c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.5 5.1 29.5 3 24 3 12.4 3 3 12.4 3 24s9.4 21 21 21 21-9.4 21-21c0-1.2-.1-2.3-.4-3.5z"/>
        <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 16 19 13 24 13c3.1 0 5.9 1.2 8 3.1l5.7-5.7C34.5 5.1 29.5 3 24 3 16 3 9.1 7.6 6.3 14.7z"/>
        <path fill="#4CAF50" d="M24 45c5.2 0 10-2 13.6-5.2l-6.3-5.3C29.2 35.9 26.7 37 24 37c-5.3 0-9.7-2.6-11.3-7l-6.5 5C9.1 42.3 16 45 24 45z"/>
        <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.1-4 5.5l6.3 5.3C41.8 35.9 45 30.5 45 24c0-1.2-.1-2.3-.4-3.5z"/>
      </svg>
      使用 Google 登入（示範）
    </button>

    <p v-if="error" class="mt-2 text-center text-sm text-red-600">{{ error }}</p>
  </div>
</template>
