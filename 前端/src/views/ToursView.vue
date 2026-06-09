<script setup>
import { computed, onMounted, ref } from "vue";
import api from "../api/api";
import RouteCard from "../components/RouteCard.vue";
import { useFavoritesStore } from "../stores/favorites";

const fav = useFavoritesStore();

const TAGS = ["自由行", "城市", "美食", "蜜月", "鐵道", "跳島", "攝影", "建築", "古城", "峽灣"];
const SORTS = [
  { v: "featured", l: "推薦排序" },
  { v: "rating", l: "評價最高" },
  { v: "price-asc", l: "價格低→高" },
  { v: "price-desc", l: "價格高→低" },
  { v: "days", l: "天數最短" },
];
const PRICE_BUCKETS = [
  { v: "", l: "不限價格" },
  { v: "lt5", l: "5 萬以下" },
  { v: "5to10", l: "5–10 萬" },
  { v: "gt10", l: "10 萬以上" },
];
const DAY_BUCKETS = [
  { v: "", l: "不限天數" },
  { v: "le6", l: "6 天以內" },
  { v: "7to9", l: "7–9 天" },
  { v: "ge10", l: "10 天以上" },
];

const all = ref([]);
const loading = ref(true);
const q = ref("");
const tag = ref("");
const sort = ref("featured");
const priceBucket = ref("");
const dayBucket = ref("");
const countries = ref([]);      // 選取的國家（多選）
const onlyFav = ref(false);
const showFilters = ref(true);

const allCountries = computed(() => [...new Set(all.value.map((r) => r.country))]);

function toggleCountry(c) {
  const i = countries.value.indexOf(c);
  if (i >= 0) countries.value.splice(i, 1);
  else countries.value.push(c);
}
function clearAll() {
  q.value = ""; tag.value = ""; priceBucket.value = ""; dayBucket.value = "";
  countries.value = []; onlyFav.value = false;
}

const filtered = computed(() => {
  let list = all.value.filter((r) => {
    const okQ = !q.value || [r.name, r.country, r.location, r.summary].some((f) => f.toLowerCase().includes(q.value.toLowerCase()));
    const okTag = !tag.value || r.tags.includes(tag.value);
    const okCountry = countries.value.length === 0 || countries.value.includes(r.country);
    const okFav = !onlyFav.value || fav.has(r.id);
    const okPrice = !priceBucket.value
      || (priceBucket.value === "lt5" && r.price < 50000)
      || (priceBucket.value === "5to10" && r.price >= 50000 && r.price <= 100000)
      || (priceBucket.value === "gt10" && r.price > 100000);
    const okDays = !dayBucket.value
      || (dayBucket.value === "le6" && r.days <= 6)
      || (dayBucket.value === "7to9" && r.days >= 7 && r.days <= 9)
      || (dayBucket.value === "ge10" && r.days >= 10);
    return okQ && okTag && okCountry && okFav && okPrice && okDays;
  });
  const s = sort.value;
  list = [...list].sort((a, b) =>
    s === "price-asc" ? a.price - b.price :
    s === "price-desc" ? b.price - a.price :
    s === "rating" ? b.rating - a.rating :
    s === "days" ? a.days - b.days :
    (b.featured - a.featured) || (b.reviews - a.reviews));
  return list;
});

const activeCount = computed(() =>
  (q.value ? 1 : 0) + (tag.value ? 1 : 0) + (priceBucket.value ? 1 : 0) +
  (dayBucket.value ? 1 : 0) + countries.value.length + (onlyFav.value ? 1 : 0));

onMounted(async () => {
  try { all.value = await api.getRoutes({}); }
  finally { loading.value = false; }
});
</script>

<template>
  <section class="border-b border-ink-100 bg-ink-50">
    <div class="container-x py-12">
      <h1 class="text-3xl font-extrabold">探索歐洲行程</h1>
      <p class="mt-2 text-ink-500">{{ loading ? "搜尋中…" : `共 ${filtered.length} 條歐洲路線` }}</p>

      <div class="mt-6 flex flex-col gap-3 sm:flex-row">
        <div class="relative flex-1">
          <svg class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><circle cx="8" cy="8" r="6"/><path d="M14 14l3 3"/></svg>
          <input v-model="q" placeholder="搜尋國家、城市或行程名稱…" class="input pl-10" />
        </div>
        <select v-model="sort" class="input sm:w-44">
          <option v-for="s in SORTS" :key="s.v" :value="s.v">{{ s.l }}</option>
        </select>
        <button @click="onlyFav = !onlyFav"
          :class="['btn whitespace-nowrap', onlyFav ? 'bg-rose-500 text-white' : 'btn-outline']">
          ♥ 我的收藏 ({{ fav.count }})
        </button>
      </div>

      <!-- 主題標籤 -->
      <div class="mt-4 flex flex-wrap gap-2">
        <button @click="tag = ''" :class="['rounded-full px-3.5 py-1.5 text-sm font-medium transition', tag === '' ? 'bg-brand-600 text-white' : 'bg-white text-ink-600 ring-1 ring-ink-200 hover:bg-ink-50']">全部主題</button>
        <button v-for="t in TAGS" :key="t" @click="tag = t" :class="['rounded-full px-3.5 py-1.5 text-sm font-medium transition', tag === t ? 'bg-brand-600 text-white' : 'bg-white text-ink-600 ring-1 ring-ink-200 hover:bg-ink-50']">{{ t }}</button>
      </div>

      <!-- 進階篩選 -->
      <div class="mt-4">
        <button @click="showFilters = !showFilters" class="text-sm font-semibold text-brand-700 hover:underline">
          {{ showFilters ? "▾" : "▸" }} 進階篩選<span v-if="activeCount"> ({{ activeCount }})</span>
          <span v-if="activeCount" @click.stop="clearAll" class="ml-2 text-ink-400 hover:text-ink-600">清除全部 ✕</span>
        </button>

        <div v-if="showFilters" class="mt-3 grid gap-4 rounded-2xl bg-white p-4 ring-1 ring-ink-100 sm:grid-cols-3">
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">國家</p>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="c in allCountries" :key="c" @click="toggleCountry(c)"
                :class="['rounded-full px-2.5 py-1 text-xs font-medium transition', countries.includes(c) ? 'bg-brand-600 text-white' : 'bg-ink-50 text-ink-600 ring-1 ring-ink-200 hover:bg-ink-100']">{{ c }}</button>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">價格</p>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="p in PRICE_BUCKETS" :key="p.v" @click="priceBucket = p.v"
                :class="['rounded-full px-2.5 py-1 text-xs font-medium transition', priceBucket === p.v ? 'bg-brand-600 text-white' : 'bg-ink-50 text-ink-600 ring-1 ring-ink-200 hover:bg-ink-100']">{{ p.l }}</button>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">天數</p>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="d in DAY_BUCKETS" :key="d.v" @click="dayBucket = d.v"
                :class="['rounded-full px-2.5 py-1 text-xs font-medium transition', dayBucket === d.v ? 'bg-brand-600 text-white' : 'bg-ink-50 text-ink-600 ring-1 ring-ink-200 hover:bg-ink-100']">{{ d.l }}</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <section class="container-x py-12">
    <div v-if="loading" class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="i in 6" :key="i" class="card overflow-hidden">
        <div class="aspect-[4/3] animate-pulse bg-ink-100" />
        <div class="space-y-2 p-4"><div class="h-4 w-2/3 animate-pulse rounded bg-ink-100" /><div class="h-3 w-full animate-pulse rounded bg-ink-100" /></div>
      </div>
    </div>
    <div v-else-if="filtered.length === 0" class="py-20 text-center text-ink-500">
      <p class="text-5xl">🧭</p>
      <p class="mt-4">找不到符合條件的行程，試試放寬篩選條件吧。</p>
      <button @click="clearAll" class="btn-outline mt-4">清除所有篩選</button>
    </div>
    <div v-else class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <RouteCard v-for="r in filtered" :key="r.slug" :route="r" />
    </div>
  </section>
</template>
