<script setup>
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";
import GoogleLoginButton from "../components/GoogleLoginButton.vue";

const auth = useAuthStore();
const router = useRouter();

const form = ref({ name: "", email: "", phone: "", password: "" });
const error = ref("");
const loading = ref(false);

// 與後端規則對齊：≥8 碼、含英文 + 數字
const PWD_RE = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/;
const pwdValid = computed(() => PWD_RE.test(form.value.password));
const pwdHint = computed(() => {
  const v = form.value.password;
  if (!v) return "至少 8 碼，需包含英文與數字";
  if (v.length < 8) return `再 ${8 - v.length} 碼`;
  if (!/[A-Za-z]/.test(v)) return "需包含英文字母";
  if (!/\d/.test(v)) return "需包含數字";
  return "✓ 密碼強度足夠";
});

async function submit() {
  error.value = "";
  if (!pwdValid.value) { error.value = "密碼需至少 8 碼，且包含英文與數字"; return; }
  loading.value = true;
  try {
    await auth.register({ ...form.value });
    router.push("/dashboard");
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
        <h1 class="text-2xl font-extrabold">建立你的帳號 ✦</h1>
        <p class="mt-1 text-sm text-ink-500">免費加入會員，開始收藏與預訂歐洲行程。</p>

        <form @submit.prevent="submit" class="mt-6 space-y-4">
          <div>
            <label class="label" for="reg-name">姓名</label>
            <input id="reg-name" required minlength="2" maxlength="50" v-model="form.name"
              autocomplete="name" class="input" placeholder="王小明" />
          </div>
          <div>
            <label class="label" for="reg-email">Email</label>
            <input id="reg-email" type="email" required v-model="form.email"
              autocomplete="email" class="input" placeholder="you@example.com" />
          </div>
          <div>
            <label class="label" for="reg-phone">手機（選填）</label>
            <input id="reg-phone" type="tel" v-model="form.phone" autocomplete="tel"
              class="input" placeholder="0912-345-678" />
          </div>
          <div>
            <label class="label">密碼</label>
            <input type="password" required minlength="8" v-model="form.password" class="input"
              placeholder="至少 8 碼，含英文與數字"
              :aria-invalid="!!form.password && !pwdValid"
              aria-describedby="pwd-hint" autocomplete="new-password" />
            <p id="pwd-hint" class="mt-1 text-xs"
              :class="!form.password ? 'text-ink-400' : pwdValid ? 'text-emerald-600' : 'text-amber-600'">
              {{ pwdHint }}
            </p>
          </div>
          <p v-if="error" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600" role="alert">{{ error }}</p>
          <button :disabled="loading || !pwdValid" class="btn-primary w-full py-3">{{ loading ? "註冊中…" : "註冊" }}</button>
        </form>

        <div class="my-5 flex items-center gap-3 text-xs text-ink-400">
          <div class="h-px flex-1 bg-ink-100" />或<div class="h-px flex-1 bg-ink-100" />
        </div>
        <GoogleLoginButton />

        <p class="mt-5 text-center text-sm text-ink-500">
          已經有帳號了？<RouterLink to="/login" class="font-semibold text-brand-700 hover:underline">前往登入</RouterLink>
        </p>
      </div>
    </div>
  </div>
</template>
