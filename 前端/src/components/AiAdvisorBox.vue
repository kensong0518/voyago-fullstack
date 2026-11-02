<script setup>
// AI 行程顧問輸入框（放在首頁 Hero 的深色背景上）。狀態由 useAiAdvisor 管理，結果顯示在 AiAdvisorResults。
const props = defineProps({ advisor: { type: Object, required: true } });

const EXAMPLES = [
  "兩個人、預算 15 萬，想看雪景",
  "一週左右的城市漫遊，喜歡美食和藝術",
  "蜜月想去看海、拍照好看的地方",
];

function submit() {
  props.advisor.ask();
}
</script>

<template>
  <div class="rounded-2xl bg-white/10 p-4 ring-1 ring-white/20 backdrop-blur sm:p-5">
    <div class="flex items-center gap-2 text-sm font-semibold text-brand-300">
      <span aria-hidden="true">✦</span> AI 行程顧問
      <span class="rounded-full bg-white/10 px-2 py-0.5 text-[11px] font-medium text-white/70">Gemini</span>
    </div>

    <form class="mt-3 flex flex-col gap-2 sm:flex-row" @submit.prevent="submit">
      <label for="ai-query" class="sr-only">描述你想要的旅程</label>
      <input id="ai-query" v-model="advisor.query.value" maxlength="300"
        :disabled="advisor.status.value === 'disabled' || advisor.status.value === 'checking'"
        placeholder="說說你想怎麼玩，例如：三個人、十天內、想看極光"
        class="min-w-0 flex-1 rounded-xl border-0 bg-white px-4 py-3 text-base text-ink-900 placeholder:text-ink-400 focus:outline-none focus:ring-2 focus:ring-brand-400 disabled:bg-white/60" />
      <button type="submit"
        :disabled="advisor.status.value === 'loading' || advisor.status.value === 'disabled' || advisor.query.value.trim().length < 2"
        class="btn-primary whitespace-nowrap px-6 py-3 text-base disabled:cursor-not-allowed disabled:opacity-60">
        {{ advisor.status.value === 'loading' ? 'AI 挑選中…' : '幫我挑行程' }}
      </button>
    </form>

    <div v-if="advisor.status.value === 'disabled'" class="mt-3 text-sm text-white/80">
      AI 顧問準備中，先
      <RouterLink to="/tours" class="font-semibold text-brand-300 underline">瀏覽全部行程</RouterLink>
      吧。
    </div>
    <div v-else class="mt-3 flex flex-wrap items-center gap-2">
      <span class="text-xs text-white/60">試試看：</span>
      <button v-for="ex in EXAMPLES" :key="ex" type="button" :disabled="advisor.status.value === 'loading'"
        class="rounded-full bg-white/10 px-3 py-1.5 text-xs text-white/90 transition hover:bg-white/20 disabled:opacity-50"
        @click="advisor.ask(ex)">{{ ex }}</button>
    </div>
  </div>
</template>
