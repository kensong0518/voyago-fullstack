<script setup>
import { nextTick, onMounted, ref } from "vue";
import api from "../api/api";

const messages = ref([]);
const text = ref("");
const sending = ref(false);
const scroller = ref(null);

async function scrollToEnd() {
  await nextTick();
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight;
}

onMounted(async () => {
  messages.value = await api.getChat();
  scrollToEnd();
});

async function send() {
  if (!text.value.trim()) return;
  sending.value = true;
  try {
    messages.value = await api.sendChat(text.value);
    text.value = "";
    scrollToEnd();
  } finally {
    sending.value = false;
  }
}
</script>

<template>
  <div class="container-x py-12">
    <div class="mx-auto max-w-2xl">
      <h1 class="text-2xl font-extrabold">線上客服</h1>
      <p class="mt-1 text-sm text-ink-500">有任何行程問題，隨時與我們聊聊。</p>

      <div class="card mt-6 flex h-[60vh] flex-col overflow-hidden">
        <div class="flex items-center gap-3 border-b border-ink-100 bg-ink-50 px-5 py-3">
          <span class="grid h-9 w-9 place-items-center rounded-full bg-brand-600 text-sm font-bold text-white">A</span>
          <div>
            <p class="text-sm font-bold">歐洲自助遊 客服中心</p>
            <p class="flex items-center gap-1 text-xs text-brand-600"><span class="h-1.5 w-1.5 rounded-full bg-brand-500" />線上中</p>
          </div>
        </div>

        <div ref="scroller" class="flex-1 space-y-3 overflow-y-auto p-5">
          <div v-if="messages.length === 0" class="grid h-full place-items-center text-center text-ink-400">
            <div><p class="text-4xl">💬</p><p class="mt-3 text-sm">傳送第一則訊息開始對話吧！</p></div>
          </div>
          <div v-for="m in messages" :key="m.id" :class="['flex', m.sender === 'MEMBER' ? 'justify-end' : 'justify-start']">
            <div :class="['max-w-[75%] rounded-2xl px-4 py-2.5 text-sm', m.sender === 'MEMBER' ? 'rounded-br-md bg-brand-600 text-white' : 'rounded-bl-md bg-ink-100 text-ink-800']">
              {{ m.content }}
            </div>
          </div>
        </div>

        <form @submit.prevent="send" class="flex gap-2 border-t border-ink-100 p-3">
          <input v-model="text" placeholder="輸入訊息…" class="input flex-1" />
          <button :disabled="sending || !text.trim()" class="btn-primary px-5">{{ sending ? "…" : "傳送" }}</button>
        </form>
      </div>
    </div>
  </div>
</template>
