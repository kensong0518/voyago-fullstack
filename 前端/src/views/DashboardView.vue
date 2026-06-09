<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import api from "../api/api";
import { useAuthStore } from "../stores/auth";
import { useFavoritesStore } from "../stores/favorites";
import RouteCard from "../components/RouteCard.vue";

const auth = useAuthStore();
const fav = useFavoritesStore();
const router = useRouter();

const tab = ref("orders");
const bookings = ref([]);
const allRoutes = ref([]);
const busy = ref(true);

const profile = ref({ name: "", phone: "" });
const savingProfile = ref(false);
const profileMsg = ref("");

const STATUS = {
  CONFIRMED: { label: "已確認", cls: "bg-brand-50 text-brand-700" },
  PENDING: { label: "處理中", cls: "bg-amber-50 text-amber-700" },
  CANCELLED: { label: "已取消", cls: "bg-ink-100 text-ink-500" },
};
const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);
const dateText = (d) => new Date(d).toLocaleDateString("zh-TW");

const favoriteRoutes = computed(() => allRoutes.value.filter((r) => fav.has(r.id)));
const activeOrders = computed(() => bookings.value.filter((b) => b.status !== "CANCELLED"));

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe();
  if (!auth.isLoggedIn) { router.push("/login"); return; }
  profile.value.name = auth.user?.name || "";
  profile.value.phone = auth.user?.phone || "";
  try {
    const [b, r] = await Promise.all([api.getBookings(), api.getRoutes({})]);
    bookings.value = b; allRoutes.value = r;
  } finally { busy.value = false; }
});

async function cancel(b) {
  if (!confirm(`確定要取消「${b.route.name}」這筆訂單嗎？`)) return;
  await api.cancelBooking(b.id);
  b.status = "CANCELLED";
}

async function saveProfile() {
  profileMsg.value = ""; savingProfile.value = true;
  try {
    const updated = await api.updateProfile({ name: profile.value.name, phone: profile.value.phone });
    if (auth.user) auth.user.name = updated.name || profile.value.name;
    profileMsg.value = "✓ 個人資料已更新";
  } catch (e) {
    profileMsg.value = e.message;
  } finally { savingProfile.value = false; }
}

const TABS = [
  { v: "orders", l: "我的訂單" },
  { v: "favorites", l: "我的收藏" },
  { v: "profile", l: "個人資料" },
];
</script>

<template>
  <div v-if="!auth.isLoggedIn" class="container-x py-24 text-center text-ink-400">載入中…</div>
  <div v-else class="container-x py-12">
    <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div><h1 class="text-3xl font-extrabold">會員中心</h1><p class="mt-1 text-ink-500">{{ auth.user?.name }}，歡迎回來！</p></div>
      <RouterLink to="/tours" class="btn-primary self-start">+ 探索新行程</RouterLink>
    </div>

    <div class="mt-8 grid gap-6 lg:grid-cols-3">
      <!-- 側欄 -->
      <div class="card h-fit p-6">
        <img v-if="auth.user?.avatarUrl" :src="auth.user.avatarUrl" @error="(e)=>(e.target.style.display='none')" class="h-16 w-16 rounded-full object-cover" />
        <div v-else class="grid h-16 w-16 place-items-center rounded-full bg-brand-600 text-2xl font-bold text-white">{{ auth.user?.name?.slice(0, 1) }}</div>
        <h2 class="mt-4 text-lg font-bold">{{ auth.user?.name }}</h2>
        <p class="text-sm text-ink-500">{{ auth.user?.email }}</p>
        <div class="mt-4 space-y-2 border-t border-ink-100 pt-4 text-sm">
          <div class="flex justify-between"><span class="text-ink-400">登入方式</span><span class="font-medium">{{ auth.user?.provider === "GOOGLE" ? "Google" : "帳號密碼" }}</span></div>
          <div class="flex justify-between"><span class="text-ink-400">有效訂單</span><span class="font-medium">{{ activeOrders.length }} 筆</span></div>
          <div class="flex justify-between"><span class="text-ink-400">收藏行程</span><span class="font-medium">{{ fav.count }} 筆</span></div>
        </div>
        <nav class="mt-5 space-y-1">
          <button v-for="t in TABS" :key="t.v" @click="tab = t.v"
            :class="['w-full rounded-xl px-4 py-2.5 text-left text-sm font-medium transition', tab === t.v ? 'bg-brand-50 text-brand-700' : 'text-ink-600 hover:bg-ink-50']">{{ t.l }}</button>
          <RouterLink to="/chat" class="block w-full rounded-xl px-4 py-2.5 text-left text-sm font-medium text-ink-600 hover:bg-ink-50">💬 聯絡客服</RouterLink>
        </nav>
      </div>

      <!-- 主內容 -->
      <div class="lg:col-span-2">
        <!-- 訂單 -->
        <div v-if="tab === 'orders'">
          <h2 class="text-lg font-bold">我的訂單</h2>
          <p v-if="busy" class="mt-6 text-ink-400">載入訂單中…</p>
          <div v-else-if="bookings.length === 0" class="card mt-4 p-10 text-center">
            <p class="text-4xl">🧳</p><p class="mt-3 text-ink-500">你還沒有任何訂單。</p>
            <RouterLink to="/tours" class="btn-primary mt-4">開始探索行程</RouterLink>
          </div>
          <div v-else class="mt-4 space-y-4">
            <div v-for="b in bookings" :key="b.id" class="card flex gap-4 overflow-hidden p-3">
              <img :src="b.route.imageUrl" alt="" @error="(e)=>(e.target.style.display='none')" class="h-24 w-32 shrink-0 rounded-xl object-cover bg-gradient-to-br from-brand-300 to-brand-600" />
              <div class="flex flex-1 flex-col justify-between py-1">
                <div>
                  <div class="flex items-start justify-between gap-2">
                    <RouterLink :to="`/tours/${b.route.slug}`" class="font-bold hover:text-brand-700">{{ b.route.name }}</RouterLink>
                    <span :class="['chip', (STATUS[b.status] || STATUS.PENDING).cls]">{{ (STATUS[b.status] || STATUS.PENDING).label }}</span>
                  </div>
                  <p class="mt-1 text-sm text-ink-500">{{ b.route.country }} · {{ b.route.days }} 天 · {{ b.people }} 人 · 出發 {{ dateText(b.travelDate) }}</p>
                </div>
                <div class="flex items-end justify-between">
                  <span class="font-extrabold text-brand-700">{{ twd(b.totalPrice) }}</span>
                  <button v-if="b.status !== 'CANCELLED'" @click="cancel(b)" class="text-xs font-semibold text-rose-500 hover:underline">取消訂單</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 收藏 -->
        <div v-else-if="tab === 'favorites'">
          <h2 class="text-lg font-bold">我的收藏</h2>
          <div v-if="favoriteRoutes.length === 0" class="card mt-4 p-10 text-center">
            <p class="text-4xl">♡</p><p class="mt-3 text-ink-500">還沒有收藏任何行程，去逛逛吧！</p>
            <RouterLink to="/tours" class="btn-primary mt-4">探索行程</RouterLink>
          </div>
          <div v-else class="mt-4 grid gap-6 sm:grid-cols-2"><RouteCard v-for="r in favoriteRoutes" :key="r.id" :route="r" /></div>
        </div>

        <!-- 個人資料 -->
        <div v-else>
          <h2 class="text-lg font-bold">個人資料</h2>
          <form @submit.prevent="saveProfile" class="card mt-4 max-w-lg space-y-4 p-6">
            <div><label class="label">姓名</label><input v-model="profile.name" required class="input" /></div>
            <div><label class="label">Email（不可修改）</label><input :value="auth.user?.email" disabled class="input bg-ink-50 text-ink-400" /></div>
            <div><label class="label">手機</label><input v-model="profile.phone" class="input" placeholder="0912-345-678" /></div>
            <p v-if="profileMsg" class="rounded-lg px-3 py-2 text-sm" :class="profileMsg.startsWith('✓') ? 'bg-brand-50 text-brand-700' : 'bg-red-50 text-red-600'">{{ profileMsg }}</p>
            <button :disabled="savingProfile" class="btn-primary">{{ savingProfile ? "儲存中…" : "儲存變更" }}</button>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>
