import { onMounted, ref } from "vue";
import api from "../api/api";

/**
 * AI 行程顧問的狀態：輸入框（在首頁 Hero）與結果區（Hero 下方）共用同一份。
 * status：checking | disabled | idle | loading | done | error
 */
export function useAiAdvisor() {
  const status = ref("checking");
  const query = ref("");
  const message = ref("");
  const results = ref([]);
  const error = ref("");
  const remaining = ref(null);
  const dailyLimit = ref(0);
  const lastQuery = ref("");

  onMounted(async () => {
    try {
      const s = await api.aiStatus();
      dailyLimit.value = s.dailyLimit;
      status.value = s.enabled ? "idle" : "disabled";
    } catch {
      status.value = "disabled";
    }
  });

  async function ask(text) {
    const q = (text ?? query.value).trim();
    if (q.length < 2 || status.value === "loading" || status.value === "disabled") return;
    query.value = q;
    lastQuery.value = q;
    status.value = "loading";
    error.value = "";
    try {
      const res = await api.askAdvisor(q);
      message.value = res.message;
      results.value = res.recommendations || [];
      if (!res.cached) remaining.value = res.remainingToday;
      status.value = "done";
    } catch (e) {
      error.value = e.message;
      if (e.status === 429) remaining.value = 0;
      status.value = "error";
    }
  }

  return { status, query, message, results, error, remaining, dailyLimit, lastQuery, ask };
}
