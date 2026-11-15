import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import RatingBadge from "../src/components/RatingBadge.vue";

describe("RatingBadge（評分顯示）", () => {
  it("沒有評價時寫「尚無評價」，不顯示 0.0", () => {
    const w = mount(RatingBadge, { props: { rating: 0, reviews: 0 } });
    expect(w.text()).toBe("尚無評價");
    expect(w.text()).not.toContain("0.0");
  });

  it("有評價時顯示一位小數與則數，並給螢幕閱讀器完整句子", () => {
    const w = mount(RatingBadge, { props: { rating: 4.666, reviews: 3 } });
    expect(w.text()).toContain("4.7");
    expect(w.text()).toContain("(3)");
    expect(w.attributes("aria-label")).toBe("平均 4.7 星，3 則評價");
  });
});
