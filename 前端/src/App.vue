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
      <RouterView v-slot="{ Component, route: current }">
        <!-- 外面包一層 div：Transition 只能處理「單一根元素」。很多頁面有多個根節點（好幾個 section），
             直接放進 out-in 會讓離開動畫永遠等不到結束，切頁後畫面一片空白（例如首頁 → 探索行程）。 -->
        <Transition name="page" mode="out-in">
          <div :key="current.path">
            <component :is="Component" />
          </div>
        </Transition>
      </RouterView>
    </main>
    <AppFooter />
    <DemoBanner position="bottom" />
  </div>
</template>
