<script setup>
// 評分顯示：沒有任何評價時明確寫「尚無評價」，不顯示 0.0 這種看起來像負評的數字
defineProps({
  rating: { type: Number, default: 0 },
  reviews: { type: Number, default: 0 },
  size: { type: String, default: "sm" },   // sm | md
});
</script>

<template>
  <span v-if="reviews > 0" class="inline-flex items-center gap-1" :aria-label="`平均 ${rating.toFixed(1)} 星，${reviews} 則評價`">
    <svg :width="size === 'md' ? 16 : 14" :height="size === 'md' ? 16 : 14" viewBox="0 0 24 24" fill="currentColor" class="text-amber-500"><path d="M12 2l2.9 6.3 6.9.7-5.1 4.6 1.4 6.8L12 17.8 5.9 21l1.4-6.8L2.2 9.6l6.9-.7z"/></svg>
    <span class="font-semibold text-ink-700">{{ rating.toFixed(1) }}</span>
    <span class="text-ink-400">({{ reviews }})</span>
  </span>
  <span v-else class="text-ink-400">尚無評價</span>
</template>
