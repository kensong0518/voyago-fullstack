<script setup>
import { onMounted } from "vue";
import { useAuthStore } from "./stores/auth";
import DemoBanner from "./components/DemoBanner.vue";
import NavBar from "./components/NavBar.vue";
import AppFooter from "./components/AppFooter.vue";

const auth = useAuthStore();
onMounted(() => auth.fetchMe());
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
