<script setup>
import { computed } from "vue";
import { useFavoritesStore } from "../stores/favorites";
import { useCompareStore } from "../stores/compare";
import RatingBadge from "./RatingBadge.vue";

const props = defineProps({ route: { type: Object, required: true } });
const fav = useFavoritesStore();
const compare = useCompareStore();

const tags = computed(() => (props.route.tags || "").split(",").map((t) => t.trim()).filter(Boolean).slice(0, 3));
const priceText = computed(() => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(props.route.price));
</script>

<template>
  <RouterLink :to="`/tours/${route.slug}`"
    class="group card block overflow-hidden transition hover:-translate-y-1 hover:shadow-lift">
    <div class="relative aspect-[4/3] overflow-hidden bg-gradient-to-br from-brand-300 to-brand-600">
      <img :src="route.imageUrl" :alt="route.name" loading="lazy" @error="(e) => (e.target.style.display = 'none')"
        class="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
      <span class="absolute left-3 top-3 chip bg-white/90 backdrop-blur">{{ route.country }}</span>
      <span class="absolute right-12 top-3 rounded-full bg-ink-900/70 px-2.5 py-1 text-xs font-semibold text-white">
        {{ route.days }} 天
      </span>
      <!-- 收藏愛心 -->
      <button type="button" @click.prevent.stop="fav.toggle(route.id)"
        class="absolute right-3 top-3 grid h-8 w-8 place-items-center rounded-full bg-white/90 backdrop-blur transition hover:scale-110"
        :aria-label="fav.has(route.id) ? '取消收藏' : '加入收藏'">
        <svg width="16" height="16" viewBox="0 0 24 24"
          :fill="fav.has(route.id) ? '#e11d48' : 'none'" :stroke="fav.has(route.id) ? '#e11d48' : '#62718c'" stroke-width="2">
          <path d="M12 21s-7.5-4.6-10-9.3C.6 8.6 2.2 5 5.5 5c2 0 3.3 1.1 4.5 2.6C11.2 6.1 12.5 5 14.5 5 17.8 5 19.4 8.6 22 11.7 19.5 16.4 12 21 12 21z"/>
        </svg>
      </button>
    </div>
    <div class="p-4">
      <div class="flex items-center gap-1 text-xs text-ink-500">
        <RatingBadge :rating="route.rating" :reviews="route.reviews" />
        <span class="mx-1">·</span>
        <span>{{ route.location }}</span>
      </div>
      <h3 class="mt-1.5 line-clamp-1 text-base font-bold text-ink-900 group-hover:text-brand-700">{{ route.name }}</h3>
      <p class="mt-1 line-clamp-2 text-sm text-ink-500">{{ route.summary }}</p>
      <div class="mt-3 flex flex-wrap gap-1.5">
        <span v-for="t in tags" :key="t" class="chip">{{ t }}</span>
      </div>
      <div class="mt-4 flex items-end justify-between border-t border-ink-100 pt-3">
        <div>
          <span class="text-xs text-ink-400">每人</span>
          <p class="text-lg font-extrabold text-brand-700">{{ priceText }}</p>
        </div>
        <div class="flex items-center gap-2">
          <button type="button" @click.prevent.stop="compare.toggle(route)"
            :aria-pressed="compare.has(route.slug)"
            :class="['rounded-full px-3 py-2 text-xs font-semibold ring-1 transition',
                     compare.has(route.slug) ? 'bg-ink-900 text-white ring-ink-900' : 'text-ink-600 ring-ink-200 hover:bg-ink-50']">
            {{ compare.has(route.slug) ? "✓ 比較中" : "＋ 比較" }}
          </button>
          <span class="btn-outline px-4 py-2 text-xs">查看行程</span>
        </div>
      </div>
    </div>
  </RouterLink>
</template>
