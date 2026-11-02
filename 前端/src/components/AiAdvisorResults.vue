<script setup>
import RouteCard from "./RouteCard.vue";

// AI 推薦結果：卡片資料一律來自資料庫（價格、天數不採用 AI 寫的數字），AI 只提供推薦理由。
const props = defineProps({ advisor: { type: Object, required: true } });
</script>

<template>
  <section v-if="['loading', 'done', 'error'].includes(advisor.status.value)" id="ai-results"
    class="scroll-mt-16 border-b border-ink-100 bg-gradient-to-b from-brand-50 to-white py-12" aria-live="polite">
    <div class="container-x">
      <p class="text-sm font-semibold text-brand-700">✦ AI 行程顧問</p>
      <h2 class="mt-1 text-2xl font-extrabold">「{{ advisor.lastQuery.value }}」</h2>

      <div v-if="advisor.status.value === 'loading'" class="mt-6">
        <p class="text-ink-500">正在比對所有行程的天數、價格與主題…（第一次使用可能要 10–20 秒）</p>
        <div class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="i in 3" :key="i" class="card overflow-hidden">
            <div class="aspect-[4/3] animate-pulse bg-ink-100" />
            <div class="space-y-2 p-4"><div class="h-4 w-2/3 animate-pulse rounded bg-ink-100" /><div class="h-3 w-full animate-pulse rounded bg-ink-100" /></div>
          </div>
        </div>
      </div>

      <div v-else-if="advisor.status.value === 'error'" class="mt-6 rounded-2xl bg-white p-6 ring-1 ring-amber-200">
        <p class="font-semibold text-amber-800">{{ advisor.error.value }}</p>
        <RouterLink to="/tours" class="btn-outline mt-4">瀏覽全部行程</RouterLink>
      </div>

      <template v-else>
        <p class="mt-3 max-w-3xl text-ink-700">{{ advisor.message.value }}</p>
        <div v-if="advisor.results.value.length" class="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="(r, i) in advisor.results.value" :key="r.route.id" class="flex flex-col gap-3">
            <RouteCard :route="r.route" />
            <p class="rounded-xl bg-white p-3 text-sm text-ink-700 ring-1 ring-brand-100">
              <span class="font-bold text-brand-700">推薦 {{ i + 1 }}・</span>{{ r.reason }}
            </p>
          </div>
        </div>
        <RouterLink v-else to="/tours" class="btn-outline mt-6">瀏覽全部行程</RouterLink>
      </template>

      <p class="mt-6 text-xs text-ink-400">
        推薦只會從本站現有的行程中挑選，價格與天數以行程頁為準。
        <template v-if="advisor.remaining.value !== null">今天還可以再問 {{ advisor.remaining.value }} 次。</template>
      </p>
    </div>
  </section>
</template>
