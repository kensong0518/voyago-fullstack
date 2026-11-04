// 每條行程在地圖上走的城市（依行程順序）。城市座標在 europeMap.js（由 scripts/map 產生）。
// 後台新增、這裡還沒登記的行程：退回用國家中心畫一個點，地圖上一樣找得到，只是沒有路線。
import { CITIES, COUNTRY_CENTROIDS } from "./europeMap";

const STOPS = {
  "paris-loire": ["paris", "loire"],
  "italy-classic": ["rome", "florence", "venice"],
  "spain-barcelona": ["barcelona", "montserrat"],
  "swiss-jungfrau": ["zurich", "interlaken", "jungfrau", "lucerne"],
  "greece-santorini": ["athens", "santorini", "mykonos"],
  "netherlands-amsterdam": ["amsterdam", "keukenhof"],
  "czech-prague": ["prague", "krumlov"],
  "austria-vienna": ["vienna", "salzburg", "hallstatt"],
  "portugal-lisbon": ["lisbon", "sintra", "porto"],
  "norway-fjord": ["oslo", "flam", "bergen", "geiranger"],
};

/** 行程的站點：[{ key, name, x, y }]；第一站是地圖上標名稱的「代表城市」。 */
export function stopsOf(route) {
  const keys = STOPS[route.slug];
  if (keys) return keys.map((k) => ({ key: k, ...CITIES[k] }));
  const c = COUNTRY_CENTROIDS[route.country];
  if (!c) return [];
  return [{ key: `country:${route.country}`, name: (route.location || route.country).split(/[·、,]/)[0].trim(), x: c[0], y: c[1] }];
}

/** 站名串成一句，給卡片與列表用：「巴黎、凡爾賽、羅亞爾河谷」。 */
export function stopNames(route) {
  const s = stopsOf(route);
  return s.length ? s.map((x) => x.name).join("、") : route.location;
}
