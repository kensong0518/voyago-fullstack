// 行程詳情頁的共用內容：逐日行程解析、費用、須知、評價、相簿、相關行程

// 把描述文字解析成「逐日行程」與「總覽」
export function parseItinerary(description) {
  const parts = (description || "").split("。").map((s) => s.trim()).filter(Boolean);
  const days = [];
  let overview = "";
  for (const p of parts) {
    if (/^第.{1,5}天/.test(p)) days.push(p);
    else if (!overview) overview = p;
  }
  return { overview, days };
}

export const COST_INCLUDED = [
  "全程精選住宿（依行程天數）",
  "當地交通票券／鐵道通行證",
  "中文自助旅遊手冊與行程規劃",
  "主要景點門票或快速通關",
  "24 小時緊急聯絡客服",
];

export const COST_EXCLUDED = [
  "往返國際機票",
  "個人旅遊／申根簽證費用",
  "旅遊平安保險",
  "部分餐食與個人消費",
  "領隊與當地服務人員小費",
];

export const TRAVEL_NOTES = [
  "護照效期需於回國日起算 6 個月以上。",
  "歐洲多數國家屬申根區，請確認簽證與停留天數。",
  "建議自行投保涵蓋海外醫療的旅遊平安險。",
  "歐洲日夜溫差大，請依季節準備衣物。",
  "扒手較多的城市請留意隨身財物與證件。",
];

const AVATAR_COLORS = ["bg-brand-600", "bg-amber-500", "bg-rose-500", "bg-sky-600", "bg-violet-600", "bg-emerald-600"];
export function avatarColor(name) {
  let h = 0;
  for (const c of name) h = (h + c.charCodeAt(0)) % AVATAR_COLORS.length;
  return AVATAR_COLORS[h];
}

// 用所有行程的圖片組出一個「相簿」（主圖 + 其他幾張，附 onerror 安全網）
export function buildGallery(route, allRoutes) {
  const others = allRoutes.filter((r) => r.id !== route.id).map((r) => r.imageUrl);
  return [route.imageUrl, ...others].slice(0, 5);
}

// 相關行程推薦改由後端 /api/routes/{slug}/similar 計分（標籤重疊 + 同國家），
// 見 api.js 的 getSimilarRoutes；demo 模式走同一套邏輯的前端版本。
