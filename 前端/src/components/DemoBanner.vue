<script setup>
import { ref } from "vue";

// position：top（頁面最上方，可本次 session 關閉）｜ bottom（頁面最下方，永遠顯示）
const props = defineProps({
  position: { type: String, default: "top" },
});

const DEMO_ONLY = import.meta.env.VITE_DEMO_ONLY === "true";
const isTop = props.position === "top";

// 頂部橫幅允許使用者本次 session 內隱藏（重新整理會再出現）；底部聲明永遠顯示
const hidden = ref(isTop && sessionStorage.getItem("voyago_banner_hidden") === "1");
function close() {
  hidden.value = true;
  sessionStorage.setItem("voyago_banner_hidden", "1");
}
</script>

<template>
  <div v-if="!hidden"
    :class="[
      'bg-amber-50 text-amber-900 ring-1 ring-amber-200',
      isTop ? 'relative z-[60]' : 'border-t border-amber-200',
    ]"
    role="status" aria-label="網站性質聲明">
    <div class="container-x flex items-center gap-3 py-2 text-sm">
      <span class="text-base">⚠️</span>
      <p class="flex-1">
        <strong class="font-bold">本站為展示網站，非真實旅遊服務。</strong>
        <span v-if="DEMO_ONLY" class="text-amber-700">
          所有資料僅暫存於你的瀏覽器，重新整理或換裝置即會還原；不會送往任何真實後端或第三方。
        </span>
        <span v-else class="text-amber-700">
          不要輸入真實的密碼、信用卡或個資。
        </span>
      </p>
      <button v-if="isTop" @click="close"
        class="rounded-full p-1 text-amber-700 hover:bg-amber-100"
        aria-label="關閉聲明">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </div>
  </div>
</template>
