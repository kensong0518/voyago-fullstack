import { describe, expect, it } from "vitest";
import { stopNames, stopsOf } from "../src/data/routeGeo";

describe("routeGeo（行程在地圖上的站點）", () => {
  it("已登記的行程：依序回傳城市名稱", () => {
    const names = stopNames({ slug: "paris-loire", country: "法國", location: "巴黎 · 羅亞爾河" });
    expect(names.startsWith("巴黎")).toBe(true);
    expect(stopsOf({ slug: "paris-loire" }).length).toBeGreaterThanOrEqual(2);
  });

  it("每一站都有座標", () => {
    for (const slug of ["paris-loire", "italy-classic", "norway-fjord", "portugal-lisbon"]) {
      for (const s of stopsOf({ slug })) {
        expect(Number.isFinite(s.x) && Number.isFinite(s.y), `${slug}/${s.key}`).toBe(true);
      }
    }
  });

  it("後台新增、還沒登記的行程：退回用國家中心畫一個點", () => {
    const stops = stopsOf({ slug: "new-route", country: "法國", location: "里昂 · 安錫" });
    expect(stops).toHaveLength(1);
    expect(stops[0].name).toBe("里昂");
  });

  it("連國家都不認得：沒有站點，名稱退回 location", () => {
    const r = { slug: "mars", country: "火星", location: "奧林帕斯山" };
    expect(stopsOf(r)).toEqual([]);
    expect(stopNames(r)).toBe("奧林帕斯山");
  });
});
