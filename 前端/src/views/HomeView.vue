<script setup>
import { computed, nextTick, onMounted, ref, watch } from "vue";
import api from "../api/api";
import EuropeRouteMap from "../components/EuropeRouteMap.vue";
import AiAdvisorBox from "../components/AiAdvisorBox.vue";
import AiAdvisorResults from "../components/AiAdvisorResults.vue";
import RatingBadge from "../components/RatingBadge.vue";
import { useAiAdvisor } from "../composables/useAiAdvisor";
import { useCompareStore } from "../stores/compare";
import { stopNames } from "../data/routeGeo";
import { avatarColor } from "../data/content";

// 首頁：一張歐洲地圖，每條行程是一條線。點線或點清單 → 右側顯示那條行程。
const all = ref([]);
const loading = ref(true);
const loadError = ref("");
const selected = ref("");
const hovered = ref("");
const compare = useCompareStore();

const current = computed(() => all.value.find((r) => r.slug === selected.value) || null);
const countries = computed(() => new Set(all.value.map((r) => r.country)).size);
const twd = (n) => "NT$" + Number(n).toLocaleString("zh-TW");

function select(slug) {
  selected.value = slug;
  // 手機上地圖在上、行程資訊在下：選了之後把資訊捲進畫面
  if (window.matchMedia("(max-width: 1023px)").matches) {
    nextTick(() => document.getElementById("route-panel")?.scrollIntoView({ behavior: "smooth", block: "nearest" }));
  }
}

// 旅人怎麼說：全站最新的真實好評；還沒有任何評價時整區不顯示
const testimonials = ref([]);

// AI 行程顧問：問完自動捲到結果區
const advisor = useAiAdvisor();
watch(advisor.status, (s) => {
  if (s === "loading") nextTick(() => document.getElementById("ai-results")?.scrollIntoView({ behavior: "smooth", block: "start" }));
});

onMounted(async () => {
  api.getLatestReviews(3).then((list) => (testimonials.value = list)).catch(() => {});
  try {
    all.value = await api.getRoutes({ sort: "featured" });
    selected.value = all.value[0]?.slug || "";
  } catch (e) {
    loadError.value = e.message || "行程載入失敗";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <!-- 地圖 -->
  <section class="bg-sea">
    <div class="container-x pb-10 pt-8 sm:pt-12">
      <h1 class="max-w-3xl font-serif text-3xl font-bold leading-snug text-navy sm:text-[2.6rem] sm:leading-tight">
        {{ all.length ? `${all.length} 條自助路線` : "自助路線" }}，畫在同一張歐洲地圖上
      </h1>
      <p class="mt-3 max-w-2xl text-base text-sea-deep sm:text-lg">
        從里斯本的電車到卑爾根的峽灣，共 {{ countries || "數" }} 個國家。點地圖上的線，或從清單挑一條，看它怎麼走。
      </p>

      <!-- 桌機：左欄地圖＋AI 顧問、右欄行程資訊與清單；手機：地圖 → 行程資訊 → 清單 → AI 顧問 -->
      <div class="mt-6 grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
        <div class="overflow-hidden rounded-md bg-sea ring-1 ring-sea-line lg:col-start-1 lg:row-start-1">
          <div v-if="loading" class="aspect-[1000/860] animate-pulse bg-sea" aria-label="地圖載入中" />
          <p v-else-if="loadError" class="grid aspect-[1000/860] place-items-center p-6 text-center text-sea-deep">{{ loadError }}</p>
          <EuropeRouteMap v-else :routes="all" :selected="selected" :highlighted="hovered"
            @select="select" @hover="(s) => (hovered = s)" />
        </div>

        <aside class="lg:sticky lg:top-20 lg:col-start-2 lg:row-span-2 lg:row-start-1">
          <!-- 選中的行程 -->
          <div id="route-panel" class="scroll-mt-20 overflow-hidden rounded-md bg-white ring-1 ring-sea-line" aria-live="polite">
            <template v-if="current">
              <img :src="current.imageUrl" :alt="current.name" class="aspect-[16/9] w-full bg-sea object-cover"
                @error="(e) => (e.target.style.visibility = 'hidden')" />
              <div class="p-5">
                <p class="text-sm text-sea-deep">{{ current.country }}</p>
                <h2 class="mt-0.5 font-serif text-xl font-bold leading-snug text-navy">{{ current.name }}</h2>
                <p class="mt-2 text-sm leading-relaxed text-ink-600">{{ stopNames(current) }}</p>
                <dl class="mt-4 grid grid-cols-3 border-y border-ink-100 py-3 text-sm">
                  <div><dt class="text-xs text-ink-400">天數</dt><dd class="mt-0.5 font-semibold text-navy">{{ current.days }} 天</dd></div>
                  <div><dt class="text-xs text-ink-400">每人</dt><dd class="mt-0.5 font-semibold text-navy">{{ twd(current.price) }}</dd></div>
                  <div><dt class="text-xs text-ink-400">評價</dt><dd class="mt-0.5"><RatingBadge :rating="current.rating" :reviews="current.reviews" /></dd></div>
                </dl>
                <div class="mt-4 flex gap-2">
                  <RouterLink :to="`/tours/${current.slug}`" class="btn-primary flex-1 justify-center">看完整行程</RouterLink>
                  <button type="button" @click="compare.toggle(current)" :aria-pressed="compare.has(current.slug)"
                    class="rounded-md px-4 text-sm font-semibold ring-1 transition"
                    :class="compare.has(current.slug) ? 'bg-navy text-white ring-navy' : 'text-navy ring-sea-line hover:bg-sea/50'">
                    {{ compare.has(current.slug) ? "比較中" : "加入比較" }}
                  </button>
                </div>
              </div>
            </template>
            <div v-else class="h-72 animate-pulse bg-white" />
          </div>

          <!-- 全部路線 -->
          <nav class="mt-4 rounded-md bg-white ring-1 ring-sea-line" aria-label="全部路線">
            <ul class="divide-y divide-ink-100 lg:max-h-[22rem] lg:overflow-y-auto">
              <li v-for="r in all" :key="r.slug">
                <button type="button" @click="select(r.slug)" @pointerenter="hovered = r.slug" @pointerleave="hovered = ''"
                  @focus="hovered = r.slug" @blur="hovered = ''" :aria-current="r.slug === selected ? 'true' : null"
                  :class="['flex w-full items-baseline gap-3 border-l-4 px-4 py-2.5 text-left text-sm transition',
                           r.slug === selected ? 'border-brand-600 bg-brand-50/60' : 'border-transparent hover:bg-sea/40']">
                  <span class="min-w-0 flex-1 truncate font-medium text-navy">{{ r.name }}</span>
                  <span class="shrink-0 text-xs text-ink-500">{{ r.days }} 天</span>
                </button>
              </li>
            </ul>
          </nav>
          <RouterLink to="/tours" class="mt-3 inline-block text-sm font-semibold text-sea-deep underline-offset-4 hover:underline">
            用天數、預算篩選全部行程
          </RouterLink>
        </aside>

        <!-- AI 行程顧問 -->
        <div class="rounded-md bg-white p-5 ring-1 ring-sea-line lg:col-start-1 lg:row-start-2">
          <h2 class="font-serif text-xl font-bold text-navy">不知道選哪一條？</h2>
          <p class="mt-1 text-sm leading-relaxed text-ink-600">用一句話說說你想怎麼玩。AI 只會從這些路線裡挑，價格、天數都照實際行程。</p>
          <div class="mt-4"><AiAdvisorBox :advisor="advisor" /></div>
        </div>
      </div>
    </div>
  </section>

  <AiAdvisorResults :advisor="advisor" />

  <!-- 旅人怎麼說（真實評價，沒有就不顯示） -->
  <section v-if="testimonials.length" class="container-x py-14">
    <h2 class="font-serif text-2xl font-bold text-navy">旅人怎麼說</h2>
    <p class="mt-1 text-sm text-ink-500">來自完成付款的旅客</p>
    <div class="mt-8 grid gap-x-10 gap-y-8 md:grid-cols-3">
      <figure v-for="t in testimonials" :key="t.id" class="border-l-2 border-sea-line pl-5">
        <div class="text-amber-500" :aria-label="`${t.rating} 星`"><span v-for="s in 5" :key="s" aria-hidden="true">{{ s <= t.rating ? '★' : '☆' }}</span></div>
        <blockquote class="mt-2 line-clamp-5 break-words leading-relaxed text-ink-700">{{ t.content }}</blockquote>
        <figcaption class="mt-3 flex items-center gap-3 text-sm">
          <span class="grid h-8 w-8 shrink-0 place-items-center rounded-full text-xs font-bold text-white" :class="avatarColor(t.authorName)">{{ t.authorName.slice(0, 1) }}</span>
          <span class="min-w-0">
            <span class="font-semibold text-navy">{{ t.authorName }}</span>
            <RouterLink :to="`/tours/${t.routeSlug}`" class="block truncate text-xs text-sea-deep hover:underline">{{ t.routeName }}</RouterLink>
          </span>
        </figcaption>
      </figure>
    </div>
  </section>
</template>
