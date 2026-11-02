<script setup>
// Voyago 品牌標誌：導覽列與頁尾共用同一份，避免兩處各寫一次而走樣。
// 圖形與 public/favicon.svg 相同（綠底白色 V、右上一點代表目的地）。
defineProps({
  size: { type: String, default: "md" }, // md：導覽列；sm：頁尾
});
</script>

<template>
  <span class="flex items-center gap-2.5" aria-label="Voyago 歐洲自助遊">
    <svg :class="size === 'sm' ? 'h-8 w-8' : 'h-9 w-9'" viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="16" class="fill-brand-600" />
      <path d="M17 19 L32 47 L47 19" fill="none" stroke="#fff" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" />
      <circle cx="47" cy="19" r="4.5" class="fill-brand-300" />
    </svg>
    <span class="flex flex-col leading-none">
      <span class="text-lg font-extrabold tracking-tight text-ink-900">Voyago</span>
      <span class="mt-1 text-[11px] font-medium tracking-[0.2em] text-ink-500">歐洲自助遊</span>
    </span>
  </span>
</template>
