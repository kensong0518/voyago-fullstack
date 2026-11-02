<script setup>
import { computed, ref } from "vue";

// 單一數列的長條圖（純 SVG，不引入圖表套件）。
// 一個圖只放一個量（不做雙 Y 軸）；顏色單一品牌綠，標題就是圖例。
const props = defineProps({
  points: { type: Array, required: true },        // [{ key, label, value, tip }]
  format: { type: Function, default: (v) => String(v) },
  height: { type: Number, default: 180 },
  ariaLabel: { type: String, default: "" },
});

const W = 640, PAD_L = 52, PAD_R = 8, PAD_T = 10, PAD_B = 26;
const H = computed(() => props.height);
const max = computed(() => niceMax(Math.max(0, ...props.points.map((p) => p.value))));
const plotW = W - PAD_L - PAD_R;
const plotH = computed(() => H.value - PAD_T - PAD_B);
const slot = computed(() => plotW / Math.max(1, props.points.length));
const barW = computed(() => Math.max(2, Math.min(28, slot.value - 2)));   // 相鄰長條留 2px 間隙
const y = (v) => PAD_T + plotH.value - (max.value ? (v / max.value) * plotH.value : 0);
const ticks = computed(() => [0, 0.5, 1].map((t) => t * max.value));
// X 軸只標頭、中、尾，避免擠成一團
const labelIdx = computed(() => {
  const n = props.points.length;
  return new Set(n <= 1 ? [0] : [0, Math.floor((n - 1) / 2), n - 1]);
});

function niceMax(v) {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * p;
}

// 只有頂端圓角（4px），底部貼齊基線
function barPath(i, v) {
  const x = PAD_L + i * slot.value + (slot.value - barW.value) / 2;
  const top = y(v), base = PAD_T + plotH.value;
  const h = base - top;
  if (h <= 0) return "";
  const r = Math.min(4, barW.value / 2, h);
  return `M${x},${base}V${top + r}Q${x},${top} ${x + r},${top}H${x + barW.value - r}Q${x + barW.value},${top} ${x + barW.value},${top + r}V${base}Z`;
}

const hover = ref(-1);
const tipStyle = computed(() => {
  if (hover.value < 0) return {};
  const cx = PAD_L + (hover.value + 0.5) * slot.value;
  return { left: `${(cx / W) * 100}%` };
});
</script>

<template>
  <div class="relative" @mouseleave="hover = -1">
    <svg :viewBox="`0 0 ${W} ${H}`" class="h-auto w-full" role="img" :aria-label="ariaLabel">
      <!-- 格線與 Y 軸刻度：淡色、不搶資料 -->
      <g v-for="t in ticks" :key="t">
        <line :x1="PAD_L" :x2="W - PAD_R" :y1="y(t)" :y2="y(t)" stroke="#eceef2" stroke-width="1" />
        <text :x="PAD_L - 6" :y="y(t) + 4" text-anchor="end" font-size="11" fill="#8290a8">{{ format(t) }}</text>
      </g>
      <path v-for="(p, i) in points" :key="p.key" :d="barPath(i, p.value)"
        :fill="hover === i ? '#097353' : '#0a9065'" />
      <text v-for="i in [...labelIdx]" :key="'l' + i" :x="PAD_L + (i + 0.5) * slot" :y="H - 8"
        text-anchor="middle" font-size="11" fill="#8290a8">{{ points[i]?.label }}</text>
      <!-- 感應區比長條大：整欄都能 hover，0 的那天也看得到數字 -->
      <rect v-for="(p, i) in points" :key="'h' + p.key" :x="PAD_L + i * slot" :y="PAD_T" :width="slot" :height="plotH"
        fill="transparent" @mouseenter="hover = i" @touchstart.passive="hover = i" />
    </svg>
    <div v-if="hover >= 0" :style="tipStyle"
      class="pointer-events-none absolute top-0 z-10 -translate-x-1/2 whitespace-nowrap rounded-lg bg-ink-900 px-2.5 py-1.5 text-xs text-white shadow-lg">
      <p class="font-semibold">{{ points[hover].tip || points[hover].label }}</p>
      <p>{{ format(points[hover].value) }}</p>
    </div>
  </div>
</template>
