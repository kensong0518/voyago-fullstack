// 產生歐洲地圖 SVG 路徑（Natural Earth 1:50m，公有領域）與城市座標，輸出成前端可直接 import 的模組。用法見 README.md
import fs from "node:fs";
import { geoConicConformal, geoPath, geoCentroid, geoBounds } from "d3-geo";
import { feature, merge, mesh } from "topojson-client";

const W = 1000, H = 860;
const topo = JSON.parse(fs.readFileSync("node_modules/world-atlas/countries-50m.json", "utf8"));
const countries = feature(topo, topo.objects.countries).features;

// 取景範圍：葡萄牙到希臘、挪威北部到地中海南岸
const frame = { type: "Feature", geometry: { type: "Polygon", coordinates: [[[-11, 34.3], [-11, 63.6], [31, 63.6], [31, 34.3], [-11, 34.3]]] } };
const projection = geoConicConformal().parallels([38, 60]).rotate([-10, 0]).fitExtent([[0, 0], [W, H]], frame).clipExtent([[-2, -2], [W + 2, H + 2]]);
const path = geoPath(projection).digits(0);

// 只留跟取景範圍有交集的國家（俄羅斯等跨換日線或很大的國家，邊界盒不準，直接收）
const keep = new Set();
for (const f of countries) {
  const [[x0, y0], [x1, y1]] = geoBounds(f);
  const overlaps = x1 >= -14 && x0 <= 34 && y1 >= 32 && y0 <= 66;
  if (overlaps || ["Russia", "Turkey", "Ukraine", "Belarus"].includes(f.properties.name)) keep.add(f.properties.name);
}
const geoms = topo.objects.countries.geometries.filter((g) => keep.has(g.properties.name));
// 陸地合併成一條路徑、國界只畫一次（相鄰國家共用的邊不重複），檔案小一半以上
const land = path(merge(topo, geoms));
const borders = path(mesh(topo, { type: "GeometryCollection", geometries: geoms }, (a, b) => a !== b));

const ZH = { 法國: "France", 義大利: "Italy", 西班牙: "Spain", 瑞士: "Switzerland", 希臘: "Greece", 荷蘭: "Netherlands",
  捷克: "Czechia", 奧地利: "Austria", 葡萄牙: "Portugal", 挪威: "Norway", 德國: "Germany", 英國: "United Kingdom",
  比利時: "Belgium", 丹麥: "Denmark", 瑞典: "Sweden", 芬蘭: "Finland", 波蘭: "Poland", 匈牙利: "Hungary",
  克羅埃西亞: "Croatia", 冰島: "Iceland", 愛爾蘭: "Ireland" };
const centroids = {};
for (const [zh, en] of Object.entries(ZH)) {
  const f = countries.find((c) => c.properties.name === en);
  if (!f) continue;
  // 挪威、法國等有海外領土，質心會偏；用本土範圍內的手動點覆寫
  const manual = { Norway: [9.5, 61], France: [2.5, 46.8], Netherlands: [5.3, 52.2], Portugal: [-8.2, 39.6], Greece: [22.5, 39.2] }[en];
  const [x, y] = projection(manual || geoCentroid(f));
  centroids[zh] = [Math.round(x), Math.round(y)];
}

const CITY = {
  paris: ["巴黎", 2.35, 48.86], loire: ["羅亞爾河谷", 1.52, 47.62], versailles: ["凡爾賽", 2.12, 48.80],
  rome: ["羅馬", 12.50, 41.90], florence: ["佛羅倫斯", 11.25, 43.77], venice: ["威尼斯", 12.33, 45.44],
  barcelona: ["巴塞隆納", 2.17, 41.39], montserrat: ["蒙瑟瑞特", 1.84, 41.59],
  zurich: ["蘇黎世", 8.54, 47.37], interlaken: ["茵特拉肯", 7.86, 46.69], jungfrau: ["少女峰", 7.98, 46.55], lucerne: ["琉森", 8.31, 47.05],
  athens: ["雅典", 23.73, 37.98], santorini: ["聖托里尼", 25.46, 36.39], mykonos: ["米克諾斯", 25.33, 37.45],
  amsterdam: ["阿姆斯特丹", 4.90, 52.37], keukenhof: ["庫肯霍夫", 4.55, 52.27],
  prague: ["布拉格", 14.44, 50.08], krumlov: ["庫倫洛夫", 14.32, 48.81],
  vienna: ["維也納", 16.37, 48.21], salzburg: ["薩爾斯堡", 13.04, 47.81], hallstatt: ["哈修塔特", 13.65, 47.56],
  lisbon: ["里斯本", -9.14, 38.72], sintra: ["辛特拉", -9.38, 38.80], porto: ["波多", -8.61, 41.15],
  oslo: ["奧斯陸", 10.75, 59.91], flam: ["弗洛姆", 7.11, 60.86], bergen: ["卑爾根", 5.32, 60.39], geiranger: ["蓋倫格", 7.21, 62.10],
};
const cities = {};
for (const [k, [name, lon, lat]] of Object.entries(CITY)) {
  const [x, y] = projection([lon, lat]);
  cities[k] = { name, x: Math.round(x * 10) / 10, y: Math.round(y * 10) / 10 };
}

const js = `// 由 scripts/map/gen-europe-map.mjs 產生，請勿手動修改。
// 底圖：Natural Earth 1:50m（公有領域），經 world-atlas 取得；圓錐等角投影，取景為歐洲大陸。
export const MAP_VIEWBOX = "0 0 ${W} ${H}";
export const LAND_PATH = ${JSON.stringify(land)};
export const BORDER_PATH = ${JSON.stringify(borders)};
export const CITIES = ${JSON.stringify(cities)};
export const COUNTRY_CENTROIDS = ${JSON.stringify(centroids)};
`;
fs.writeFileSync(process.argv[2], js);
console.log("bytes", js.length);
