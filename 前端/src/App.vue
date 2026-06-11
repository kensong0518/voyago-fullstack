<script setup>
import { onMounted, watch } from "vue";
import { useAuthStore } from "./stores/auth";
import { useFavoritesStore } from "./stores/favorites";
import DemoBanner from "./components/DemoBanner.vue";
import NavBar from "./components/NavBar.vue";
import AppFooter from "./components/AppFooter.vue";

const auth = useAuthStore();
const fav = useFavoritesStore();
onMounted(() => auth.fetchMe());

// 換帳號（含登入/登出）時，切換到該帳號自己的收藏清單
watch(() => auth.user?.email, (email) => fav.bindUser(email), { immediate: true });
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <DemoBanner position="top" />
    <NavBar />
    <main class="flex-1">
      <RouterView />
    </main>
    <AppFooter />
    <DemoBanner position="bottom" />
  </div>
</template>
