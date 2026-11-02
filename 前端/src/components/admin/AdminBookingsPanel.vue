<script setup>
import { onMounted, ref } from "vue";
import api from "../../api/api";

const emit = defineEmits(["changed"]);

const FILTERS = [
  { v: "", l: "全部" },
  { v: "PENDING", l: "待付款" },
  { v: "CONFIRMED", l: "已付款" },
  { v: "CANCELLED", l: "已取消" },
];
const STATUS = {
  CONFIRMED: { label: "已付款", cls: "bg-brand-50 text-brand-700" },
  PENDING: { label: "待付款", cls: "bg-amber-50 text-amber-700" },
  CANCELLED: { label: "已取消", cls: "bg-ink-100 text-ink-500" },
};

const status = ref("");
const page = ref(0);
const size = 10;
const list = ref([]);
const total = ref(0);
const totalPages = ref(0);
const busy = ref(false);
const err = ref("");
const savingId = ref(null);

const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);
const dateText = (d) => (d ? new Date(d).toLocaleDateString("zh-TW") : "");

async function load() {
  busy.value = true; err.value = "";
  try {
    const data = await api.adminListBookings({ status: status.value, page: page.value, size });
    list.value = data.content || [];
    total.value = data.totalElements ?? list.value.length;
    totalPages.value = data.totalPages ?? 1;
  } catch (e) {
    err.value = e.message;
  } finally { busy.value = false; }
}

function setFilter(v) {
  status.value = v;
  page.value = 0;
  load();
}

function changePage(delta) {
  const next = page.value + delta;
  if (next < 0 || next >= totalPages.value) return;
  page.value = next;
  load();
}

async function changeStatus(b, selectEl) {
  const next = selectEl.value;
  if (next === b.status) return;
  // 綁定值沒變時 Vue 不會重寫 DOM，所以取消或失敗時要手動把下拉選單還原
  const revert = () => { selectEl.value = b.status; };
  const label = STATUS[next].label;
  if (!confirm(`把訂單 #${b.id}（${b.memberName}・${b.routeName}）改為「${label}」？`)) {
    revert();
    return;
  }
  savingId.value = b.id;
  try {
    const updated = await api.adminUpdateBookingStatus(b.id, next);
    Object.assign(b, updated);
    emit("changed");
  } catch (e) {
    alert(e.message);
    revert();
  } finally { savingId.value = null; }
}

onMounted(load);
</script>

<template>
  <div>
    <h2 class="text-lg font-bold">訂單管理</h2>
    <p class="text-sm text-ink-500">所有會員的訂單，可直接調整付款狀態。</p>

    <div class="mt-4 flex flex-wrap gap-2">
      <button v-for="f in FILTERS" :key="f.v" @click="setFilter(f.v)"
        :class="['rounded-full px-4 py-1.5 text-sm font-medium transition', status === f.v ? 'bg-brand-600 text-white' : 'bg-white text-ink-600 ring-1 ring-ink-200 hover:bg-ink-50']">
        {{ f.l }}
      </button>
    </div>

    <div class="card mt-4 p-5">
      <p v-if="busy" class="text-center text-ink-400">載入中…</p>
      <p v-else-if="err" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{{ err }}</p>
      <p v-else-if="list.length === 0" class="text-center text-ink-400">目前沒有訂單</p>

      <div v-else class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead class="border-b border-ink-100 text-left text-xs text-ink-400">
            <tr>
              <th class="pb-2 pr-3">#</th>
              <th class="pb-2 pr-3">行程</th>
              <th class="pb-2 pr-3">下訂人</th>
              <th class="pb-2 pr-3">出發 / 人數</th>
              <th class="pb-2 pr-3 text-right">金額</th>
              <th class="pb-2 pr-3">狀態</th>
              <th class="pb-2">下訂時間</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in list" :key="b.id" class="border-b border-ink-50 last:border-0">
              <td class="py-3 pr-3 text-ink-400">{{ b.id }}</td>
              <td class="py-3 pr-3">
                <RouterLink :to="`/tours/${b.routeSlug}`" class="font-medium hover:text-brand-700">{{ b.routeName }}</RouterLink>
              </td>
              <td class="py-3 pr-3">
                <p class="font-medium">{{ b.memberName }}</p>
                <p class="text-xs text-ink-400">{{ b.memberEmail }}</p>
              </td>
              <td class="whitespace-nowrap py-3 pr-3 text-ink-500">{{ dateText(b.travelDate) }} · {{ b.people }} 人</td>
              <td class="whitespace-nowrap py-3 pr-3 text-right font-semibold">{{ twd(b.totalPrice) }}
                <p v-if="b.discountAmount > 0" class="text-xs font-normal text-ink-400">{{ b.couponCode }} −{{ twd(b.discountAmount) }}</p>
              </td>
              <td class="py-3 pr-3">
                <select :value="b.status" :disabled="savingId === b.id"
                  @change="(e) => changeStatus(b, e.target)"
                  :class="['chip cursor-pointer border-0 pr-6 text-xs', (STATUS[b.status] || STATUS.PENDING).cls]">
                  <option value="PENDING">待付款</option>
                  <option value="CONFIRMED">已付款</option>
                  <option value="CANCELLED">已取消</option>
                </select>
              </td>
              <td class="whitespace-nowrap py-3 text-ink-500">{{ dateText(b.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="totalPages > 1" class="mt-4 flex items-center justify-between text-sm">
        <span class="text-ink-500">共 {{ total }} 筆，第 {{ page + 1 }} / {{ totalPages }} 頁</span>
        <div class="flex gap-2">
          <button @click="changePage(-1)" :disabled="page === 0" class="btn-outline px-3 py-1 disabled:opacity-50">←</button>
          <button @click="changePage(1)" :disabled="page >= totalPages - 1" class="btn-outline px-3 py-1 disabled:opacity-50">→</button>
        </div>
      </div>
    </div>
  </div>
</template>
