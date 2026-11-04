<script setup>
// AI 行程顧問輸入框。狀態由 useAiAdvisor 管理，結果顯示在 AiAdvisorResults。
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
  <div>
    <form class="flex flex-col gap-2 sm:flex-row" @submit.prevent="submit">
      <label for="ai-query" class="sr-only">AI 行程顧問：描述你想要的旅程</label>
      <input id="ai-query" v-model="advisor.query.value" maxlength="300"
        :disabled="advisor.status.value === 'disabled' || advisor.status.value === 'checking'"
        placeholder="例如：三個人、十天內、想看極光"
        class="input min-w-0 flex-1 py-3 text-base disabled:bg-ink-50" />
      <button type="submit"
        :disabled="advisor.status.value === 'loading' || advisor.status.value === 'disabled' || advisor.query.value.trim().length < 2"
        class="btn-primary whitespace-nowrap px-6 py-3 text-base disabled:cursor-not-allowed">
        {{ advisor.status.value === 'loading' ? 'AI 挑選中…' : '幫我挑行程' }}
      </button>
    </form>

    <p v-if="advisor.status.value === 'disabled'" class="mt-3 text-sm text-ink-600">
      AI 行程顧問暫時沒有開放，可以先
      <RouterLink to="/tours" class="font-semibold text-sea-deep underline">瀏覽全部行程</RouterLink>。
    </p>
    <div v-else class="mt-3 flex flex-wrap items-center gap-2">
      <span class="text-xs text-ink-500">試試看：</span>
      <button v-for="ex in EXAMPLES" :key="ex" type="button" :disabled="advisor.status.value === 'loading'"
        class="rounded-md bg-sea/50 px-3 py-1.5 text-xs text-navy transition hover:bg-sea disabled:opacity-50"
        @click="advisor.ask(ex)">{{ ex }}</button>
    </div>
  </div>
</template>
