<script setup>
import { ref } from "vue";
import { useAuthStore } from "../stores/auth";
import AdminStatsCards from "../components/admin/AdminStatsCards.vue";
import AdminRoutesPanel from "../components/admin/AdminRoutesPanel.vue";
import AdminBookingsPanel from "../components/admin/AdminBookingsPanel.vue";
import AdminMembersPanel from "../components/admin/AdminMembersPanel.vue";
import AdminInsightsPanel from "../components/admin/AdminInsightsPanel.vue";
import AdminCouponsPanel from "../components/admin/AdminCouponsPanel.vue";
import AdminAuditPanel from "../components/admin/AdminAuditPanel.vue";

const auth = useAuthStore();

const TABS = [
  { v: "insights", l: "📈 營收報表" },
  { v: "routes", l: "🗺️ 行程管理" },
  { v: "bookings", l: "🧾 訂單管理" },
  { v: "members", l: "👥 會員管理" },
  { v: "coupons", l: "🏷️ 優惠碼" },
  { v: "audit", l: "📝 操作紀錄" },
];
const tab = ref("insights");
const stats = ref(null);
</script>

<template>
  <div class="container-x py-12">
    <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <p class="text-sm font-semibold text-brand-700">Voyago 後台</p>
        <h1 class="text-3xl font-extrabold">管理中心</h1>
        <p class="mt-1 text-ink-500">{{ auth.user?.name }}（{{ auth.user?.email }}）</p>
      </div>
      <RouterLink to="/" class="btn-outline self-start">← 回到前台</RouterLink>
    </div>

    <!-- 儀表板：訂單狀態改了之後重新抓，數字跟著變 -->
    <AdminStatsCards ref="stats" />

    <nav class="mt-8 flex gap-2 overflow-x-auto border-b border-ink-100">
      <button v-for="t in TABS" :key="t.v" @click="tab = t.v"
        :class="['-mb-px whitespace-nowrap border-b-2 px-4 py-2.5 text-sm font-semibold transition',
                 tab === t.v ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-500 hover:text-ink-800']">
        {{ t.l }}
      </button>
    </nav>

    <div class="mt-6">
      <!-- KeepAlive：切換分頁不會丟掉已載入的列表與表單內容 -->
      <KeepAlive>
        <AdminInsightsPanel v-if="tab === 'insights'" />
        <AdminRoutesPanel v-else-if="tab === 'routes'" />
        <AdminBookingsPanel v-else-if="tab === 'bookings'" @changed="stats?.reload()" />
        <AdminMembersPanel v-else-if="tab === 'members'" />
        <AdminCouponsPanel v-else-if="tab === 'coupons'" />
        <AdminAuditPanel v-else-if="tab === 'audit'" />
      </KeepAlive>
    </div>
  </div>
</template>
