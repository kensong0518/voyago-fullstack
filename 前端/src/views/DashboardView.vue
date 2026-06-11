<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import api from "../api/api";
import { useAuthStore } from "../stores/auth";
import { useFavoritesStore } from "../stores/favorites";
import RouteCard from "../components/RouteCard.vue";

const auth = useAuthStore();
const fav = useFavoritesStore();
const router = useRouter();

const tab = ref("orders");
const bookings = ref([]);
const allRoutes = ref([]);
const busy = ref(true);

const profile = ref({ name: "", phone: "" });
const savingProfile = ref(false);
const profileMsg = ref("");

// 自刪
const deleteBusy = ref(false);
const deleteErr = ref("");

// ---- STAFF 後台：會員管理 ----
const memberList = ref([]);
const memberPage = ref(0);
const memberSize = 10;
const memberTotal = ref(0);
const memberTotalPages = ref(0);
const memberQ = ref("");
const memberBusy = ref(false);
const memberErr = ref("");
const newMember = ref({ name: "", email: "", phone: "", password: "", role: "MEMBER" });
const newMemberMsg = ref("");
const newMemberBusy = ref(false);
const PWD_RE = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/;
const newPwdValid = computed(() => PWD_RE.test(newMember.value.password));

const STATUS = {
  CONFIRMED: { label: "已確認", cls: "bg-brand-50 text-brand-700" },
  PENDING: { label: "處理中", cls: "bg-amber-50 text-amber-700" },
  CANCELLED: { label: "已取消", cls: "bg-ink-100 text-ink-500" },
};
const twd = (n) => new Intl.NumberFormat("zh-TW", { style: "currency", currency: "TWD", maximumFractionDigits: 0 }).format(n);
const dateText = (d) => new Date(d).toLocaleDateString("zh-TW");

const favoriteRoutes = computed(() => allRoutes.value.filter((r) => fav.has(r.id)));
const activeOrders = computed(() => bookings.value.filter((b) => b.status !== "CANCELLED"));

const TABS = computed(() => {
  const base = [
    { v: "orders", l: "我的訂單" },
    { v: "favorites", l: "我的收藏" },
    { v: "profile", l: "個人資料" },
  ];
  if (auth.isStaff) base.push({ v: "members", l: "👥 會員管理" });
  return base;
});

onMounted(async () => {
  if (!auth.ready) await auth.fetchMe();
  if (!auth.isLoggedIn) { router.push("/login"); return; }
  profile.value.name = auth.user?.name || "";
  profile.value.phone = auth.user?.phone || "";
  try {
    const [b, r] = await Promise.all([api.getBookings(), api.getRoutes({})]);
    bookings.value = b; allRoutes.value = r;
  } finally { busy.value = false; }
});

async function cancel(b) {
  if (!confirm(`確定要取消「${b.route.name}」這筆訂單嗎？取消後將直接刪除、不再顯示。`)) return;
  await api.cancelBooking(b.id);
  bookings.value = bookings.value.filter((x) => x.id !== b.id);   // 取消即刪除
}

async function saveProfile() {
  profileMsg.value = ""; savingProfile.value = true;
  try {
    const updated = await api.updateProfile({ name: profile.value.name, phone: profile.value.phone });
    if (auth.user) auth.user.name = updated.name || profile.value.name;
    profileMsg.value = "✓ 個人資料已更新";
  } catch (e) {
    profileMsg.value = e.message;
  } finally { savingProfile.value = false; }
}

async function deleteMyAccount() {
  if (!confirm("⚠️ 確定要刪除你的帳號嗎？\n\n所有訂單、客服訊息會一起刪除，無法復原。")) return;
  if (!confirm("再次確認：真的要刪除帳號？")) return;
  deleteErr.value = ""; deleteBusy.value = true;
  try {
    await auth.deleteAccount();
    router.push("/?deleted=1");
  } catch (e) {
    deleteErr.value = e.message;
  } finally { deleteBusy.value = false; }
}

// ---- STAFF 會員管理動作 ----
async function loadMembers() {
  memberBusy.value = true; memberErr.value = "";
  try {
    const data = await api.listMembers({ q: memberQ.value, page: memberPage.value, size: memberSize });
    memberList.value = data.content || [];
    memberTotal.value = data.totalElements ?? memberList.value.length;
    memberTotalPages.value = data.totalPages ?? 1;
  } catch (e) {
    memberErr.value = e.message;
  } finally { memberBusy.value = false; }
}

async function openMembersTab() {
  tab.value = "members";
  if (memberList.value.length === 0) await loadMembers();
}

function changePage(delta) {
  const next = memberPage.value + delta;
  if (next < 0 || next >= memberTotalPages.value) return;
  memberPage.value = next;
  loadMembers();
}

async function searchMembers() {
  memberPage.value = 0;
  await loadMembers();
}

async function createNewMember() {
  newMemberMsg.value = "";
  if (!newPwdValid.value) { newMemberMsg.value = "密碼需至少 8 碼且含英文與數字"; return; }
  newMemberBusy.value = true;
  try {
    await api.createMember({ ...newMember.value });
    newMemberMsg.value = "✓ 新增成功";
    newMember.value = { name: "", email: "", phone: "", password: "", role: "MEMBER" };
    await loadMembers();
  } catch (e) {
    newMemberMsg.value = e.message;
  } finally { newMemberBusy.value = false; }
}

async function deleteOneMember(m) {
  if (m.id === auth.user?.id) { alert("請改用「刪除我的帳號」"); return; }
  if (!confirm(`確定要刪除會員「${m.name}」(${m.email})？此動作無法復原`)) return;
  try {
    await api.deleteMember(m.id);
    await loadMembers();
  } catch (e) {
    alert(e.message);
  }
}
</script>

<template>
  <div v-if="!auth.isLoggedIn" class="container-x py-24 text-center text-ink-400">載入中…</div>
  <div v-else class="container-x py-12">
    <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div><h1 class="text-3xl font-extrabold">會員中心</h1><p class="mt-1 text-ink-500">{{ auth.user?.name }}，歡迎回來！</p></div>
      <RouterLink to="/tours" class="btn-primary self-start">+ 探索新行程</RouterLink>
    </div>

    <div class="mt-8 grid gap-6 lg:grid-cols-3">
      <!-- 側欄 -->
      <div class="card h-fit p-6">
        <img v-if="auth.user?.avatarUrl" :src="auth.user.avatarUrl" @error="(e)=>(e.target.style.display='none')" class="h-16 w-16 rounded-full object-cover" />
        <div v-else class="grid h-16 w-16 place-items-center rounded-full bg-brand-600 text-2xl font-bold text-white">{{ auth.user?.name?.slice(0, 1) }}</div>
        <h2 class="mt-4 text-lg font-bold">{{ auth.user?.name }}</h2>
        <p class="text-sm text-ink-500">{{ auth.user?.email }}</p>
        <div class="mt-4 space-y-2 border-t border-ink-100 pt-4 text-sm">
          <div class="flex justify-between"><span class="text-ink-400">登入方式</span><span class="font-medium">{{ auth.user?.provider === "GOOGLE" ? "Google" : "帳號密碼" }}</span></div>
          <div class="flex justify-between"><span class="text-ink-400">有效訂單</span><span class="font-medium">{{ activeOrders.length }} 筆</span></div>
          <div class="flex justify-between"><span class="text-ink-400">收藏行程</span><span class="font-medium">{{ fav.count }} 筆</span></div>
        </div>
        <nav class="mt-5 space-y-1">
          <button v-for="t in TABS" :key="t.v" @click="t.v === 'members' ? openMembersTab() : (tab = t.v)"
            :class="['w-full rounded-xl px-4 py-2.5 text-left text-sm font-medium transition', tab === t.v ? 'bg-brand-50 text-brand-700' : 'text-ink-600 hover:bg-ink-50']">{{ t.l }}</button>
          <RouterLink to="/chat" class="block w-full rounded-xl px-4 py-2.5 text-left text-sm font-medium text-ink-600 hover:bg-ink-50">💬 聯絡客服</RouterLink>
        </nav>
      </div>

      <!-- 主內容 -->
      <div class="lg:col-span-2">
        <!-- 訂單 -->
        <div v-if="tab === 'orders'">
          <h2 class="text-lg font-bold">我的訂單</h2>
          <p v-if="busy" class="mt-6 text-ink-400">載入訂單中…</p>
          <div v-else-if="bookings.length === 0" class="card mt-4 p-10 text-center">
            <p class="text-4xl">🧳</p><p class="mt-3 text-ink-500">你還沒有任何訂單。</p>
            <RouterLink to="/tours" class="btn-primary mt-4">開始探索行程</RouterLink>
          </div>
          <div v-else class="mt-4 space-y-4">
            <div v-for="b in bookings" :key="b.id" class="card flex gap-4 overflow-hidden p-3">
              <img :src="b.route.imageUrl" alt="" @error="(e)=>(e.target.style.display='none')" class="h-24 w-32 shrink-0 rounded-xl object-cover bg-gradient-to-br from-brand-300 to-brand-600" />
              <div class="flex flex-1 flex-col justify-between py-1">
                <div>
                  <div class="flex items-start justify-between gap-2">
                    <RouterLink :to="`/tours/${b.route.slug}`" class="font-bold hover:text-brand-700">{{ b.route.name }}</RouterLink>
                    <span :class="['chip', (STATUS[b.status] || STATUS.PENDING).cls]">{{ (STATUS[b.status] || STATUS.PENDING).label }}</span>
                  </div>
                  <p class="mt-1 text-sm text-ink-500">{{ b.route.country }} · {{ b.route.days }} 天 · {{ b.people }} 人 · 出發 {{ dateText(b.travelDate) }}</p>
                </div>
                <div class="flex items-end justify-between">
                  <span class="font-extrabold text-brand-700">{{ twd(b.totalPrice) }}</span>
                  <button v-if="b.status !== 'CANCELLED'" @click="cancel(b)" class="text-xs font-semibold text-rose-500 hover:underline">取消訂單</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 收藏 -->
        <div v-else-if="tab === 'favorites'">
          <h2 class="text-lg font-bold">我的收藏</h2>
          <div v-if="favoriteRoutes.length === 0" class="card mt-4 p-10 text-center">
            <p class="text-4xl">♡</p><p class="mt-3 text-ink-500">還沒有收藏任何行程，去逛逛吧！</p>
            <RouterLink to="/tours" class="btn-primary mt-4">探索行程</RouterLink>
          </div>
          <div v-else class="mt-4 grid gap-6 sm:grid-cols-2"><RouteCard v-for="r in favoriteRoutes" :key="r.id" :route="r" /></div>
        </div>

        <!-- 個人資料 -->
        <div v-else-if="tab === 'profile'">
          <h2 class="text-lg font-bold">個人資料</h2>
          <form @submit.prevent="saveProfile" class="card mt-4 max-w-lg space-y-4 p-6">
            <div><label class="label">姓名</label><input v-model="profile.name" required class="input" /></div>
            <div><label class="label">Email（不可修改）</label><input :value="auth.user?.email" disabled class="input bg-ink-50 text-ink-400" /></div>
            <div><label class="label">手機</label><input v-model="profile.phone" class="input" placeholder="0912-345-678" /></div>
            <p v-if="profileMsg" class="rounded-lg px-3 py-2 text-sm" :class="profileMsg.startsWith('✓') ? 'bg-brand-50 text-brand-700' : 'bg-red-50 text-red-600'">{{ profileMsg }}</p>
            <button :disabled="savingProfile" class="btn-primary">{{ savingProfile ? "儲存中…" : "儲存變更" }}</button>
          </form>

          <!-- 危險區：刪除帳號 -->
          <div class="card mt-6 max-w-lg border border-red-200 bg-red-50/40 p-6">
            <h3 class="text-base font-bold text-red-700">⚠️ 危險區</h3>
            <p class="mt-2 text-sm text-ink-600">
              刪除帳號後，你的所有訂單與客服訊息會一併消失，無法復原。
            </p>
            <p v-if="deleteErr" class="mt-3 rounded-lg bg-red-100 px-3 py-2 text-sm text-red-700">{{ deleteErr }}</p>
            <button @click="deleteMyAccount" :disabled="deleteBusy"
              class="btn mt-4 bg-red-600 text-white hover:bg-red-700 disabled:opacity-60">
              {{ deleteBusy ? "刪除中…" : "刪除我的帳號" }}
            </button>
          </div>
        </div>

        <!-- 會員管理（STAFF 限定） -->
        <div v-else-if="tab === 'members' && auth.isStaff">
          <h2 class="text-lg font-bold">👥 會員管理</h2>

          <!-- 新增會員 -->
          <details class="card mt-4 p-5" open>
            <summary class="cursor-pointer text-sm font-bold text-brand-700">+ 新增會員</summary>
            <form @submit.prevent="createNewMember" class="mt-4 grid gap-3 sm:grid-cols-2">
              <div><label class="label">姓名</label>
                <input v-model="newMember.name" required minlength="2" maxlength="50" class="input" /></div>
              <div><label class="label">Email</label>
                <input v-model="newMember.email" type="email" required class="input" /></div>
              <div><label class="label">手機（選填）</label>
                <input v-model="newMember.phone" type="tel" class="input" /></div>
              <div><label class="label">角色</label>
                <select v-model="newMember.role" class="input">
                  <option value="MEMBER">一般會員</option>
                  <option value="STAFF">客服人員</option>
                </select>
              </div>
              <div class="sm:col-span-2">
                <label class="label">初始密碼（至少 8 碼、含英文與數字）</label>
                <input v-model="newMember.password" type="password" required minlength="8"
                  autocomplete="new-password" class="input"
                  :class="newMember.password && !newPwdValid ? 'ring-1 ring-amber-400' : ''" />
              </div>
              <p v-if="newMemberMsg" class="sm:col-span-2 rounded-lg px-3 py-2 text-sm"
                :class="newMemberMsg.startsWith('✓') ? 'bg-brand-50 text-brand-700' : 'bg-red-50 text-red-600'">
                {{ newMemberMsg }}
              </p>
              <button :disabled="newMemberBusy || !newPwdValid" class="btn-primary sm:col-span-2">
                {{ newMemberBusy ? "建立中…" : "建立會員" }}
              </button>
            </form>
          </details>

          <!-- 搜尋 + 列表 -->
          <div class="card mt-4 p-5">
            <form @submit.prevent="searchMembers" class="flex gap-2">
              <input v-model="memberQ" placeholder="搜尋姓名 / Email / 電話…" class="input flex-1" />
              <button class="btn-outline whitespace-nowrap">搜尋</button>
            </form>

            <p v-if="memberBusy" class="mt-4 text-center text-ink-400">載入中…</p>
            <p v-else-if="memberErr" class="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">{{ memberErr }}</p>
            <p v-else-if="memberList.length === 0" class="mt-4 text-center text-ink-400">沒有符合條件的會員</p>

            <div v-else class="mt-4 overflow-x-auto">
              <table class="w-full text-sm">
                <thead class="border-b border-ink-100 text-left text-xs uppercase text-ink-400">
                  <tr>
                    <th class="pb-2 pr-3">姓名</th>
                    <th class="pb-2 pr-3">Email</th>
                    <th class="pb-2 pr-3">角色</th>
                    <th class="pb-2 pr-3">登入方式</th>
                    <th class="pb-2 text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="m in memberList" :key="m.id" class="border-b border-ink-50 last:border-0">
                    <td class="py-3 pr-3 font-medium">{{ m.name }}</td>
                    <td class="py-3 pr-3 text-ink-500">{{ m.email }}</td>
                    <td class="py-3 pr-3">
                      <span :class="['chip text-xs', m.role === 'STAFF' ? 'bg-brand-50 text-brand-700' : 'bg-ink-50 text-ink-600']">
                        {{ m.role === 'STAFF' ? '客服' : '會員' }}
                      </span>
                    </td>
                    <td class="py-3 pr-3 text-ink-500">{{ m.provider === "GOOGLE" ? "Google" : "帳密" }}</td>
                    <td class="py-3 text-right">
                      <button @click="deleteOneMember(m)" class="text-xs font-semibold text-rose-600 hover:underline"
                        :disabled="m.id === auth.user?.id">
                        {{ m.id === auth.user?.id ? "（自己）" : "刪除" }}
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- 分頁 -->
            <div v-if="memberTotalPages > 1" class="mt-4 flex items-center justify-between text-sm">
              <span class="text-ink-500">共 {{ memberTotal }} 位，第 {{ memberPage + 1 }} / {{ memberTotalPages }} 頁</span>
              <div class="flex gap-2">
                <button @click="changePage(-1)" :disabled="memberPage === 0" class="btn-outline px-3 py-1 disabled:opacity-50">←</button>
                <button @click="changePage(1)" :disabled="memberPage >= memberTotalPages - 1" class="btn-outline px-3 py-1 disabled:opacity-50">→</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
