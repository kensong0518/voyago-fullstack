<script setup>
import BrandLogo from "./BrandLogo.vue";
</script>

<template>
  <footer class="border-t border-sea-line bg-white">
    <div class="container-x grid gap-8 py-12 sm:grid-cols-2 lg:grid-cols-4">
      <div>
        <BrandLogo size="sm" />
        <p class="mt-3 text-sm text-ink-500">歐洲自助行程：交通票券與住宿先訂好，行程每天留白。</p>
      </div>
      <div>
        <h4 class="text-sm font-semibold text-ink-800">探索</h4>
        <ul class="mt-3 space-y-2 text-sm text-ink-500">
          <li><RouterLink to="/" class="hover:text-brand-700">路線地圖</RouterLink></li>
          <li><RouterLink to="/tours" class="hover:text-brand-700">全部路線</RouterLink></li>
          <li><RouterLink to="/compare" class="hover:text-brand-700">行程比較</RouterLink></li>
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
      © {{ new Date().getFullYear() }} Voyago 歐洲自助遊。本站為作品展示，不是真實的旅遊服務。
    </div>
  </footer>
</template>
