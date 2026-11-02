package com.voyago.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewServiceTest {

    @Test
    void maskName_hidesMiddleCharacters() {
        assertEquals("王*明", ReviewService.maskName("王小明"));
        assertEquals("歐*", ReviewService.maskName("歐陽"));
        assertEquals("K*n", ReviewService.maskName("Ken"));
        assertEquals("C***e", ReviewService.maskName("Christine"));   // 最多三個星號，不洩漏名字長度
        assertEquals("旅客", ReviewService.maskName("  "));
        assertEquals("😀*😀", ReviewService.maskName("😀a😀"));        // 以字元（code point）計算，不會切壞 emoji
    }
}
