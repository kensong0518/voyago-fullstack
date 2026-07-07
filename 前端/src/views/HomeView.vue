<script setup>
import { computed, onMounted, ref } from "vue";
import api from "../api/api";
import RouteCard from "../components/RouteCard.vue";
import { pickReviews, avatarColor } from "../data/content";

const all = ref([]);
const featured = computed(() => all.value.filter((r) => r.featured).slice(0, 3));
const popular = computed(() => all.value.filter((r) => !r.featured).slice(0, 6));

// 熱門目的地：用各國第一個行程的圖
const destinations = computed(() => {
  const seen = new Map();
  for (const r of all.value) if (!seen.has(r.country)) seen.set(r.country, r);
  return [...seen.values()].slice(0, 6);
});

const testimonials = pickReviews(1, 3);

const stats = [
  { value: "20+", label: "歐洲國家" },
  { value: "10", label: "精選路線" },
  { value: "8萬", label: "旅人同行" },
  { value: "4.8★", label: "平均評價" },
];
const steps = [
  { icon: "🔍", title: "先挑路線", desc: "用主題、天數、預算篩，找到對你胃口的那條，而不是被推一堆。" },
  { icon: "🗓️", title: "線上下訂", desc: "選好出發日和人數，當下就看到有沒有位，不用等回覆。" },
  { icon: "💬", title: "有人接手", desc: "行前問簽證、旅途中臨時想改，找到的都是真人。" },
];
const whyUs = [
  { icon: "✦", title: "路線是走過的", desc: "每條都是編輯自己去過、實際訂過住宿才放上來，不是搬型錄。" },
  { icon: "🧭", title: "不塞好塞滿", desc: "每天留白給你自己亂逛，我們只把交通和門票這種麻煩事處理掉。" },
  { icon: "🤝", title: "回你的是真人", desc: "從行前到旅途中都找得到人，不是罐頭訊息打發你。" },
  { icon: "💎", title: "價格不藏東西", desc: "含什麼、不含什麼列在明處，機票簽證保險不會結帳才冒出來。" },
];

const email = ref("");
const subscribed = ref(false);
function subscribe() {
  if (!email.value.includes("@")) return;
  subscribed.value = true;
}

onMounted(async () => { all.value = await api.getRoutes({ sort: "featured" }); });
</script>

<template>
  <!-- Hero -->
  <section class="relative overflow-hidden bg-ink-950 text-white">
    <img src="https://images.unsplash.com/photo-1499856871958-5b9627545d1a?w=1600&q=80" alt="" fetchpriority="high"
      @error="(e)=>(e.target.style.display='none')"
      class="absolute inset-0 h-full w-full object-cover opacity-40" />
    <div class="absolute inset-0 bg-gradient-to-br from-ink-950/90 via-ink-950/60 to-brand-950/70" />
    <div class="container-x relative py-24 sm:py-32">
      <div class="max-w-2xl animate-fade-up">
        <span class="chip bg-white/15 text-white">✦ 2026 全新歐洲行程上線</span>
        <h1 class="mt-5 text-4xl font-extrabold leading-tight sm:text-5xl lg:text-6xl">探索歐洲的<br /><span class="text-brand-400">下一段自助旅行</span></h1>
        <p class="mt-5 max-w-xl text-lg text-ink-200">從巴黎鐵塔到挪威峽灣，十條走過的歐洲路線任你挑。交通、住宿、門票先幫你搞定，剩下的時間留給你自己玩。</p>
        <div class="mt-8 flex flex-wrap gap-3">
          <RouterLink to="/tours" class="btn-primary px-7 py-3 text-base">開始探索 →</RouterLink>
          <RouterLink to="/register" class="btn px-7 py-3 text-base bg-white/10 text-white hover:bg-white/20">免費加入會員</RouterLink>
        </div>
      </div>
      <dl class="mt-16 grid max-w-2xl grid-cols-2 gap-6 sm:grid-cols-4">
        <div v-for="s in stats" :key="s.label"><dt class="text-3xl font-extrabold text-brand-400">{{ s.value }}</dt><dd class="mt-1 text-sm text-ink-300">{{ s.label }}</dd></div>
      </dl>
    </div>
  </section>

  <!-- 熱門目的地 -->
  <section class="container-x py-16 sm:py-20">
    <h2 class="text-2xl font-extrabold sm:text-3xl">熱門目的地</h2>
    <p class="mt-2 text-ink-500">挑一個國家，開始你的歐洲冒險。</p>
    <div class="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
      <RouterLink v-for="d in destinations" :key="d.country" :to="`/tours`"
        class="group relative aspect-[3/4] overflow-hidden rounded-2xl bg-gradient-to-br from-brand-400 to-brand-700">
        <img :src="d.imageUrl" :alt="d.country" loading="lazy" decoding="async"
          @error="(e)=>(e.target.style.display='none')"
          class="h-full w-full object-cover transition duration-500 group-hover:scale-110" />
        <div class="absolute inset-0 bg-gradient-to-t from-ink-950/80 to-transparent" />
        <span class="absolute inset-x-0 bottom-3 text-center text-lg font-bold text-white">{{ d.country }}</span>
      </RouterLink>
    </div>
  </section>

  <!-- 精選行程 -->
  <section class="bg-ink-50 py-16 sm:py-20">
    <div class="container-x">
      <div class="flex items-end justify-between">
        <div><h2 class="text-2xl font-extrabold sm:text-3xl">本月精選行程</h2><p class="mt-2 text-ink-500">編輯團隊嚴選，最受旅人喜愛的歐洲路線。</p></div>
        <RouterLink to="/tours" class="hidden text-sm font-semibold text-brand-700 hover:underline sm:block">查看全部 →</RouterLink>
      </div>
      <div class="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3"><RouteCard v-for="r in featured" :key="r.id" :route="r" /></div>
    </div>
  </section>

  <!-- 為什麼選我們 -->
  <section class="container-x py-16 sm:py-20">
    <h2 class="text-center text-2xl font-extrabold sm:text-3xl">為什麼選擇我們</h2>
    <div class="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
      <div v-for="w in whyUs" :key="w.title" class="card p-6 text-center">
        <div class="mx-auto grid h-12 w-12 place-items-center rounded-2xl bg-brand-50 text-2xl text-brand-600">{{ w.icon }}</div>
        <h3 class="mt-3 text-lg font-bold">{{ w.title }}</h3>
        <p class="mt-1.5 text-sm text-ink-500">{{ w.desc }}</p>
      </div>
    </div>
  </section>

  <!-- 三步驟 -->
  <section class="bg-ink-50 py-16 sm:py-20">
    <div class="container-x">
      <h2 class="text-center text-2xl font-extrabold sm:text-3xl">三步驟，輕鬆出發</h2>
      <div class="mt-10 grid gap-6 sm:grid-cols-3">
        <div v-for="(s, i) in steps" :key="s.title" class="card p-6"><div class="text-3xl">{{ s.icon }}</div><h3 class="mt-3 text-lg font-bold">{{ i + 1 }}. {{ s.title }}</h3><p class="mt-1.5 text-sm text-ink-500">{{ s.desc }}</p></div>
      </div>
    </div>
  </section>

  <!-- 熱門路線 -->
  <section class="container-x py-16 sm:py-20">
    <h2 class="text-2xl font-extrabold sm:text-3xl">熱門路線</h2>
    <div class="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3"><RouteCard v-for="r in popular" :key="r.id" :route="r" /></div>
    <div class="mt-10 text-center"><RouterLink to="/tours" class="btn-primary px-7 py-3">瀏覽所有行程</RouterLink></div>
  </section>

  <!-- 旅人評價 -->
  <section class="bg-ink-50 py-16 sm:py-20">
    <div class="container-x">
      <h2 class="text-center text-2xl font-extrabold sm:text-3xl">旅人怎麼說</h2>
      <div class="mt-10 grid gap-6 sm:grid-cols-3">
        <div v-for="t in testimonials" :key="t.name" class="card p-6">
          <div class="flex items-center gap-1 text-amber-500"><span v-for="s in 5" :key="s">{{ s <= t.rating ? '★' : '☆' }}</span></div>
          <p class="mt-3 text-ink-600">「{{ t.text }}」</p>
          <div class="mt-4 flex items-center gap-3">
            <span class="grid h-9 w-9 place-items-center rounded-full text-sm font-bold text-white" :class="avatarColor(t.name)">{{ t.name.slice(0, 1) }}</span>
            <span class="text-sm font-semibold">{{ t.name }}</span>
          </div>
        </div>
      </div>
    </div>
  </section>

  <!-- 電子報訂閱 + CTA -->
  <section class="container-x py-16 sm:py-20">
    <div class="overflow-hidden rounded-3xl bg-gradient-to-br from-brand-600 to-brand-800 px-8 py-14 text-center text-white">
      <h2 class="text-2xl font-extrabold sm:text-3xl">訂閱電子報，搶先收到歐洲行程優惠</h2>
      <p class="mx-auto mt-3 max-w-xl text-brand-50">每月精選路線、季節限定行程與會員專屬折扣，直接送到你信箱。</p>
      <form v-if="!subscribed" @submit.prevent="subscribe" class="mx-auto mt-6 flex max-w-md flex-col gap-3 sm:flex-row">
        <input v-model="email" type="email" required placeholder="輸入你的 Email" class="input flex-1 !text-ink-900" />
        <button class="btn bg-white px-6 text-brand-700 hover:bg-brand-50">訂閱</button>
      </form>
      <p v-else class="mx-auto mt-6 max-w-md rounded-xl bg-white/15 px-4 py-3">✓ 訂閱成功！我們會把最新歐洲行程寄給你 🎉</p>
    </div>
  </section>
</template>
