<script setup>
import { computed, onMounted, ref } from "vue";
import api from "../../api/api";
import { useAuthStore } from "../../stores/auth";

const auth = useAuthStore();

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

onMounted(loadMembers);
</script>

<template>
  <div>
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
</template>
