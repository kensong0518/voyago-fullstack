<script setup>
import { onMounted, ref } from "vue";
import api from "../../api/api";

const list = ref([]);
const loading = ref(false);
const err = ref("");
const msg = ref("");
const formOpen = ref(false);
const editingId = ref(null);
const saving = ref(false);
const formErr = ref("");
const EMPTY = { code: "", description: "", discountType: "PERCENT", discountValue: 10, minAmount: 0, maxUses: "", expiresAt: "", active: true };
const form = ref({ ...EMPTY });

const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);
const percentText = (c) => (c.discountType === "PERCENT" ? `打 ${(100 - c.discountValue) / 10} 折` : `折 ${twd(c.discountValue)}`);
const expired = (c) => c.expiresAt && new Date(c.expiresAt) < new Date();

async function load() {
  loading.value = true; err.value = "";
  try { list.value = await api.adminListCoupons(); }
  catch (e) { err.value = e.message; }
  finally { loading.value = false; }
}
onMounted(load);

function openCreate() { editingId.value = null; form.value = { ...EMPTY }; formErr.value = ""; msg.value = ""; formOpen.value = true; }
function openEdit(c) {
  editingId.value = c.id;
  form.value = { code: c.code, description: c.description, discountType: c.discountType, discountValue: c.discountValue,
    minAmount: c.minAmount, maxUses: c.maxUses ?? "", expiresAt: c.expiresAt ? c.expiresAt.slice(0, 16) : "", active: c.active };
  formErr.value = ""; msg.value = ""; formOpen.value = true;
}

function payloadOf(f) {
  return {
    code: f.code.trim(), description: f.description.trim(), discountType: f.discountType,
    discountValue: Number(f.discountValue), minAmount: Number(f.minAmount || 0),
    maxUses: f.maxUses === "" || f.maxUses == null ? null : Number(f.maxUses),
    expiresAt: f.expiresAt ? f.expiresAt + (f.expiresAt.length === 16 ? ":00" : "") : null,
    active: !!f.active,
  };
}

async function save() {
  formErr.value = ""; saving.value = true;
  try {
    const p = payloadOf(form.value);
    if (editingId.value == null) { await api.adminCreateCoupon(p); msg.value = `✓ 已新增優惠碼 ${p.code.toUpperCase()}`; }
    else { await api.adminUpdateCoupon(editingId.value, p); msg.value = `✓ 已更新 ${p.code.toUpperCase()}`; }
    formOpen.value = false;
    await load();
  } catch (e) { formErr.value = e.message; }
  finally { saving.value = false; }
}

async function toggle(c) {
  try {
    await api.adminUpdateCoupon(c.id, payloadOf({ ...c, maxUses: c.maxUses ?? "", expiresAt: c.expiresAt ? c.expiresAt.slice(0, 16) : "", active: !c.active }));
    msg.value = `✓ ${c.code} 已${c.active ? "停用" : "啟用"}`;
    await load();
  } catch (e) { msg.value = e.message; }
}

async function remove(c) {
  if (!window.confirm(`確定刪除優惠碼 ${c.code}？`)) return;
  try { await api.adminDeleteCoupon(c.id); msg.value = `✓ 已刪除 ${c.code}`; await load(); }
  catch (e) { msg.value = e.message; }
}
</script>

<template>
  <div>
    <div class="flex flex-wrap items-center justify-between gap-3">
      <p class="text-sm text-ink-500">會員在結帳頁輸入；每位會員每個碼限用一次，次數由資料庫原子更新，不會超賣。</p>
      <button type="button" @click="openCreate" class="btn-primary px-4 py-2 text-sm">＋ 新增優惠碼</button>
    </div>
    <p v-if="msg" class="mt-3 text-sm text-ink-600" role="status">{{ msg }}</p>
    <p v-if="err" class="mt-3 text-sm text-red-600">{{ err }}</p>

    <form v-if="formOpen" @submit.prevent="save" class="card mt-4 grid gap-4 p-5 sm:grid-cols-2">
      <h3 class="font-bold sm:col-span-2">{{ editingId == null ? "新增優惠碼" : "編輯優惠碼" }}</h3>
      <div><label class="label" for="cp-code">代碼</label>
        <input id="cp-code" v-model="form.code" required maxlength="30" pattern="[A-Za-z0-9_\-]{3,30}" class="input font-mono uppercase" placeholder="SUMMER25" /></div>
      <div><label class="label" for="cp-desc">說明（給管理員看）</label>
        <input id="cp-desc" v-model="form.description" maxlength="100" class="input" placeholder="夏季活動 85 折" /></div>
      <div><label class="label" for="cp-type">折扣方式</label>
        <select id="cp-type" v-model="form.discountType" class="input">
          <option value="PERCENT">百分比（例：15 = 打 85 折）</option>
          <option value="AMOUNT">固定金額（新台幣）</option>
        </select></div>
      <div><label class="label" for="cp-val">{{ form.discountType === 'PERCENT' ? '折扣 %（1–90）' : '折抵金額' }}</label>
        <input id="cp-val" v-model="form.discountValue" type="number" required min="1" :max="form.discountType === 'PERCENT' ? 90 : 1000000" class="input" /></div>
      <div><label class="label" for="cp-min">最低消費（0 = 不限）</label>
        <input id="cp-min" v-model="form.minAmount" type="number" min="0" class="input" /></div>
      <div><label class="label" for="cp-max">使用上限（留空 = 不限）</label>
        <input id="cp-max" v-model="form.maxUses" type="number" min="1" class="input" /></div>
      <div><label class="label" for="cp-exp">到期時間（留空 = 不過期）</label>
        <input id="cp-exp" v-model="form.expiresAt" type="datetime-local" class="input" /></div>
      <label class="flex items-center gap-2 self-end pb-2 text-sm font-medium"><input type="checkbox" v-model="form.active" class="h-4 w-4 accent-brand-600" /> 啟用</label>
      <p v-if="formErr" class="text-sm text-red-600 sm:col-span-2" role="alert">{{ formErr }}</p>
      <div class="flex gap-2 sm:col-span-2">
        <button :disabled="saving" class="btn-primary px-5 py-2 text-sm disabled:opacity-60">{{ saving ? "儲存中…" : "儲存" }}</button>
        <button type="button" @click="formOpen = false" class="btn-outline px-5 py-2 text-sm">取消</button>
      </div>
    </form>

    <div v-if="loading && !list.length" class="mt-4 h-32 animate-pulse rounded-2xl bg-ink-100" />
    <div v-else class="mt-4 overflow-x-auto">
      <table class="w-full min-w-[640px] text-sm">
        <thead class="text-left text-xs text-ink-400">
          <tr><th class="py-2 pr-3">代碼</th><th class="py-2 pr-3">折扣</th><th class="py-2 pr-3">條件</th><th class="py-2 pr-3">已用</th><th class="py-2 pr-3">狀態</th><th class="py-2" /></tr>
        </thead>
        <tbody>
          <tr v-for="c in list" :key="c.id" class="border-t border-ink-100 align-top">
            <td class="py-3 pr-3"><p class="font-mono font-bold">{{ c.code }}</p><p class="text-xs text-ink-400">{{ c.description }}</p></td>
            <td class="py-3 pr-3 whitespace-nowrap">{{ percentText(c) }}</td>
            <td class="py-3 pr-3 text-xs text-ink-500">
              <p>{{ c.minAmount > 0 ? `滿 ${twd(c.minAmount)}` : '不限金額' }}</p>
              <p v-if="c.expiresAt">到 {{ c.expiresAt.slice(0, 16).replace('T', ' ') }}</p>
            </td>
            <td class="py-3 pr-3 whitespace-nowrap">{{ c.usedCount }}<span class="text-ink-400"> / {{ c.maxUses ?? '∞' }}</span></td>
            <td class="py-3 pr-3 whitespace-nowrap">
              <span v-if="!c.active" class="chip bg-ink-100 text-ink-500">停用</span>
              <span v-else-if="expired(c)" class="chip bg-amber-50 text-amber-700">已過期</span>
              <span v-else-if="c.maxUses != null && c.usedCount >= c.maxUses" class="chip bg-amber-50 text-amber-700">已用完</span>
              <span v-else class="chip bg-brand-50 text-brand-700">可使用</span>
            </td>
            <td class="py-3 whitespace-nowrap text-right">
              <button type="button" @click="openEdit(c)" class="text-xs font-semibold text-brand-700 hover:underline">編輯</button>
              <button type="button" @click="toggle(c)" class="ml-3 text-xs font-semibold text-ink-500 hover:underline">{{ c.active ? '停用' : '啟用' }}</button>
              <button v-if="c.usedCount === 0" type="button" @click="remove(c)" class="ml-3 text-xs font-semibold text-rose-600 hover:underline">刪除</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="6" class="py-8 text-center text-ink-400">還沒有優惠碼</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
