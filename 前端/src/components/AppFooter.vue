<script setup></script>

<template>
  <footer class="border-t border-ink-100 bg-ink-50">
    <div class="container-x grid gap-8 py-12 sm:grid-cols-2 lg:grid-cols-4">
      <div>
        <div class="flex items-center gap-2 text-lg font-extrabold">
          <span class="grid h-8 w-8 place-items-center rounded-xl bg-brand-600 text-white">歐</span>
          歐洲自助遊
        </div>
        <p class="mt-3 text-sm text-ink-500">用心策劃每一段深度旅程，讓世界成為你的日常。</p>
      </div>
      <div>
        <h4 class="text-sm font-semibold text-ink-800">探索</h4>
        <ul class="mt-3 space-y-2 text-sm text-ink-500">
          <li><RouterLink to="/tours" class="hover:text-brand-700">所有行程</RouterLink></li>
          <li><RouterLink to="/tours" class="hover:text-brand-700">蜜月推薦</RouterLink></li>
          <li><RouterLink to="/tours" class="hover:text-brand-700">親子旅遊</RouterLink></li>
        </ul>
      </div>
      <div>
        <h4 class="text-sm font-semibold text-ink-800">會員</h4>
        <ul class="mt-3 space-y-2 text-sm text-ink-500">
          <li><RouterLink to="/login" class="hover:text-brand-700">登入</RouterLink></li>
          <li><RouterLink to="/register" class="hover:text-brand-700">註冊</RouterLink></li>
          <li><RouterLink to="/dashboard" class="hover:text-brand-700">我的訂單</RouterLink></li>
        </ul>
      </div>
      <div>
        <h4 class="text-sm font-semibold text-ink-800">關於</h4>
        <p class="mt-3 text-sm text-ink-500">本網站僅為展示用途，所有行程與訂單皆為範例，無法實際預訂或付款。</p>
      </div>
    </div>
    <div class="border-t border-ink-100 py-5 text-center text-xs text-ink-400">
      © {{ new Date().getFullYear() }} 歐洲自助遊 · 本站為展示網站，非真實旅遊服務。
    </div>
  </footer>
</template>
