<script setup>
import { useRoute } from "vue-router";
import { computed } from "vue";
import { useCompareStore, COMPARE_MAX } from "../stores/compare";

const compare = useCompareStore();
const route = useRoute();
// 比較頁本身、後台不顯示浮動列
const visible = computed(() => (compare.count > 0 || compare.notice) && !["compare", "admin", "checkout"].includes(route.name));
</script>

<template>
  <Transition name="fade">
    <div v-if="visible" class="pointer-events-none fixed inset-x-0 bottom-4 z-40 flex justify-center px-4"
      style="padding-bottom: env(safe-area-inset-bottom)">
      <div class="pointer-events-auto flex max-w-full items-center gap-3 rounded-md bg-navy py-2 pl-3 pr-2 text-white shadow-lg"
        role="region" aria-label="行程比較">
        <p v-if="compare.notice" class="px-2 text-sm">{{ compare.notice }}</p>
        <template v-else>
          <div class="flex -space-x-2">
            <img v-for="it in compare.items" :key="it.slug" :src="it.imageUrl" :alt="it.name"
              @error="(e) => (e.target.style.visibility = 'hidden')"
              class="h-8 w-8 rounded-full object-cover ring-2 ring-navy bg-sea-deep" />
          </div>
          <span class="whitespace-nowrap text-sm">比較 {{ compare.count }} / {{ COMPARE_MAX }}</span>
          <button type="button" @click="compare.clear()" class="whitespace-nowrap rounded-md px-2 py-1 text-xs text-white/70 hover:text-white">清除</button>
          <RouterLink to="/compare"
            :class="['btn whitespace-nowrap px-4 py-1.5 text-sm', compare.count >= 2 ? 'bg-brand-600 text-white hover:bg-brand-700' : 'bg-white/10 text-white/70']">
            {{ compare.count >= 2 ? "開始比較" : "再選一個" }}
          </RouterLink>
        </template>
      </div>
    </div>
  </Transition>
</template>
