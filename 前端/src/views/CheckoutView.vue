<script setup>
import { onMounted, ref, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../api/api";

const route = useRoute();
const router = useRouter();

const loading = ref(true);
const booking = ref(null);
const error = ref("");
const state = ref("idle");           // idle | paying | done
const method = ref("CARD");          // CARD | ATM | LINEPAY

// 示範付款用的測試卡（國際通用測試卡號，非真實卡片）
const card = ref({ number: "4242 4242 4242 4242", expiry: "12/30", cvc: "123", holder: "" });

const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);
const dateText = (d) => new Date(d).toLocaleDateString("zh-TW");
const orderNo = computed(() => booking.value ? "VG" + String(booking.value.id).slice(-8).padStart(8, "0") : "");

const METHODS = [
  { v: "CARD",    icon: "💳", label: "信用卡",  hint: "VISA / Mastercard / JCB" },
  { v: "ATM",     icon: "🏧", label: "ATM 轉帳", hint: "提供虛擬帳號" },
  { v: "LINEPAY", icon: "🟢", label: "LINE Pay", hint: "跳轉付款（示範）" },
];

onMounted(async () => {
  try {
    const list = await api.getBookings();
    booking.value = list.find((b) => String(b.id) === String(route.params.id)) || null;
    if (booking.value?.status === "CONFIRMED") state.value = "done";   // 已付款過，直接顯示完成
  } finally { loading.value = false; }
});

async function pay() {
  error.value = "";
  if (method.value === "CARD" && !card.value.holder.trim()) {
    error.value = "請輸入持卡人姓名（示範用，可隨意填）"; return;
  }
  state.value = "paying";
  try {
    // 模擬金流處理時間
    await new Promise((r) => setTimeout(r, 1200));
    booking.value = await api.payBooking(booking.value.id, method.value);
    state.value = "done";
  } catch (e) {
    error.value = e.message || "付款失敗，請稍後再試";
    state.value = "idle";
  }
}
</script>

<template>
  <div class="container-x py-10">
    <p v-if="loading" class="py-20 text-center text-ink-400">載入訂單中…</p>

    <!-- 找不到訂單 -->
    <div v-else-if="!booking" class="mx-auto max-w-lg card p-10 text-center">
      <p class="text-5xl">🧾</p>
      <p class="mt-4 text-ink-500">找不到這筆訂單，它可能已被取消。</p>
      <RouterLink to="/dashboard" class="btn-primary mt-6 inline-block">回會員中心</RouterLink>
    </div>

    <!-- 付款完成 -->
    <div v-else-if="state === 'done'" class="mx-auto max-w-lg card p-10 text-center">
      <div class="mx-auto grid h-16 w-16 place-items-center rounded-full bg-brand-50 text-3xl">✅</div>
      <h1 class="mt-4 text-2xl font-extrabold">付款完成！</h1>
      <p class="mt-2 text-ink-500">訂單編號 <span class="font-mono font-bold text-ink-700">{{ orderNo }}</span></p>
      <div class="mt-6 rounded-2xl bg-ink-50 p-4 text-left text-sm">
        <div class="flex justify-between py-1"><span class="text-ink-500">行程</span><span class="font-semibold">{{ booking.route.name }}</span></div>
        <div class="flex justify-between py-1"><span class="text-ink-500">出發日期</span><span>{{ dateText(booking.travelDate) }}</span></div>
        <div class="flex justify-between py-1"><span class="text-ink-500">人數</span><span>{{ booking.people }} 人</span></div>
        <div class="flex justify-between border-t border-ink-100 pt-2 mt-1"><span class="text-ink-500">已付金額</span><span class="font-extrabold text-brand-700">{{ twd(booking.totalPrice) }}</span></div>
      </div>
      <p class="mt-4 text-xs text-ink-400">行程確認信已寄出（示範站不會真的寄信）</p>
      <div class="mt-6 flex justify-center gap-3">
        <RouterLink to="/dashboard" class="btn-primary">查看我的訂單</RouterLink>
        <RouterLink to="/tours" class="rounded-full border border-ink-200 px-5 py-2.5 text-sm font-semibold text-ink-600 hover:bg-ink-50">繼續探索行程</RouterLink>
      </div>
    </div>

    <!-- 結帳 -->
    <div v-else class="mx-auto grid max-w-4xl gap-6 lg:grid-cols-5">
      <div class="lg:col-span-3">
        <h1 class="text-2xl font-extrabold">結帳</h1>
        <p class="mt-1 text-sm text-amber-700">⚠️ 示範付款：不會真的扣款，請勿輸入真實卡號或個資。</p>

        <!-- 付款方式 -->
        <div class="card mt-5 p-5">
          <h2 class="font-bold">付款方式</h2>
          <div class="mt-3 grid gap-2 sm:grid-cols-3">
            <button v-for="m in METHODS" :key="m.v" type="button" @click="method = m.v"
              :class="['rounded-2xl border p-3 text-left transition',
                       method === m.v ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-300' : 'border-ink-200 hover:bg-ink-50']">
              <div class="text-xl">{{ m.icon }}</div>
              <div class="mt-1 text-sm font-bold">{{ m.label }}</div>
              <div class="text-xs text-ink-400">{{ m.hint }}</div>
            </button>
          </div>

          <!-- 信用卡（示範預填測試卡） -->
          <div v-if="method === 'CARD'" class="mt-5 space-y-3">
            <div>
              <label for="card-no" class="text-xs font-semibold text-ink-500">卡號（測試卡，請勿改成真卡）</label>
              <input id="card-no" v-model="card.number" class="input mt-1 w-full font-mono" autocomplete="off" />
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label for="card-exp" class="text-xs font-semibold text-ink-500">有效期限</label>
                <input id="card-exp" v-model="card.expiry" class="input mt-1 w-full font-mono" autocomplete="off" />
              </div>
              <div>
                <label for="card-cvc" class="text-xs font-semibold text-ink-500">安全碼</label>
                <input id="card-cvc" v-model="card.cvc" class="input mt-1 w-full font-mono" autocomplete="off" />
              </div>
            </div>
            <div>
              <label for="card-holder" class="text-xs font-semibold text-ink-500">持卡人姓名（隨意填）</label>
              <input id="card-holder" v-model="card.holder" placeholder="王小明" class="input mt-1 w-full" autocomplete="off" />
            </div>
          </div>

          <!-- ATM -->
          <div v-else-if="method === 'ATM'" class="mt-5 rounded-2xl bg-ink-50 p-4 text-sm">
            <p class="text-ink-500">請於 24 小時內轉帳至以下虛擬帳號（示範用，請勿真的轉帳）：</p>
            <p class="mt-2 font-mono text-lg font-bold tracking-wider">822-9988 7766 5544 3322</p>
            <p class="mt-1 text-xs text-ink-400">按「完成付款」模擬入帳。</p>
          </div>

          <!-- LINE Pay -->
          <div v-else class="mt-5 rounded-2xl bg-ink-50 p-4 text-sm text-ink-500">
            按「完成付款」會模擬 LINE Pay 授權完成（示範站不會真的跳轉）。
          </div>

          <p v-if="error" class="mt-3 text-sm text-rose-600">{{ error }}</p>
          <button @click="pay" :disabled="state === 'paying'"
            class="btn-primary mt-5 w-full disabled:opacity-60">
            {{ state === 'paying' ? '付款處理中…' : `完成付款 ${twd(booking.totalPrice)}` }}
          </button>
        </div>
      </div>

      <!-- 訂單摘要 -->
      <div class="lg:col-span-2">
        <div class="card overflow-hidden">
          <img :src="booking.route.imageUrl" alt="" @error="(e)=>(e.target.style.display='none')" class="h-36 w-full object-cover bg-gradient-to-br from-brand-300 to-brand-600" />
          <div class="p-5 text-sm">
            <h2 class="text-base font-bold">{{ booking.route.name }}</h2>
            <p class="mt-1 text-ink-500">{{ booking.route.country }} · {{ booking.route.days }} 天</p>
            <div class="mt-4 space-y-2 border-t border-ink-100 pt-3">
              <div class="flex justify-between"><span class="text-ink-500">訂單編號</span><span class="font-mono">{{ orderNo }}</span></div>
              <div class="flex justify-between"><span class="text-ink-500">出發日期</span><span>{{ dateText(booking.travelDate) }}</span></div>
              <div class="flex justify-between"><span class="text-ink-500">人數</span><span>{{ booking.people }} 人</span></div>
              <div class="flex justify-between"><span class="text-ink-500">單價</span><span>{{ twd(booking.route.price) }} / 人</span></div>
            </div>
            <div class="mt-3 flex justify-between border-t border-ink-100 pt-3 text-base">
              <span class="font-bold">應付總額</span>
              <span class="font-extrabold text-brand-700">{{ twd(booking.totalPrice) }}</span>
            </div>
          </div>
        </div>
        <RouterLink to="/dashboard" class="mt-3 block text-center text-xs text-ink-400 hover:underline">稍後再付，回會員中心</RouterLink>
      </div>
    </div>
  </div>
</template>
