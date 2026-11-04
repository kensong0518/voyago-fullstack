<script setup>
import { computed } from "vue";
import { useFavoritesStore } from "../stores/favorites";
import { useCompareStore } from "../stores/compare";
import { stopNames } from "../data/routeGeo";
import RatingBadge from "./RatingBadge.vue";

// 行程卡：照片、行程名稱（明體）、走過的城市、天數與價格。細框線、小圓角、不加陰影與漸層。
const props = defineProps({ route: { type: Object, required: true } });
const fav = useFavoritesStore();
const compare = useCompareStore();

const stops = computed(() => stopNames(props.route));
const price = computed(() => "NT$" + Number(props.route.price).toLocaleString("zh-TW"));
</script>

<template>
  <article class="group relative flex flex-col overflow-hidden rounded-md bg-white ring-1 ring-ink-200/80 transition hover:ring-navy/40">
    <div class="relative aspect-[4/3] overflow-hidden bg-sea">
      <img :src="route.imageUrl" :alt="route.name" loading="lazy" @error="(e) => (e.target.style.visibility = 'hidden')"
        class="h-full w-full object-cover" />
      <button type="button" @click="fav.toggle(route.id)"
        class="absolute right-2 top-2 z-10 grid h-9 w-9 place-items-center rounded-full bg-white/90 transition hover:bg-white"
        :aria-label="fav.has(route.id) ? `取消收藏 ${route.name}` : `收藏 ${route.name}`" :aria-pressed="fav.has(route.id)">
        <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"
          :fill="fav.has(route.id) ? '#e11d48' : 'none'" :stroke="fav.has(route.id) ? '#e11d48' : '#2b4a66'" stroke-width="2">
          <path d="M12 21s-7.5-4.6-10-9.3C.6 8.6 2.2 5 5.5 5c2 0 3.3 1.1 4.5 2.6C11.2 6.1 12.5 5 14.5 5 17.8 5 19.4 8.6 22 11.7 19.5 16.4 12 21 12 21z"/>
        </svg>
      </button>
    </div>

    <div class="flex flex-1 flex-col p-4">
      <p class="text-xs text-sea-deep">{{ route.country }}</p>
      <h3 class="mt-0.5 font-serif text-lg font-bold leading-snug text-navy">
        <!-- 整張卡可點：連結的點擊範圍延伸到整張卡（收藏、比較按鈕疊在上層） -->
        <RouterLink :to="`/tours/${route.slug}`" class="after:absolute after:inset-0 after:content-[''] focus:outline-none focus-visible:underline">
          {{ route.name }}
        </RouterLink>
      </h3>
      <p class="mt-1.5 line-clamp-2 text-sm text-ink-600">{{ stops }}</p>

      <div class="mt-auto flex items-end justify-between gap-3 pt-4">
        <div class="text-sm">
          <p class="text-navy"><span class="font-semibold">{{ route.days }} 天</span>，每人 <span class="font-semibold">{{ price }}</span></p>
          <p class="mt-0.5 text-xs"><RatingBadge :rating="route.rating" :reviews="route.reviews" /></p>
        </div>
        <button type="button" @click="compare.toggle(route)" :aria-pressed="compare.has(route.slug)"
          :aria-label="compare.has(route.slug) ? `從比較移除 ${route.name}` : `加入比較 ${route.name}`"
          :class="['relative z-10 shrink-0 rounded-md px-3 py-1.5 text-xs font-semibold ring-1 transition',
                   compare.has(route.slug) ? 'bg-navy text-white ring-navy' : 'text-navy ring-sea-line hover:bg-sea/50']">
          {{ compare.has(route.slug) ? "比較中" : "加入比較" }}
        </button>
      </div>
    </div>
  </article>
</template>
