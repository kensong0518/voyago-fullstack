<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import api from "../api/api";
import { useAuthStore } from "../stores/auth";
import { useFavoritesStore } from "../stores/favorites";
import RouteCard from "../components/RouteCard.vue";
import { parseItinerary, COST_INCLUDED, COST_EXCLUDED, TRAVEL_NOTES, pickReviews, avatarColor, buildGallery, relatedRoutes } from "../data/content";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const fav = useFavoritesStore();

const data = ref(null);
const allRoutes = ref([]);
const loading = ref(true);
const notFound = ref(false);
const activeImg = ref("");
let unmounted = false;

const people = ref(2);
const travelDate = ref("");
const submitState = ref("idle");
const error = ref("");
const minDate = new Date(Date.now() + 86400000).toISOString().slice(0, 10);

const itinerary = computed(() => data.value ? parseItinerary(data.value.description) : { overview: "", days: [] });
const tags = computed(() => (data.value?.tags || "").split(",").map((t) => t.trim()).filter(Boolean));
const gallery = computed(() => data.value ? buildGallery(data.value, allRoutes.value) : []);
const reviews = computed(() => data.value ? pickReviews(data.value.id) : []);
const related = computed(() => data.value ? relatedRoutes(data.value, allRoutes.value) : []);
const costIncluded = COST_INCLUDED, costExcluded = COST_EXCLUDED, notes = TRAVEL_NOTES;

const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);

async function loadAll() {
  loading.value = true; notFound.value = false;
  try {
    const [d, list] = await Promise.all([api.getRoute(route.params.slug), api.getRoutes({})]);
    if (unmounted) return;                              // 元件已銷毀則放棄寫入
    data.value = d; allRoutes.value = list; activeImg.value = d.imageUrl;
  } catch {
    if (!unmounted) notFound.value = true;
  } finally {
    if (!unmounted) loading.value = false;
  }
}
onMounted(loadAll);
onBeforeUnmount(() => { unmounted = true; });

async function book() {
  error.value = "";
  if (!auth.isLoggedIn) return router.push({ name: "login", query: { next: route.fullPath } });
  if (!travelDate.value) { error.value = "請選擇出發日期"; return; }
  submitState.value = "loading";
  try {
    const booking = await api.createBooking({ routeId: data.value.id, people: people.value, travelDate: travelDate.value });
    submitState.value = "done";
    // 訂單建立後直接前往結帳頁付款
    setTimeout(() => router.push(`/checkout/${booking.id}`), 600);
  } catch (e) { error.value = e.message; submitState.value = "idle"; }
}
</script>

<template>
  <div v-if="loading" class="container-x py-24 text-center text-ink-400">載入中…</div>

  <div v-else-if="notFound" class="container-x grid min-h-[50vh] place-items-center py-20 text-center">
    <div><p class="text-6xl">🧭</p><p class="mt-4 text-ink-500">找不到此行程。</p>
      <RouterLink to="/tours" class="btn-primary mt-6">返回行程列表</RouterLink></div>
  </div>

  <template v-else>
    <!-- Hero -->
    <div class="relative h-[42vh] min-h-[320px] w-full overflow-hidden bg-gradient-to-br from-brand-500 to-ink-900">
      <img :src="activeImg" :alt="data.name" fetchpriority="high" @error="(e) => (e.target.style.display = 'none')" class="h-full w-full object-cover opacity-80" />
      <div class="absolute inset-0 bg-gradient-to-t from-ink-950/90 to-transparent" />
      <div class="container-x absolute inset-x-0 bottom-0 pb-8 text-white">
        <RouterLink to="/tours" class="text-sm text-white/80 hover:text-white">← 返回行程列表</RouterLink>
        <div class="mt-3 flex flex-wrap items-center gap-2">
          <span class="chip bg-white/20 text-white">{{ data.country }}</span>
          <span class="chip bg-white/20 text-white">{{ data.days }} 天</span>
        </div>
        <h1 class="mt-2 text-3xl font-extrabold sm:text-4xl">{{ data.name }}</h1>
        <p class="mt-1 text-white/80">📍 {{ data.location }}</p>
      </div>
    </div>

    <!-- 相簿縮圖 -->
    <div class="container-x mt-4">
      <div class="flex gap-2 overflow-x-auto pb-1">
        <button v-for="(img, i) in gallery" :key="img" @click="activeImg = img"
          class="h-16 w-24 shrink-0 overflow-hidden rounded-lg ring-2 transition"
          :class="activeImg === img ? 'ring-brand-500' : 'ring-transparent hover:ring-ink-200'">
          <img :src="img" loading="lazy" decoding="async" :alt="`${data.name} 相片 ${i + 1}`"
            @error="(e) => (e.target.style.display = 'none')" class="h-full w-full object-cover" />
        </button>
      </div>
    </div>

    <div class="container-x grid gap-10 py-10 lg:grid-cols-3">
      <div class="lg:col-span-2">
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-4 text-sm">
            <span class="flex items-center gap-1 font-semibold text-amber-600">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l2.9 6.3 6.9.7-5.1 4.6 1.4 6.8L12 17.8 5.9 21l1.4-6.8L2.2 9.6l6.9-.7z"/></svg>
              {{ data.rating.toFixed(1) }}
            </span>
            <span class="text-ink-400">{{ data.reviews }} 則旅人評價</span>
          </div>
          <button @click="fav.toggle(data.id)" class="btn-outline text-sm"
            :class="fav.has(data.id) ? '!border-rose-300 !text-rose-600' : ''">
            {{ fav.has(data.id) ? '♥ 已收藏' : '♡ 收藏' }}
          </button>
        </div>

        <p class="mt-5 text-lg text-ink-700">{{ data.summary }}</p>
        <div class="mt-4 flex flex-wrap gap-2"><span v-for="t in tags" :key="t" class="chip">{{ t }}</span></div>
        <p v-if="itinerary.overview" class="mt-5 rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-800">{{ itinerary.overview }}。</p>

        <!-- 逐日行程時間軸 -->
        <h2 class="mt-10 text-xl font-bold">逐日行程</h2>
        <ol class="mt-4 space-y-4 border-l-2 border-brand-100 pl-6">
          <li v-for="(d, i) in itinerary.days" :key="`day-${i}-${d.slice(0,8)}`" class="relative">
            <span class="absolute -left-[31px] grid h-6 w-6 place-items-center rounded-full bg-brand-600 text-xs font-bold text-white">{{ i + 1 }}</span>
            <p class="text-ink-700">{{ d }}。</p>
          </li>
        </ol>

        <!-- 費用包含 / 不含 -->
        <div class="mt-10 grid gap-4 sm:grid-cols-2">
          <div class="card p-5">
            <h3 class="flex items-center gap-2 font-bold text-brand-700"><span>✓</span> 費用包含</h3>
            <ul class="mt-3 space-y-2 text-sm text-ink-600">
              <li v-for="c in costIncluded" :key="c" class="flex gap-2"><span class="text-brand-500">•</span>{{ c }}</li>
            </ul>
          </div>
          <div class="card p-5">
            <h3 class="flex items-center gap-2 font-bold text-ink-500"><span>✕</span> 費用不含</h3>
            <ul class="mt-3 space-y-2 text-sm text-ink-600">
              <li v-for="c in costExcluded" :key="c" class="flex gap-2"><span class="text-ink-300">•</span>{{ c }}</li>
            </ul>
          </div>
        </div>

        <!-- 行前須知 -->
        <h2 class="mt-10 text-xl font-bold">行前須知</h2>
        <ul class="mt-4 space-y-2">
          <li v-for="n in notes" :key="n" class="flex gap-3 text-ink-600">
            <span class="mt-0.5 text-amber-500">⚠️</span><span>{{ n }}</span>
          </li>
        </ul>

        <!-- 旅人評價 -->
        <div class="mt-10 flex items-center justify-between">
          <h2 class="text-xl font-bold">旅人評價</h2>
          <span class="flex items-center gap-1 text-sm font-semibold text-amber-600">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l2.9 6.3 6.9.7-5.1 4.6 1.4 6.8L12 17.8 5.9 21l1.4-6.8L2.2 9.6l6.9-.7z"/></svg>
            {{ data.rating.toFixed(1) }} / 5.0
          </span>
        </div>
        <div class="mt-4 grid gap-4 sm:grid-cols-2">
          <div v-for="r in reviews" :key="`${r.name}-${r.date}`" class="card p-4">
            <div class="flex items-center gap-3">
              <span class="grid h-9 w-9 place-items-center rounded-full text-sm font-bold text-white" :class="avatarColor(r.name)">{{ r.name.slice(0, 1) }}</span>
              <div>
                <p class="text-sm font-bold">{{ r.name }}</p>
                <div class="flex items-center gap-1 text-xs text-amber-500">
                  <span v-for="s in 5" :key="s">{{ s <= r.rating ? '★' : '☆' }}</span>
                  <span class="ml-1 text-ink-400">{{ r.date }}</span>
                </div>
              </div>
            </div>
            <p class="mt-3 text-sm text-ink-600">{{ r.text }}</p>
          </div>
        </div>
      </div>

      <!-- 預訂側欄 -->
      <div class="lg:col-span-1">
        <form @submit.prevent="book" class="card sticky top-20 p-6">
          <div class="flex items-baseline justify-between">
            <span class="text-sm text-ink-500">每人</span>
            <span class="text-2xl font-extrabold text-brand-700">{{ twd(data.price) }}</span>
          </div>
          <div class="mt-5 space-y-4">
            <div><label class="label">出發日期</label><input type="date" :min="minDate" v-model="travelDate" class="input" /></div>
            <div>
              <label class="label">人數</label>
              <div class="flex items-center gap-3">
                <button type="button" @click="people = Math.max(1, people - 1)" class="btn-outline h-10 w-10 p-0 text-lg">−</button>
                <span class="w-10 text-center text-lg font-bold">{{ people }}</span>
                <button type="button" @click="people = Math.min(20, people + 1)" class="btn-outline h-10 w-10 p-0 text-lg">+</button>
              </div>
            </div>
          </div>
          <div class="mt-5 flex items-center justify-between border-t border-ink-100 pt-4">
            <span class="text-sm text-ink-500">總計</span>
            <span class="text-xl font-extrabold">{{ twd(data.price * people) }}</span>
          </div>
          <p v-if="error" class="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{{ error }}</p>
          <button :disabled="submitState !== 'idle'" class="btn-primary mt-4 w-full py-3 text-base">
            {{ submitState === "loading" ? "預訂中…" : submitState === "done" ? "✓ 訂單成立，前往結帳…" : auth.isLoggedIn ? "立即預訂" : "登入後預訂" }}
          </button>
          <p v-if="!auth.isLoggedIn" class="mt-2 text-center text-xs text-ink-400">需登入會員才能完成預訂</p>
          <button type="button" @click="fav.toggle(data.id)" class="btn-outline mt-2 w-full">
            {{ fav.has(data.id) ? '♥ 已加入收藏' : '♡ 加入收藏' }}
          </button>
        </form>
      </div>
    </div>

    <!-- 相關行程 -->
    <section class="container-x pb-16">
      <h2 class="text-xl font-bold">你可能也喜歡</h2>
      <div class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <RouteCard v-for="r in related" :key="r.slug" :route="r" />
      </div>
    </section>
  </template>
</template>
