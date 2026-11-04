# 歐洲地圖底圖

`前端/src/data/europeMap.js` 由這裡的腳本產生：Natural Earth 1:50m 國界（公有領域，經 `world-atlas` 套件取得），
用圓錐等角投影預先算成 SVG 路徑，網站執行時不需要任何地圖服務或圖資 API。

重新產生（例如要加新城市）：

```bash
cd scripts/map
npm i --no-save d3-geo@3 topojson-client@3 world-atlas@2
node gen-europe-map.mjs ../../前端/src/data/europeMap.js
```

城市座標在腳本的 `CITY` 表；行程走哪些城市在 `前端/src/data/routeGeo.js`。
