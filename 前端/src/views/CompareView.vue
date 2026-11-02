<script setup>
import { computed, onMounted, ref } from "vue";
import api from "../api/api";
import { useCompareStore, COMPARE_MAX } from "../stores/compare";
import RatingBadge from "../components/RatingBadge.vue";

const compare = useCompareStore();
const all = ref([]);
const loading = ref(true);
const error = ref("");

// 用後端最新資料（價格、評分可能已變），順序照使用者加入的順序
const routes = computed(() => compare.slugs.map((s) => all.value.find((r) => r.slug === s)).filter(Boolean));

const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);
const perDay = (r) => Math.round(r.price / r.days);
const tagsOf = (r) => (r.tags || "").split(",").map((t) => t.trim()).filter(Boolean);

// 每一列標出最好的那個（至少兩個行程才標；評價要有人評過才比）
function best(values, pick) {
  if (routes.value.length < 2) return null;
  const valid = values.filter((v) => v != null);
  if (valid.length < 2) return null;
  const target = pick === "min" ? Math.min(...valid) : Math.max(...valid);
  return values.filter((v) => v === target).length === values.length ? null : target;   // 全部一樣就不標
}
const bestPrice = computed(() => best(routes.value.map((r) => r.price), "min"));
const bestPerDay = computed(() => best(routes.value.map(perDay), "min"));
const bestRating = computed(() => best(routes.value.map((r) => (r.reviews > 0 ? r.rating : null)), "max"));
const mostDays = computed(() => best(routes.value.map((r) => r.days), "max"));

// 共同標籤：幾個行程都有的主題，一眼看出差異在哪
const commonTags = computed(() => {
  if (routes.value.length < 2) return new Set();
  const [first, ...rest] = routes.value.map((r) => new Set(tagsOf(r)));
  return new Set([...first].filter((t) => rest.every((s) => s.has(t))));
});

onMounted(async () => {
  try { all.value = await api.getRoutes({}); }
  catch (e) { error.value = e.message || "載入失敗"; }
  finally { loading.value = false; }
});
</script>

<template>
  <section class="border-b border-ink-100 bg-ink-50">
    <div class="container-x py-10">
      <RouterLink to="/tours" class="text-sm text-ink-500 hover:text-ink-700">← 回行程列表</RouterLink>
      <h1 class="mt-2 text-3xl font-extrabold">行程比較</h1>
      <p class="mt-2 text-ink-500">最多並排 {{ COMPARE_MAX }} 個行程，綠色標示該項目最划算的選擇。</p>
    </div>
  </section>

  <section class="container-x py-10">
    <p v-if="loading" class="py-16 text-center text-ink-400">載入中…</p>
    <p v-else-if="error" class="py-16 text-center text-rose-600">{{ error }}</p>

    <div v-else-if="routes.length === 0" class="mx-auto max-w-md py-16 text-center">
      <p class="text-5xl">⚖️</p>
      <p class="mt-4 text-ink-500">還沒有加入比較的行程。在行程卡片上按「＋ 比較」，最多可選 {{ COMPARE_MAX }} 個。</p>
      <RouterLink to="/tours" class="btn-primary mt-6 inline-block">去挑行程</RouterLink>
    </div>

    <template v-else>
      <p v-if="routes.length === 1" class="mb-4 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800">
        再加入一個行程就能並排比較。<RouterLink to="/tours" class="font-semibold underline">去挑行程</RouterLink>
      </p>

      <!-- 手機可左右滑；第一欄固定 -->
      <div class="-mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
        <table class="w-full min-w-[640px] table-fixed border-separate border-spacing-0 text-sm">
          <colgroup>
            <col class="w-24 sm:w-32" />
            <col v-for="r in routes" :key="r.slug" />
          </colgroup>
          <thead>
            <tr>
              <th class="sticky left-0 z-10 bg-white" />
              <th v-for="r in routes" :key="r.slug" class="px-2 pb-4 align-top text-left font-normal">
                <div class="card overflow-hidden">
                  <img :src="r.imageUrl" :alt="r.name" loading="lazy" @error="(e) => (e.target.style.visibility = 'hidden')"
                    class="aspect-[4/3] w-full bg-ink-100 object-cover" />
                  <div class="p-3">
                    <RouterLink :to="`/tours/${r.slug}`" class="line-clamp-2 font-bold text-ink-900 hover:text-brand-700">{{ r.name }}</RouterLink>
                    <div class="mt-3 flex gap-2">
                      <RouterLink :to="`/tours/${r.slug}`" class="btn-primary flex-1 px-2 py-1.5 text-center text-xs">查看行程</RouterLink>
                      <button type="button" @click="compare.remove(r.slug)" :aria-label="`從比較中移除 ${r.name}`"
                        class="rounded-full px-2.5 text-xs text-ink-500 ring-1 ring-ink-200 hover:bg-ink-50">移除</button>
                    </div>
                  </div>
                </div>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th class="cmp-head">每人價格</th>
              <td v-for="r in routes" :key="r.slug" class="cmp-cell">
                <span :class="['text-base font-extrabold', r.price === bestPrice ? 'text-brand-700' : 'text-ink-900']">{{ twd(r.price) }}</span>
                <span v-if="r.price === bestPrice" class="cmp-best">最便宜</span>
              </td>
            </tr>
            <tr>
              <th class="cmp-head">天數</th>
              <td v-for="r in routes" :key="r.slug" class="cmp-cell">
                {{ r.days }} 天<span v-if="r.days === mostDays" class="cmp-best">玩最久</span>
              </td>
            </tr>
            <tr>
              <th class="cmp-head">平均每天</th>
              <td v-for="r in routes" :key="r.slug" class="cmp-cell">
                <span :class="perDay(r) === bestPerDay ? 'font-semibold text-brand-700' : ''">{{ twd(perDay(r)) }}</span>
                <span v-if="perDay(r) === bestPerDay" class="cmp-best">每天最划算</span>
              </td>
            </tr>
            <tr>
              <th class="cmp-head">旅人評價</th>
              <td v-for="r in routes" :key="r.slug" class="cmp-cell">
                <RatingBadge :rating="r.rating" :reviews="r.reviews" />
                <span v-if="r.reviews > 0 && r.rating === bestRating" class="cmp-best">評價最高</span>
              </td>
            </tr>
            <tr>
              <th class="cmp-head">國家／地點</th>
              <td v-for="r in routes" :key="r.slug" class="cmp-cell">{{ r.country }}<br /><span class="text-ink-500">{{ r.location }}</span></td>
            </tr>
            <tr>
              <th class="cmp-head">主題</th>
              <td v-for="r in routes" :key="r.slug" class="cmp-cell">
                <div class="flex flex-wrap gap-1">
                  <span v-for="t in tagsOf(r)" :key="t"
                    :class="['rounded-full px-2 py-0.5 text-xs', commonTags.has(t) ? 'bg-ink-100 text-ink-500' : 'bg-brand-50 font-semibold text-brand-700']">{{ t }}</span>
                </div>
              </td>
            </tr>
            <tr>
              <th class="cmp-head">行程特色</th>
              <td v-for="r in routes" :key="r.slug" class="cmp-cell text-ink-600">{{ r.summary }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="routes.length >= 2 && commonTags.size" class="mt-3 text-xs text-ink-400">綠底主題是這個行程獨有的；灰底是比較中的行程都有的。</p>
    </template>
  </section>
</template>

<style scoped>
.cmp-head {
  @apply sticky left-0 z-10 border-t border-ink-100 bg-white py-3 pr-2 text-left align-top text-xs font-semibold text-ink-500;
}
.cmp-cell {
  @apply border-t border-ink-100 px-2 py-3 align-top text-ink-800;
}
.cmp-best {
  @apply ml-1.5 inline-block rounded-full bg-brand-50 px-2 py-0.5 text-[11px] font-semibold text-brand-700;
}
</style>
