<script setup>
import { onActivated, ref } from "vue";
import api from "../../api/api";

const ACTIONS = {
  ROUTE_CREATE: "新增行程", ROUTE_UPDATE: "修改行程", ROUTE_DELETE: "刪除行程",
  BOOKING_STATUS: "變更訂單狀態", MEMBER_CREATE: "新增會員", MEMBER_DELETE: "刪除會員",
  COUPON_CREATE: "新增優惠碼", COUPON_UPDATE: "修改優惠碼", COUPON_DELETE: "刪除優惠碼",
  EXPORT_BOOKINGS: "匯出訂單 CSV", EXPORT_MEMBERS: "匯出會員 CSV",
  LOGIN_SUCCESS: "登入成功", LOGIN_FAILED: "登入失敗", LOGIN_LOCKED: "帳號鎖定", LOGIN_BLOCKED: "鎖定中仍嘗試登入",
  LOGOUT_ALL: "登出所有裝置", ACCESS_DENIED: "嘗試存取無權限的功能", ACCOUNT_RECLAIMED: "帳號由 Email 擁有者收回",
};
// 需要留意的資安事件用紅色標出
const ALERT = new Set(["LOGIN_FAILED", "LOGIN_LOCKED", "LOGIN_BLOCKED", "ACCESS_DENIED", "ACCOUNT_RECLAIMED"]);
const CATEGORIES = [{ v: "", l: "全部" }, { v: "SECURITY", l: "🔒 資安事件" }, { v: "ADMIN", l: "後台操作" }];
const category = ref("");
function setCategory(c) { category.value = c; load(0); }
const page = ref(0);
const data = ref(null);
const loading = ref(false);
const err = ref("");
const ts = (t) => new Date(t).toLocaleString("zh-TW", { hour12: false });

async function load(p = page.value) {
  loading.value = true; err.value = "";
  try { data.value = await api.adminAuditLog({ page: p, size: 20, category: category.value }); page.value = p; }
  catch (e) { err.value = e.message; }
  finally { loading.value = false; }
}
// 放在 KeepAlive 裡：每次切回這個分頁都重新抓，才看得到剛剛的操作
onActivated(() => load(0));
defineExpose({ reload: () => load(0) });
</script>

<template>
  <div>
    <div class="flex items-center justify-between gap-3">
      <p class="text-sm text-ink-500">後台操作與登入、權限相關的資安事件都會留下紀錄，只能查看、不能修改。</p>
      <button type="button" @click="load(0)" class="btn-outline px-4 py-2 text-sm">重新整理</button>
    </div>
    <div class="mt-3 inline-flex rounded-full bg-ink-50 p-1" role="group" aria-label="紀錄類別">
      <button v-for="c in CATEGORIES" :key="c.v" type="button" @click="setCategory(c.v)" :aria-pressed="category === c.v"
        :class="['rounded-full px-4 py-1.5 text-sm font-semibold transition', category === c.v ? 'bg-white text-ink-900 shadow' : 'text-ink-500 hover:text-ink-800']">
        {{ c.l }}
      </button>
    </div>
    <p v-if="err" class="mt-3 text-sm text-red-600">{{ err }}</p>
    <div v-if="loading && !data" class="mt-4 h-32 animate-pulse rounded-2xl bg-ink-100" />
    <ul v-else-if="data" class="mt-4 divide-y divide-ink-100">
      <li v-for="a in data.content" :key="a.id" class="flex flex-col gap-1 py-3 text-sm sm:flex-row sm:items-start sm:gap-4">
        <span class="w-40 shrink-0 text-xs text-ink-400">{{ ts(a.createdAt) }}</span>
        <div class="min-w-0 flex-1">
          <p><span :class="['font-semibold', ALERT.has(a.action) ? 'text-rose-600' : '']">{{ ACTIONS[a.action] || a.action }}</span>
            <span v-if="a.targetId && a.targetType !== 'SECURITY'" class="ml-1 text-xs text-ink-400">#{{ a.targetId }}</span></p>
          <p class="break-words text-ink-600">{{ a.detail }}</p>
        </div>
        <span class="shrink-0 truncate text-xs text-ink-500 sm:max-w-[14rem]">{{ a.actorEmail }}</span>
      </li>
      <li v-if="!data.content.length" class="py-8 text-center text-ink-400">還沒有任何操作紀錄</li>
    </ul>
    <div v-if="data && data.totalPages > 1" class="mt-4 flex items-center justify-center gap-3 text-sm">
      <button type="button" :disabled="page === 0" @click="load(page - 1)" class="btn-outline px-3 py-1.5 disabled:opacity-40">上一頁</button>
      <span class="text-ink-500">{{ page + 1 }} / {{ data.totalPages }}</span>
      <button type="button" :disabled="page + 1 >= data.totalPages" @click="load(page + 1)" class="btn-outline px-3 py-1.5 disabled:opacity-40">下一頁</button>
    </div>
  </div>
</template>
