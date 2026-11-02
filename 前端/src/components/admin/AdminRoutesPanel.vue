<script setup>
import { computed, onMounted, ref } from "vue";
import api from "../../api/api";

const routes = ref([]);
const busy = ref(true);
const listErr = ref("");
const q = ref("");

// 表單：editingId = null 代表新增，否則是編輯那一筆
const formOpen = ref(false);
const editingId = ref(null);
const saving = ref(false);
const formErr = ref("");
const okMsg = ref("");
const EMPTY = {
  slug: "", name: "", summary: "", description: "", location: "", country: "",
  days: 5, price: 30000, imageUrl: "", tags: "", featured: false,
};
const form = ref({ ...EMPTY });

const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);

const filtered = computed(() => {
  const k = q.value.trim().toLowerCase();
  if (!k) return routes.value;
  return routes.value.filter((r) =>
    [r.name, r.country, r.location, r.slug].some((f) => (f || "").toLowerCase().includes(k)));
});

async function load() {
  busy.value = true; listErr.value = "";
  try {
    routes.value = await api.getRoutes({ sort: "featured" });
  } catch (e) {
    listErr.value = e.message;
  } finally { busy.value = false; }
}

function openCreate() {
  editingId.value = null;
  form.value = { ...EMPTY };
  formErr.value = ""; okMsg.value = "";
  formOpen.value = true;
}

function openEdit(r) {
  editingId.value = r.id;
  form.value = {
    slug: r.slug, name: r.name, summary: r.summary, description: r.description,
    location: r.location, country: r.country, days: r.days, price: r.price,
    imageUrl: r.imageUrl, tags: r.tags || "", featured: !!r.featured,
  };
  formErr.value = ""; okMsg.value = "";
  formOpen.value = true;
}

function closeForm() {
  formOpen.value = false;
  formErr.value = "";
}

async function save() {
  formErr.value = ""; saving.value = true;
  const payload = {
    ...form.value,
    slug: form.value.slug.trim().toLowerCase(),
    days: Number(form.value.days),
    price: Number(form.value.price),
  };
  try {
    if (editingId.value == null) {
      await api.adminCreateRoute(payload);
      okMsg.value = `✓ 已上架「${payload.name}」`;
    } else {
      await api.adminUpdateRoute(editingId.value, payload);
      okMsg.value = `✓ 已更新「${payload.name}」`;
    }
    formOpen.value = false;
    await load();
  } catch (e) {
    formErr.value = e.message;
  } finally { saving.value = false; }
}

async function remove(r) {
  okMsg.value = "";
  if (!confirm(`確定要刪除行程「${r.name}」？此動作無法復原。\n（已有訂單的行程無法刪除）`)) return;
  try {
    await api.adminDeleteRoute(r.id);
    okMsg.value = `✓ 已刪除「${r.name}」`;
    if (editingId.value === r.id) closeForm();
    await load();
  } catch (e) {
    alert(e.message);
  }
}

onMounted(load);
</script>

<template>
  <div>
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h2 class="text-lg font-bold">行程管理</h2>
        <p class="text-sm text-ink-500">新增、修改或下架行程，前台會立即更新。</p>
      </div>
      <button v-if="!formOpen" @click="openCreate" class="btn-primary self-start">+ 新增行程</button>
    </div>

    <p v-if="okMsg" class="mt-4 rounded-lg bg-brand-50 px-3 py-2 text-sm text-brand-700">{{ okMsg }}</p>

    <!-- 新增 / 編輯表單 -->
    <form v-if="formOpen" @submit.prevent="save" class="card mt-4 grid gap-4 p-5 sm:grid-cols-2">
      <h3 class="text-base font-bold sm:col-span-2">{{ editingId == null ? "新增行程" : "編輯行程" }}</h3>

      <div><label class="label">行程名稱</label>
        <input v-model="form.name" required maxlength="120" class="input" placeholder="東京美食五日遊" /></div>
      <div><label class="label">網址代稱（小寫英文、數字、連字號）</label>
        <input v-model="form.slug" required maxlength="80" pattern="[a-z0-9]+(-[a-z0-9]+)*" class="input" placeholder="tokyo-food" /></div>

      <div class="sm:col-span-2"><label class="label">摘要（列表卡片上的一句話）</label>
        <input v-model="form.summary" required maxlength="255" class="input" /></div>
      <div class="sm:col-span-2"><label class="label">行程介紹</label>
        <textarea v-model="form.description" required rows="5" class="input"></textarea></div>

      <div><label class="label">地點</label>
        <input v-model="form.location" required maxlength="120" class="input" placeholder="東京 · 箱根" /></div>
      <div><label class="label">國家</label>
        <input v-model="form.country" required maxlength="60" class="input" placeholder="日本" /></div>

      <div><label class="label">天數</label>
        <input v-model="form.days" type="number" required min="1" max="60" class="input" /></div>
      <div><label class="label">每人價格（新台幣）</label>
        <input v-model="form.price" type="number" required min="0" step="1" class="input" /></div>

      <p class="text-xs text-ink-400 sm:col-span-2">評分與評價數由會員的真實評價自動計算，不能手動修改。</p>

      <div class="sm:col-span-2"><label class="label">封面圖片網址</label>
        <input v-model="form.imageUrl" type="url" required maxlength="500" class="input" placeholder="https://images.unsplash.com/..." />
        <img v-if="form.imageUrl" :src="form.imageUrl" alt="封面預覽" @error="(e)=>(e.target.style.display='none')" @load="(e)=>(e.target.style.display='')"
          class="mt-2 h-32 w-56 rounded-xl object-cover" />
      </div>

      <div><label class="label">標籤（用逗號分隔）</label>
        <input v-model="form.tags" maxlength="255" class="input" placeholder="自由行,美食,城市" /></div>
      <label class="flex items-center gap-2 self-end pb-2 text-sm font-medium">
        <input v-model="form.featured" type="checkbox" class="h-4 w-4" /> 首頁精選
      </label>

      <p v-if="formErr" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600 sm:col-span-2">{{ formErr }}</p>
      <div class="flex gap-2 sm:col-span-2">
        <button :disabled="saving" class="btn-primary">{{ saving ? "儲存中…" : (editingId == null ? "上架行程" : "儲存變更") }}</button>
        <button type="button" @click="closeForm" class="btn-outline">取消</button>
      </div>
    </form>

    <!-- 列表 -->
    <div class="card mt-4 p-5">
      <input v-model="q" placeholder="搜尋行程名稱 / 國家 / 地點…" class="input" />

      <p v-if="busy" class="mt-4 text-center text-ink-400">載入中…</p>
      <p v-else-if="listErr" class="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{{ listErr }}</p>
      <p v-else-if="filtered.length === 0" class="mt-4 text-center text-ink-400">沒有符合條件的行程</p>

      <div v-else class="mt-4 overflow-x-auto">
        <table class="w-full text-sm">
          <thead class="border-b border-ink-100 text-left text-xs text-ink-400">
            <tr>
              <th class="pb-2 pr-3">行程</th>
              <th class="pb-2 pr-3">國家</th>
              <th class="pb-2 pr-3">天數</th>
              <th class="pb-2 pr-3 text-right">價格</th>
              <th class="pb-2 pr-3">精選</th>
              <th class="pb-2 text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in filtered" :key="r.id" class="border-b border-ink-50 last:border-0">
              <td class="py-3 pr-3">
                <div class="flex items-center gap-3">
                  <img :src="r.imageUrl" alt="" @error="(e)=>(e.target.style.visibility='hidden')"
                    class="h-10 w-14 shrink-0 rounded-lg object-cover bg-ink-100" />
                  <div class="min-w-0">
                    <RouterLink :to="`/tours/${r.slug}`" class="font-medium hover:text-brand-700">{{ r.name }}</RouterLink>
                    <p class="text-xs text-ink-400">{{ r.slug }}</p>
                  </div>
                </div>
              </td>
              <td class="py-3 pr-3 text-ink-500">{{ r.country }}</td>
              <td class="py-3 pr-3 text-ink-500">{{ r.days }} 天</td>
              <td class="py-3 pr-3 text-right font-semibold">{{ twd(r.price) }}</td>
              <td class="py-3 pr-3">{{ r.featured ? "★" : "" }}</td>
              <td class="whitespace-nowrap py-3 text-right">
                <button @click="openEdit(r)" class="text-xs font-semibold text-brand-700 hover:underline">編輯</button>
                <button @click="remove(r)" class="ml-3 text-xs font-semibold text-rose-600 hover:underline">刪除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
