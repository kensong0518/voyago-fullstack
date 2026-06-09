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

// 評價樣本池（依行程 id 取出穩定的幾則）
const REVIEW_POOL = [
  { name: "Jessica L.", rating: 5, date: "2026-04-18", text: "行程安排得很自由，住宿位置都在市中心，走到景點超方便！客服回覆也很快。" },
  { name: "陳柏宇", rating: 5, date: "2026-03-30", text: "第一次自助歐洲就選這個，手冊寫得很詳細，完全不用擔心迷路，推薦給新手。" },
  { name: "Mia Wang", rating: 4, date: "2026-03-12", text: "整體很棒，唯一小缺點是某天的飯店早餐選擇較少，但瑕不掩瑜。" },
  { name: "林志明", rating: 5, date: "2026-02-25", text: "鐵道票券和景點門票都幫你準備好，省下超多排隊時間，CP 值很高。" },
  { name: "Sophie C.", rating: 5, date: "2026-02-08", text: "風景美到不真實，行程鬆緊適中，還有自由活動時間可以亂逛，很喜歡。" },
  { name: "黃怡君", rating: 4, date: "2026-01-20", text: "客製化程度高，臨時想加一晚也幫忙處理，服務態度很好。" },
  { name: "David T.", rating: 5, date: "2026-01-05", text: "蜜月選這條路線完全正確，浪漫又難忘，會再回購其他國家的行程。" },
];

export function pickReviews(routeId, n = 4) {
  const start = (routeId * 2) % REVIEW_POOL.length;
  const out = [];
  for (let i = 0; i < n; i++) out.push(REVIEW_POOL[(start + i) % REVIEW_POOL.length]);
  return out;
}

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

// 相關行程：同國家優先，其次評價高
export function relatedRoutes(route, allRoutes, n = 3) {
  const sameCountry = allRoutes.filter((r) => r.id !== route.id && r.country === route.country);
  const rest = allRoutes
    .filter((r) => r.id !== route.id && r.country !== route.country)
    .sort((a, b) => b.rating - a.rating);
  return [...sameCountry, ...rest].slice(0, n);
}
