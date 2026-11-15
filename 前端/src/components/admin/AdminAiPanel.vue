<script setup>
import { computed, onActivated, ref } from "vue";
import api from "../../api/api";
import BarChart from "./BarChart.vue";

// AI 行程顧問的使用狀況：每次真的呼叫模型都會記一筆（快取命中不算）。
const REASONS = {
  QUOTA_EXCEEDED: "額度用完", TIMEOUT: "逾時", BAD_RESPONSE: "回答格式錯誤", NOT_CONFIGURED: "未設定金鑰", RATE_LIMITED: "次數用完",
};
const RANGES = [{ v: 7, l: "近 7 天" }, { v: 30, l: "近 30 天" }, { v: 90, l: "近 90 天" }];
const days = ref(7);
const data = ref(null);
const loading = ref(false);
const err = ref("");

const pct = (x) => (x * 100).toFixed(1) + "%";
const sec = (ms) => (ms / 1000).toFixed(1) + " 秒";
const ts = (t) => new Date(t).toLocaleString("zh-TW", { hour12: false });
const dayPoints = computed(() => (data.value?.byDay || []).map((d) => ({
  key: d.date, label: d.date.slice(5), value: d.calls, tip: `${d.date}：${d.calls} 次，失敗 ${d.errors} 次`,
})));
const cards = computed(() => {
  const d = data.value;
  if (!d) return [];
  return [
    { l: "呼叫次數", v: d.calls, note: "快取命中不算" },
    { l: "失敗率", v: pct(d.errorRate), note: `${d.errors} 次失敗` },
    { l: "平均 / p95 延遲", v: `${sec(d.avgLatencyMs)} / ${sec(d.p95LatencyMs)}`, note: "只算成功的呼叫" },
    { l: "模型編造的行程", v: d.rejected, note: `共推薦 ${d.picks} 個，編造的已被過濾` },
  ];
});

async function load() {
  loading.value = true; err.value = "";
  try { data.value = await api.adminAiStats(days.value); }
  catch (e) { err.value = e.message; }
  finally { loading.value = false; }
}
function setDays(d) { days.value = d; load(); }
onActivated(load);
</script>

<template>
  <div>
    <div class="flex flex-wrap items-center justify-between gap-3">
      <p class="text-sm text-ink-500">每次實際呼叫 AI 都會記錄提示詞版本、耗時與結果，保留 90 天。</p>
      <button type="button" @click="load" class="btn-outline px-4 py-2 text-sm">重新整理</button>
    </div>
    <div class="mt-3 inline-flex rounded-full bg-ink-50 p-1" role="group" aria-label="時間範圍">
      <button v-for="r in RANGES" :key="r.v" type="button" @click="setDays(r.v)" :aria-pressed="days === r.v"
        :class="['rounded-full px-4 py-1.5 text-sm font-semibold transition', days === r.v ? 'bg-white text-ink-900 shadow' : 'text-ink-500 hover:text-ink-800']">
        {{ r.l }}
      </button>
    </div>
    <p v-if="err" class="mt-3 text-sm text-red-600">{{ err }}</p>
    <div v-if="loading && !data" class="mt-4 h-32 animate-pulse rounded-2xl bg-ink-100" />

    <template v-else-if="data">
      <p v-if="data.truncated" class="mt-3 rounded-xl bg-amber-50 px-4 py-2 text-sm text-amber-800">資料量超過 5,000 筆，這裡只統計最新的 5,000 筆。</p>

      <dl class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div v-for="c in cards" :key="c.l" class="rounded-2xl bg-ink-50 p-4">
          <dt class="text-xs text-ink-500">{{ c.l }}</dt>
          <dd class="mt-1 text-2xl font-extrabold text-ink-900">{{ c.v }}</dd>
          <p class="mt-0.5 text-xs text-ink-400">{{ c.note }}</p>
        </div>
      </dl>

      <section class="mt-6">
        <h3 class="text-sm font-bold">每日呼叫次數</h3>
        <BarChart class="mt-2" :points="dayPoints" :ariaLabel="`近 ${data.days} 天每日 AI 呼叫次數`" />
      </section>

      <section class="mt-6">
        <h3 class="text-sm font-bold">各版提示詞比較</h3>
        <p class="text-xs text-ink-400">改提示詞後，看新舊版本的失敗率與延遲有沒有變好。</p>
        <div class="mt-2 overflow-x-auto">
          <table class="w-full min-w-[480px] text-left text-sm">
            <thead class="text-xs text-ink-500"><tr><th class="py-2 pr-3">版本</th><th class="pr-3">呼叫</th><th class="pr-3">失敗</th><th class="pr-3">平均延遲</th><th>編造</th></tr></thead>
            <tbody class="divide-y divide-ink-100">
              <tr v-for="v in data.byVersion" :key="v.version">
                <td class="py-2 pr-3 font-mono text-xs">{{ v.version }}</td><td class="pr-3">{{ v.calls }}</td>
                <td class="pr-3">{{ v.errors }}</td><td class="pr-3">{{ sec(v.avgLatencyMs) }}</td><td>{{ v.rejected }}</td>
              </tr>
              <tr v-if="!data.byVersion.length"><td colspan="5" class="py-6 text-center text-ink-400">這段期間還沒有 AI 呼叫</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-if="Object.keys(data.errorReasons).length" class="mt-6">
        <h3 class="text-sm font-bold">失敗原因</h3>
        <ul class="mt-2 flex flex-wrap gap-2 text-sm">
          <li v-for="(n, k) in data.errorReasons" :key="k" class="rounded-full bg-rose-50 px-3 py-1 text-rose-700">{{ REASONS[k] || k }} {{ n }}</li>
        </ul>
      </section>

      <section class="mt-6">
        <h3 class="text-sm font-bold">最近 20 次</h3>
        <ul class="mt-2 divide-y divide-ink-100">
          <li v-for="(r, i) in data.recent" :key="i" class="flex flex-col gap-1 py-3 text-sm sm:flex-row sm:gap-4">
            <span class="w-40 shrink-0 text-xs text-ink-400">{{ ts(r.at) }}</span>
            <div class="min-w-0 flex-1">
              <p class="break-words">{{ r.query }}</p>
              <p class="text-xs text-ink-500">
                <span :class="r.status === 'OK' ? 'text-brand-700' : 'font-semibold text-rose-600'">{{ r.status === 'OK' ? '成功' : (REASONS[r.errorReason] || '失敗') }}</span>
                ・{{ sec(r.latencyMs) }}・{{ r.version }}
                <template v-if="r.picks">・推薦 {{ r.picks }}</template>
                <template v-if="r.rejected">・<span class="text-rose-600">編造 {{ r.rejected }}</span></template>
              </p>
            </div>
          </li>
          <li v-if="!data.recent.length" class="py-8 text-center text-ink-400">還沒有紀錄</li>
        </ul>
      </section>
    </template>
  </div>
</template>
