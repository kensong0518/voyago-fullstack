<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
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
const drawerOpen = ref(false);   // 手機版：篩選改成從底部滑出的抽屜

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

// 抽屜開著時鎖住背景捲動，按 Esc 關閉
function onKey(e) { if (e.key === "Escape") drawerOpen.value = false; }
watch(drawerOpen, (open) => {
  document.body.style.overflow = open ? "hidden" : "";
  if (open) window.addEventListener("keydown", onKey);
  else window.removeEventListener("keydown", onKey);
});
onBeforeUnmount(() => { document.body.style.overflow = ""; window.removeEventListener("keydown", onKey); });

const chip = (on) => ["rounded-md px-3 py-1.5 text-sm font-medium transition",
  on ? "bg-navy text-white" : "bg-white text-ink-600 ring-1 ring-ink-200"];

onMounted(async () => {
  try { all.value = await api.getRoutes({}); }
  finally { loading.value = false; }
});
</script>

<template>
  <section class="border-b border-sea-line bg-sea">
    <div class="container-x py-10 sm:py-12">
      <h1 class="font-serif text-3xl font-bold text-navy">全部路線</h1>
      <p class="mt-2 text-sea-deep">{{ loading ? "搜尋中…" : `符合條件的有 ${filtered.length} 條` }}</p>

      <div class="mt-6 flex flex-col gap-3 sm:flex-row">
        <div class="relative flex-1">
          <svg class="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><circle cx="8" cy="8" r="6"/><path d="M14 14l3 3"/></svg>
          <input v-model="q" placeholder="搜尋國家、城市或行程名稱…" class="input pl-10" />
        </div>
        <!-- 手機：一顆按鈕打開篩選抽屜 -->
        <button type="button" @click="drawerOpen = true" class="btn-outline flex items-center justify-center gap-2 sm:hidden"
          aria-haspopup="dialog" :aria-expanded="drawerOpen">
          <svg width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path d="M4 6h16M7 12h10M10 18h4"/></svg>
          篩選與排序<span v-if="activeCount" class="rounded-full bg-brand-600 px-2 text-xs text-white">{{ activeCount }}</span>
        </button>
        <select v-model="sort" class="input hidden sm:block sm:w-44" aria-label="排序">
          <option v-for="s in SORTS" :key="s.v" :value="s.v">{{ s.l }}</option>
        </select>
        <button @click="onlyFav = !onlyFav"
          :class="['btn hidden whitespace-nowrap sm:inline-flex', onlyFav ? 'bg-navy text-white' : 'btn-outline bg-white']">
          只看收藏（{{ fav.count }}）
        </button>
      </div>

      <!-- 主題標籤 -->
      <div class="mt-4 hidden flex-wrap gap-2 sm:flex">
        <button @click="tag = ''" :class="['rounded-md px-3.5 py-1.5 text-sm font-medium transition', tag === '' ? 'bg-navy text-white' : 'bg-white text-ink-600 ring-1 ring-ink-200 hover:bg-ink-50']">全部主題</button>
        <button v-for="t in TAGS" :key="t" @click="tag = t" :class="['rounded-md px-3.5 py-1.5 text-sm font-medium transition', tag === t ? 'bg-navy text-white' : 'bg-white text-ink-600 ring-1 ring-ink-200 hover:bg-ink-50']">{{ t }}</button>
      </div>

      <!-- 進階篩選（桌機） -->
      <div class="mt-4 hidden sm:block">
        <button @click="showFilters = !showFilters" class="text-sm font-semibold text-brand-700 hover:underline">
          {{ showFilters ? "▾" : "▸" }} 進階篩選<span v-if="activeCount"> ({{ activeCount }})</span>
          <span v-if="activeCount" @click.stop="clearAll" class="ml-2 text-ink-400 hover:text-ink-600">清除全部</span>
        </button>

        <div v-if="showFilters" class="mt-3 grid gap-4 rounded-md bg-white p-4 ring-1 ring-sea-line sm:grid-cols-3">
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">國家</p>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="c in allCountries" :key="c" @click="toggleCountry(c)"
                :class="['rounded-md px-2.5 py-1 text-xs font-medium transition', countries.includes(c) ? 'bg-navy text-white' : 'bg-ink-50 text-ink-600 ring-1 ring-ink-200 hover:bg-ink-100']">{{ c }}</button>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">價格</p>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="p in PRICE_BUCKETS" :key="p.v" @click="priceBucket = p.v"
                :class="['rounded-md px-2.5 py-1 text-xs font-medium transition', priceBucket === p.v ? 'bg-navy text-white' : 'bg-ink-50 text-ink-600 ring-1 ring-ink-200 hover:bg-ink-100']">{{ p.l }}</button>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">天數</p>
            <div class="flex flex-wrap gap-1.5">
              <button v-for="d in DAY_BUCKETS" :key="d.v" @click="dayBucket = d.v"
                :class="['rounded-md px-2.5 py-1 text-xs font-medium transition', dayBucket === d.v ? 'bg-navy text-white' : 'bg-ink-50 text-ink-600 ring-1 ring-ink-200 hover:bg-ink-100']">{{ d.l }}</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- 手機版篩選抽屜 -->
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="drawerOpen" class="fixed inset-0 z-50 bg-ink-950/50 sm:hidden" @click="drawerOpen = false" />
    </Transition>
    <Transition name="drawer-up">
      <div v-if="drawerOpen" role="dialog" aria-modal="true" aria-label="篩選與排序"
        class="fixed inset-x-0 bottom-0 z-50 flex max-h-[85vh] flex-col rounded-t-3xl bg-white shadow-2xl sm:hidden">
        <div class="relative flex items-center justify-between border-b border-ink-100 px-5 pb-3 pt-4">
          <span class="mx-auto h-1 w-10 rounded-full bg-ink-200 absolute left-1/2 top-1.5 -translate-x-1/2" aria-hidden="true" />
          <h2 class="text-base font-bold">篩選與排序</h2>
          <button type="button" @click="drawerOpen = false" class="grid h-9 w-9 place-items-center rounded-full text-ink-500 hover:bg-ink-50" aria-label="關閉">✕</button>
        </div>
        <div class="flex-1 space-y-5 overflow-y-auto px-5 py-4">
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">排序</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="o in SORTS" :key="o.v" type="button" @click="sort = o.v" :class="chip(sort === o.v)">{{ o.l }}</button>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">主題</p>
            <div class="flex flex-wrap gap-2">
              <button type="button" @click="tag = ''" :class="chip(tag === '')">全部</button>
              <button v-for="t in TAGS" :key="t" type="button" @click="tag = tag === t ? '' : t" :class="chip(tag === t)">{{ t }}</button>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">國家（可複選）</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="c in allCountries" :key="c" type="button" @click="toggleCountry(c)" :class="chip(countries.includes(c))">{{ c }}</button>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">價格</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="p in PRICE_BUCKETS" :key="p.v" type="button" @click="priceBucket = p.v" :class="chip(priceBucket === p.v)">{{ p.l }}</button>
            </div>
          </div>
          <div>
            <p class="mb-2 text-xs font-semibold text-ink-500">天數</p>
            <div class="flex flex-wrap gap-2">
              <button v-for="d in DAY_BUCKETS" :key="d.v" type="button" @click="dayBucket = d.v" :class="chip(dayBucket === d.v)">{{ d.l }}</button>
            </div>
          </div>
          <label class="flex items-center justify-between rounded-2xl bg-ink-50 px-4 py-3 text-sm font-medium">
            <span>只看我的收藏（{{ fav.count }}）</span>
            <input type="checkbox" v-model="onlyFav" class="h-5 w-5 accent-rose-500" />
          </label>
        </div>
        <div class="flex gap-3 border-t border-ink-100 px-5 py-3" style="padding-bottom: max(0.75rem, env(safe-area-inset-bottom))">
          <button type="button" @click="clearAll" class="btn-outline flex-1">清除</button>
          <button type="button" @click="drawerOpen = false" class="btn-primary flex-[2]">顯示 {{ filtered.length }} 個行程</button>
        </div>
      </div>
    </Transition>
  </Teleport>

  <section class="container-x py-12">
    <Transition name="fade" mode="out-in">
      <div v-if="loading" key="skeleton" class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <div v-for="i in 6" :key="i" class="card overflow-hidden">
          <div class="aspect-[4/3] animate-pulse bg-ink-100" />
          <div class="space-y-2 p-4"><div class="h-4 w-2/3 animate-pulse rounded bg-ink-100" /><div class="h-3 w-full animate-pulse rounded bg-ink-100" /></div>
        </div>
      </div>
      <div v-else-if="filtered.length === 0" key="empty" class="py-20 text-center text-ink-500">
        <p>沒有符合條件的行程。放寬天數或預算再找一次。</p>
        <button @click="clearAll" class="btn-outline mt-4">清除所有篩選</button>
      </div>
      <div v-else key="grid" class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <RouteCard v-for="r in filtered" :key="r.slug" :route="r" />
      </div>
    </Transition>
  </section>
</template>

<style scoped>
.drawer-up-enter-active, .drawer-up-leave-active { transition: transform .25s ease; }
.drawer-up-enter-from, .drawer-up-leave-to { transform: translateY(100%); }
.drawer-fade-enter-active, .drawer-fade-leave-active { transition: opacity .25s ease; }
.drawer-fade-enter-from, .drawer-fade-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .drawer-up-enter-active, .drawer-up-leave-active, .drawer-fade-enter-active, .drawer-fade-leave-active { transition: none; }
}
</style>
