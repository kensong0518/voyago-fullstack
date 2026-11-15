import { beforeEach, describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { COMPARE_MAX, useCompareStore } from "../src/stores/compare";

const route = (n) => ({ slug: `r${n}`, name: `行程${n}`, imageUrl: `/img/${n}.jpg` });

describe("compare store（行程比較）", () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("加入再按一次就移除", () => {
    const s = useCompareStore();
    s.toggle(route(1));
    expect(s.has("r1")).toBe(true);
    s.toggle(route(1));
    expect(s.has("r1")).toBe(false);
    expect(s.count).toBe(0);
  });

  it(`最多 ${COMPARE_MAX} 個，第 ${COMPARE_MAX + 1} 個不會加入並顯示提示`, () => {
    const s = useCompareStore();
    for (let i = 1; i <= COMPARE_MAX + 1; i++) s.toggle(route(i));
    expect(s.count).toBe(COMPARE_MAX);
    expect(s.has(`r${COMPARE_MAX + 1}`)).toBe(false);
    expect(s.notice).toContain(String(COMPARE_MAX));
  });

  it("只存必要欄位，換頁重新載入後還在", () => {
    useCompareStore().toggle({ ...route(1), price: 1, description: "很長的內容" });
    setActivePinia(createPinia());
    const again = useCompareStore();
    expect(again.slugs).toEqual(["r1"]);
    expect(JSON.parse(localStorage.getItem("voyago_compare"))[0]).toEqual(route(1));
  });

  it("localStorage 內容壞掉時不當機，視為空的", () => {
    localStorage.setItem("voyago_compare", "{不是 JSON");
    setActivePinia(createPinia());
    expect(useCompareStore().count).toBe(0);
  });

  it("localStorage 裡超過上限或缺 slug 的項目會被濾掉", () => {
    localStorage.setItem("voyago_compare", JSON.stringify([route(1), null, { name: "沒有 slug" }, route(2), route(3), route(4)]));
    setActivePinia(createPinia());
    expect(useCompareStore().slugs).toEqual(["r1", "r2", "r3"]);
  });

  it("clear 清空", () => {
    const s = useCompareStore();
    s.toggle(route(1));
    s.clear();
    expect(s.count).toBe(0);
    expect(JSON.parse(localStorage.getItem("voyago_compare"))).toEqual([]);
  });
});
