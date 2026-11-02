<script setup>
import { computed, onActivated, ref } from "vue";
import api from "../../api/api";
import BarChart from "./BarChart.vue";

const RANGES = [7, 30, 90];
const days = ref(30);
const data = ref(null);
const loading = ref(false);
const err = ref("");
const exportMsg = ref("");
const exporting = ref("");

const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);
const short = (n) => (n >= 10000 ? `${Math.round(n / 1000) / 10}萬` : String(Math.round(n)));
const md = (d) => d.slice(5).replace("-", "/");

const revenuePoints = computed(() => (data.value?.daily || []).map((d) => ({
  key: d.date, label: md(d.date), value: d.revenue, tip: `${md(d.date)}・已付款 ${d.paidOrders} 筆`,
})));
const orderPoints = computed(() => (data.value?.daily || []).map((d) => ({
  key: d.date, label: md(d.date), value: d.orders, tip: `${md(d.date)}・其中已付款 ${d.paidOrders} 筆`,
})));
const topMax = computed(() => Math.max(1, ...(data.value?.topRoutes || []).map((t) => t.revenue)));
const paidRate = computed(() => (data.value?.orders ? Math.round((data.value.paidOrders / data.value.orders) * 100) : 0));

async function load() {
  loading.value = true; err.value = "";
  try { data.value = await api.adminReport(days.value); }
  catch (e) { err.value = e.message; }
  finally { loading.value = false; }
}
function setDays(d) { days.value = d; load(); }

async function exportCsv(kind, params) {
  exporting.value = kind; exportMsg.value = "";
  try {
    const r = await api.adminExportCsv(kind, params);
    exportMsg.value = `✓ 已下載 ${r.name}（${r.rows} 筆）`;
  } catch (e) { exportMsg.value = e.message || "匯出失敗"; }
  finally { exporting.value = ""; }
}

onActivated(load);   // 每次切回報表分頁都更新數字
defineExpose({ reload: load });
</script>

<template>
  <div class="space-y-6">
    <!-- 篩選列：區間 + 匯出 -->
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="inline-flex rounded-full bg-ink-50 p-1" role="group" aria-label="報表區間">
        <button v-for="d in RANGES" :key="d" type="button" @click="setDays(d)" :aria-pressed="days === d"
          :class="['rounded-full px-4 py-1.5 text-sm font-semibold transition', days === d ? 'bg-white text-ink-900 shadow' : 'text-ink-500 hover:text-ink-800']">
          近 {{ d }} 天
        </button>
      </div>
      <div class="flex flex-wrap gap-2">
        <button type="button" @click="exportCsv('bookings')" :disabled="!!exporting" class="btn-outline px-4 py-2 text-sm disabled:opacity-60">
          {{ exporting === 'bookings' ? '匯出中…' : '⬇ 匯出訂單 CSV' }}
        </button>
        <button type="button" @click="exportCsv('members')" :disabled="!!exporting" class="btn-outline px-4 py-2 text-sm disabled:opacity-60">
          {{ exporting === 'members' ? '匯出中…' : '⬇ 匯出會員 CSV' }}
        </button>
      </div>
    </div>
    <p v-if="exportMsg" class="text-sm text-ink-600" role="status">{{ exportMsg }}</p>
    <p v-if="err" class="card p-4 text-sm text-red-600">{{ err }}</p>

    <div v-if="loading && !data" class="h-64 animate-pulse rounded-2xl bg-ink-100" />

    <template v-else-if="data">
      <!-- 區間摘要 -->
      <div class="grid grid-cols-3 gap-3">
        <div class="card p-4"><p class="text-xs text-ink-400">新訂單</p><p class="mt-1 text-2xl font-extrabold">{{ data.orders }}</p></div>
        <div class="card p-4"><p class="text-xs text-ink-400">已付款</p><p class="mt-1 text-2xl font-extrabold">{{ data.paidOrders }}</p>
          <p class="text-xs text-ink-400">付款率 {{ paidRate }}%</p></div>
        <div class="card p-4"><p class="text-xs text-ink-400">已付款營收</p><p class="mt-1 text-xl font-extrabold text-brand-700 sm:text-2xl">{{ twd(data.revenue) }}</p></div>
      </div>

      <div class="grid gap-6 lg:grid-cols-2">
        <section class="card p-5">
          <h3 class="font-bold">每日已付款營收</h3>
          <p class="text-xs text-ink-400">以訂單建立日計算（台灣時間）</p>
          <BarChart class="mt-3" :points="revenuePoints" :format="short" :aria-label="`近 ${data.days} 天每日已付款營收長條圖`" />
        </section>
        <section class="card p-5">
          <h3 class="font-bold">每日新訂單</h3>
          <p class="text-xs text-ink-400">含待付款與已取消</p>
          <BarChart class="mt-3" :points="orderPoints" :aria-label="`近 ${data.days} 天每日新訂單數長條圖`" />
        </section>
      </div>

      <section class="card p-5">
        <h3 class="font-bold">熱賣行程（依已付款營收）</h3>
        <p v-if="!data.topRoutes.length" class="mt-3 text-sm text-ink-400">這段期間還沒有已付款的訂單。</p>
        <ol v-else class="mt-3 space-y-3">
          <li v-for="(t, i) in data.topRoutes" :key="t.routeId" class="text-sm">
            <div class="flex items-baseline justify-between gap-3">
              <RouterLink :to="`/tours/${t.slug}`" class="truncate font-medium hover:text-brand-700">{{ i + 1 }}. {{ t.name }}</RouterLink>
              <span class="shrink-0 text-ink-600">{{ twd(t.revenue) }}<span class="ml-1 text-xs text-ink-400">・{{ t.paidOrders }} 筆</span></span>
            </div>
            <div class="mt-1 h-2 rounded-full bg-ink-50"><div class="h-2 rounded-full bg-brand-600" :style="{ width: (t.revenue / topMax) * 100 + '%' }" /></div>
          </li>
        </ol>
      </section>

      <!-- 表格檢視：圖表的同一份數字，給要看精確值或用螢幕閱讀器的人 -->
      <details class="card p-5">
        <summary class="cursor-pointer text-sm font-semibold text-ink-600">以表格檢視每日數字</summary>
        <div class="mt-3 max-h-80 overflow-auto">
          <table class="w-full text-sm">
            <thead class="sticky top-0 bg-white text-left text-xs text-ink-400">
              <tr><th class="py-2">日期</th><th class="py-2 text-right">新訂單</th><th class="py-2 text-right">已付款</th><th class="py-2 text-right">營收</th></tr>
            </thead>
            <tbody>
              <tr v-for="d in [...data.daily].reverse()" :key="d.date" class="border-t border-ink-100">
                <td class="py-1.5">{{ d.date }}</td><td class="py-1.5 text-right">{{ d.orders }}</td>
                <td class="py-1.5 text-right">{{ d.paidOrders }}</td><td class="py-1.5 text-right">{{ twd(d.revenue) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </template>
  </div>
</template>
