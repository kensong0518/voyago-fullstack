<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import api from "../api/api";
import { useAuthStore } from "../stores/auth";
import { avatarColor } from "../data/content";

// 行程頁的真實評價：只有付過款的會員能寫，每人一則可修改；平均分數由後端重算。
const props = defineProps({ slug: { type: String, required: true } });
const emit = defineEmits(["changed"]);   // 評價變動後通知父層更新頁首的平均分數

const auth = useAuthStore();
const route = useRoute();

const summary = ref({ average: 0, count: 0, distribution: [0, 0, 0, 0, 0], items: [] });
const loading = ref(true);
const eligibility = ref(null);
const editing = ref(false);
const form = ref({ rating: 0, content: "" });
const saving = ref(false);
const error = ref("");

const hasMine = computed(() => eligibility.value?.myRating != null);
const maxBar = computed(() => Math.max(1, ...summary.value.distribution));
const dateText = (d) => (d ? new Date(d).toLocaleDateString("zh-TW") : "");

async function load() {
  loading.value = true;
  try {
    summary.value = await api.getReviews(props.slug);
    eligibility.value = auth.isLoggedIn ? await api.getReviewEligibility(props.slug) : null;
  } catch { /* 評價載入失敗不影響行程頁其他內容 */ }
  finally { loading.value = false; }
}
onMounted(load);   // 網址帶 #reviews 時由 router 的 scrollBehavior 捲過來
watch(() => [props.slug, auth.isLoggedIn], load);

function startEdit() {
  form.value = { rating: eligibility.value?.myRating || 0, content: eligibility.value?.myContent || "" };
  error.value = "";
  editing.value = true;
}

async function submit() {
  error.value = "";
  if (!form.value.rating) { error.value = "請選擇星等"; return; }
  if (form.value.content.trim().length < 5) { error.value = "心得請至少寫 5 個字"; return; }
  saving.value = true;
  try {
    summary.value = await api.saveMyReview(props.slug, { rating: form.value.rating, content: form.value.content.trim() });
    eligibility.value = { ...eligibility.value, myRating: form.value.rating, myContent: form.value.content.trim() };
    editing.value = false;
    emit("changed", summary.value);
  } catch (e) { error.value = e.message || "送出失敗，請稍後再試"; }
  finally { saving.value = false; }
}

async function removeMine() {
  if (!window.confirm("確定要刪除你的評價嗎？")) return;
  saving.value = true;
  try {
    summary.value = await api.deleteMyReview(props.slug);
    eligibility.value = { ...eligibility.value, myRating: null, myContent: null };
    editing.value = false;
    emit("changed", summary.value);
  } catch (e) { error.value = e.message || "刪除失敗"; }
  finally { saving.value = false; }
}
</script>

<template>
  <section id="reviews" class="scroll-mt-20">
    <div class="mt-10 flex flex-wrap items-center justify-between gap-3">
      <h2 class="text-xl font-bold">旅人評價</h2>
      <span v-if="summary.count" class="text-sm text-ink-500">{{ summary.count }} 則・僅限完成付款的旅客</span>
    </div>

    <div v-if="loading" class="mt-4 h-28 animate-pulse rounded-2xl bg-ink-100" />

    <template v-else>
      <!-- 總覽 -->
      <div v-if="summary.count" class="mt-4 flex flex-col gap-5 rounded-2xl bg-ink-50 p-5 sm:flex-row sm:items-center">
        <div class="text-center sm:w-32">
          <p class="text-4xl font-extrabold">{{ summary.average.toFixed(1) }}</p>
          <p class="text-amber-500" aria-hidden="true">
            <span v-for="s in 5" :key="s">{{ s <= Math.round(summary.average) ? "★" : "☆" }}</span>
          </p>
          <p class="text-xs text-ink-400">{{ summary.count }} 則評價</p>
        </div>
        <ul class="flex-1 space-y-1.5" aria-label="星等分佈">
          <li v-for="star in [5, 4, 3, 2, 1]" :key="star" class="flex items-center gap-2 text-xs text-ink-500">
            <span class="w-6">{{ star }} ★</span>
            <span class="h-2 flex-1 overflow-hidden rounded-full bg-white">
              <span class="block h-full rounded-full bg-amber-400" :style="{ width: (summary.distribution[star - 1] / maxBar) * 100 + '%' }" />
            </span>
            <span class="w-6 text-right">{{ summary.distribution[star - 1] }}</span>
          </li>
        </ul>
      </div>
      <div v-else class="mt-4 rounded-2xl border border-dashed border-ink-200 px-5 py-8 text-center">
                <p class="mt-2 font-semibold text-ink-700">還沒有人評價這個行程</p>
        <p class="mt-1 text-sm text-ink-500">評價只開放給完成付款的旅客，每一則都是真的。</p>
      </div>

      <!-- 我的評價／寫評價 -->
      <div class="mt-4">
        <p v-if="!auth.isLoggedIn" class="text-sm text-ink-500">
          <RouterLink :to="{ name: 'login', query: { next: route.fullPath + '#reviews' } }" class="font-semibold text-brand-700 hover:underline">登入</RouterLink>
          並完成付款後，就能分享你的旅程心得。
        </p>
        <p v-else-if="eligibility && !eligibility.canReview" class="text-sm text-ink-500">{{ eligibility.reason }}。</p>
        <template v-else-if="eligibility?.canReview">
          <form v-if="editing" @submit.prevent="submit" class="card p-5">
            <p class="text-sm font-semibold">{{ hasMine ? "修改你的評價" : "分享你的旅程" }}</p>
            <div class="mt-2 flex gap-1" role="radiogroup" aria-label="星等">
              <button v-for="s in 5" :key="s" type="button" role="radio" :aria-checked="form.rating === s" :aria-label="`${s} 星`"
                @click="form.rating = s"
                :class="['text-3xl leading-none transition', s <= form.rating ? 'text-amber-500' : 'text-ink-200 hover:text-amber-300']">★</button>
            </div>
            <textarea v-model="form.content" rows="4" maxlength="500" placeholder="行程安排、住宿、交通、哪裡最難忘？（5–500 字）"
              class="input mt-3 w-full" />
            <div class="mt-1 text-right text-xs text-ink-400">{{ form.content.length }} / 500</div>
            <p v-if="error" class="mt-2 text-sm text-rose-600">{{ error }}</p>
            <div class="mt-3 flex flex-wrap gap-2">
              <button :disabled="saving" class="btn-primary px-5 py-2 text-sm disabled:opacity-60">{{ saving ? "送出中…" : "送出評價" }}</button>
              <button type="button" @click="editing = false" class="btn-outline px-5 py-2 text-sm">取消</button>
              <button v-if="hasMine" type="button" @click="removeMine" :disabled="saving" class="ml-auto text-sm text-rose-600 hover:underline">刪除我的評價</button>
            </div>
          </form>
          <div v-else class="flex flex-wrap items-center gap-3">
            <button type="button" @click="startEdit" class="btn-primary px-5 py-2 text-sm">{{ hasMine ? "修改我的評價" : "寫評價" }}</button>
            <span v-if="hasMine" class="text-sm text-ink-500">你給了 {{ eligibility.myRating }} 星</span>
          </div>
        </template>
      </div>

      <!-- 評價列表 -->
      <div v-if="summary.items.length" class="mt-5 grid gap-4 sm:grid-cols-2">
        <article v-for="r in summary.items" :key="r.id" class="card p-4">
          <div class="flex items-center gap-3">
            <span class="grid h-9 w-9 place-items-center rounded-full text-sm font-bold text-white" :class="avatarColor(r.authorName)">{{ r.authorName.slice(0, 1) }}</span>
            <div>
              <p class="text-sm font-bold">{{ r.authorName }} <span class="ml-1 rounded-full bg-brand-50 px-1.5 py-0.5 text-[10px] font-semibold text-brand-700">已購買</span></p>
              <div class="flex items-center gap-1 text-xs text-amber-500">
                <span :aria-label="`${r.rating} 星`"><span v-for="s in 5" :key="s" aria-hidden="true">{{ s <= r.rating ? "★" : "☆" }}</span></span>
                <span class="ml-1 text-ink-400">{{ dateText(r.updatedAt) }}</span>
              </div>
            </div>
          </div>
          <p class="mt-3 whitespace-pre-line break-words text-sm text-ink-600">{{ r.content }}</p>
        </article>
      </div>
    </template>
  </section>
</template>
