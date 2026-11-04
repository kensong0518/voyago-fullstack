<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { LAND_PATH, BORDER_PATH } from "../data/europeMap";
import { stopsOf } from "../data/routeGeo";

// 歐洲路線圖：每條行程畫成一條經過各站的線。
// 地圖是預先算好的 SVG（scripts/map），不靠任何地圖服務；線寬、字級、站點大小都換算成螢幕像素，
// 地圖縮放（手機很小、行程頁放大到單一路線）時看起來一樣粗細。
const props = defineProps({
  routes: { type: Array, required: true },
  selected: { type: String, default: "" },     // 選中的行程 slug（實心綠線、所有站名）
  highlighted: { type: String, default: "" },  // 外部列表 hover 的行程（暫時加粗）
  focus: { type: String, default: "" },        // 有值時放大到這條路線（行程頁用）
  interactive: { type: Boolean, default: true },
  label: { type: String, default: "歐洲行程路線圖" },
});
const emit = defineEmits(["select", "hover"]);

const lines = computed(() => props.routes
  .map((r) => ({ route: r, stops: stopsOf(r) }))
  .filter((l) => l.stops.length));

// ---- 取景：全部路線的範圍，或放大到單一路線 ----
const ASPECT = 860 / 1000;
const viewBox = computed(() => {
  const target = props.focus ? lines.value.filter((l) => l.route.slug === props.focus) : lines.value;
  const pts = target.flatMap((l) => l.stops);
  if (!pts.length) return { x: 0, y: 0, w: 1000, h: 860 };
  let x0 = Math.min(...pts.map((p) => p.x)), x1 = Math.max(...pts.map((p) => p.x));
  let y0 = Math.min(...pts.map((p) => p.y)), y1 = Math.max(...pts.map((p) => p.y));
  const pad = props.focus ? 70 : 55;
  x0 -= pad; x1 += pad; y0 -= pad; y1 += pad;
  let w = Math.max(x1 - x0, props.focus ? 260 : 600), h = Math.max(y1 - y0, w * ASPECT);
  w = Math.max(w, h / ASPECT);
  const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
  return { x: Math.round(cx - w / 2), y: Math.round(cy - h / 2), w: Math.round(w), h: Math.round(h) };
});
const viewBoxAttr = computed(() => `${viewBox.value.x} ${viewBox.value.y} ${viewBox.value.w} ${viewBox.value.h}`);

// ---- 換算：1 個螢幕像素 = 幾個地圖單位 ----
const svgEl = ref(null);
const pxWidth = ref(1000);
let ro;
onMounted(() => {
  ro = new ResizeObserver(([e]) => { pxWidth.value = e.contentRect.width || 1000; });
  if (svgEl.value) ro.observe(svgEl.value);
});
onBeforeUnmount(() => ro?.disconnect());
const unit = computed(() => viewBox.value.w / Math.max(pxWidth.value, 1));
const fs = computed(() => (pxWidth.value < 480 ? 11 : 13) * unit.value);

const points = (stops) => stops.map((s) => `${s.x},${s.y}`).join(" ");
// 選中／hover 的路線另外疊一層畫在最上面（不能用調換 DOM 順序的方式：會讓鍵盤焦點跑掉）
const overlay = computed(() => {
  const out = [];
  const h = lines.value.find((l) => l.route.slug === props.highlighted && l.route.slug !== props.selected);
  const sel = lines.value.find((l) => l.route.slug === props.selected);
  if (h) out.push({ ...h, kind: "hover" });
  if (sel) out.push({ ...sel, kind: "selected" });
  return out;
});

function pick(slug) { if (props.interactive) emit("select", slug); }
function onKey(e, slug) {
  if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pick(slug); }
}
</script>

<template>
  <svg ref="svgEl" :viewBox="viewBoxAttr" class="block h-auto w-full select-none" role="group" :aria-label="label"
    preserveAspectRatio="xMidYMid meet">
    <rect :x="viewBox.x - 10" :y="viewBox.y - 10" :width="viewBox.w + 20" :height="viewBox.h + 20" class="fill-sea" />
    <path :d="LAND_PATH" class="fill-white" />
    <path :d="BORDER_PATH" fill="none" class="stroke-sea-line" stroke-width="1" vector-effect="non-scaling-stroke" />

    <!-- 每條路線一個可點、可用鍵盤操作的群組；順序固定 -->
    <g v-for="l in lines" :key="l.route.slug"
      :role="interactive ? 'button' : null" :tabindex="interactive ? 0 : null"
      :aria-label="interactive ? `${l.route.name}，${l.stops.map((s) => s.name).join('、')}` : null"
      :aria-pressed="interactive ? l.route.slug === selected : null"
      :class="['route', { 'cursor-pointer': interactive }]"
      @click="pick(l.route.slug)" @keydown="onKey($event, l.route.slug)"
      @pointerenter="interactive && emit('hover', l.route.slug)" @pointerleave="interactive && emit('hover', '')"
      @focus="emit('hover', l.route.slug)" @blur="emit('hover', '')">
      <!-- 感應範圍比線粗很多，手指也點得到 -->
      <polyline v-if="interactive && l.stops.length > 1" :points="points(l.stops)" fill="none" stroke="transparent"
        :stroke-width="22 * unit" stroke-linecap="round" stroke-linejoin="round" />
      <circle v-if="interactive" :cx="l.stops[0].x" :cy="l.stops[0].y" :r="14 * unit" fill="transparent" />

      <polyline v-if="l.stops.length > 1" :points="points(l.stops)"
        fill="none" class="line" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
      <circle v-for="(s, i) in l.stops" :key="s.key" :cx="s.x" :cy="s.y" :r="(i === 0 ? 4.5 : 3.2) * unit"
        class="stop" vector-effect="non-scaling-stroke" />
      <text :x="l.stops[0].x + 7 * unit" :y="l.stops[0].y - 6 * unit" :font-size="fs" :stroke-width="3 * unit"
        class="map-label map-label-main">{{ l.stops[0].name }}</text>
    </g>

    <!-- 最上層：hover 的路線（加粗）與選中的路線（綠色、標出所有站名） -->
    <g v-for="l in overlay" :key="'o-' + l.kind" :class="['overlay', l.kind]" aria-hidden="true" pointer-events="none">
      <polyline v-if="l.stops.length > 1" :points="points(l.stops)" fill="none" class="line"
        stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
      <circle v-for="(s, i) in l.stops" :key="s.key" :cx="s.x" :cy="s.y" :r="(i === 0 ? 4.5 : 3.2) * unit"
        class="stop" vector-effect="non-scaling-stroke" />
      <text v-for="(s, i) in l.stops" :key="'t' + s.key" :x="s.x + 7 * unit" :y="s.y - 6 * unit" :font-size="fs"
        :stroke-width="3 * unit" :class="['map-label', i === 0 ? 'map-label-main' : '']">{{ s.name }}</text>
    </g>
  </svg>
</template>

<style scoped>
.fill-sea { fill: #dbe5ec; }
.stroke-sea-line { stroke: #c3ced8; }
.route .line { stroke: #2b4a66; stroke-width: 2.5px; opacity: .55; }
.route .stop { fill: #fff; stroke: #2b4a66; stroke-width: 1.5px; }
.route:focus { outline: none; }
.route:focus-visible .line { stroke-width: 5px; opacity: 1; }
.overlay .line { stroke: #2b4a66; stroke-width: 4px; }
.overlay .stop { fill: #fff; stroke: #2b4a66; stroke-width: 2px; }
.overlay.selected .line { stroke: #0a9065; stroke-width: 4.5px; }
.overlay.selected .stop { stroke: #0a9065; }
.map-label { fill: #18324a; font-weight: 500; paint-order: stroke; stroke: #fff; stroke-linejoin: round; pointer-events: none; }
.map-label-main { font-weight: 700; }
</style>
