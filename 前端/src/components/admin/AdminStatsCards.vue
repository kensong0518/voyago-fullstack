<script setup>
import { onMounted, ref } from "vue";
import api from "../../api/api";

const adminStats = ref(null);
const statsErr = ref("");

const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);

async function loadStats() {
  statsErr.value = "";
  try {
    adminStats.value = await api.getAdminStats();
  } catch (e) {
    statsErr.value = e.message;
  }
}

onMounted(loadStats);
defineExpose({ reload: loadStats });
</script>

<template>
  <div>
    <!-- 儀表板統計 -->
    <p v-if="statsErr" class="card mt-4 p-4 text-sm text-red-600">{{ statsErr }}</p>
    <div v-else-if="adminStats" class="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
      <div class="card p-4">
        <p class="text-xs text-ink-400">總會員數</p>
        <p class="mt-1 text-2xl font-extrabold">{{ adminStats.totalMembers }}</p>
        <p class="mt-0.5 text-xs text-brand-600">本月新增 {{ adminStats.newMembersThisMonth }}</p>
      </div>
      <div class="card p-4">
        <p class="text-xs text-ink-400">待付款訂單</p>
        <p class="mt-1 text-2xl font-extrabold text-amber-600">{{ adminStats.pendingBookings }}</p>
      </div>
      <div class="card p-4">
        <p class="text-xs text-ink-400">已付款訂單</p>
        <p class="mt-1 text-2xl font-extrabold text-brand-700">{{ adminStats.confirmedBookings }}</p>
        <p class="mt-0.5 text-xs text-ink-400">已取消 {{ adminStats.cancelledBookings }}</p>
      </div>
      <div class="card p-4">
        <p class="text-xs text-ink-400">總營收（已付款）</p>
        <p class="mt-1 text-2xl font-extrabold text-brand-700">{{ twd(adminStats.totalRevenue) }}</p>
      </div>
    </div>
    <p v-else class="mt-4 text-sm text-ink-400">統計載入中…</p>
  </div>
</template>
