package com.voyago.controller;

import com.voyago.entity.Route;
import com.voyago.repository.RouteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 行程頁的分享預覽標籤由後端換成該行程的內容（爬蟲不執行 JS）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TourPageControllerTest {

    @Autowired MockMvc mvc;
    @Autowired RouteRepository routes;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)   // 讀取走 MyBatis，要真的 commit 才看得到
    void tourPage_hasThatTourInShareTags_andIsEscaped() throws Exception {
        Route r = new Route();
        r.setSlug("og-test-" + System.nanoTime());
        r.setName("冰島極光 <script>alert(1)</script> \"夜\"");
        r.setSummary("追極光 & 泡溫泉");
        r.setDescription("d");
        r.setLocation("雷克雅維克");
        r.setCountry("冰島");
        r.setDays(6);
        r.setPrice(88000);
        r.setImageUrl("https://images.example.com/iceland.jpg");
        r = routes.save(r);
        try {
            mvc.perform(get("/tours/" + r.getSlug()))
               .andExpect(status().isOk())
               .andExpect(header().string("Cache-Control", containsString("no-cache")))
               .andExpect(content().string(containsString(
                   "<title>冰島極光 &lt;script&gt;alert(1)&lt;/script&gt; &quot;夜&quot;｜Voyago 歐洲自助遊</title>")))
               .andExpect(content().string(containsString(
                   "<meta property=\"og:image\" content=\"https://images.example.com/iceland.jpg\"")))
               .andExpect(content().string(containsString(
                   "<meta property=\"og:url\" content=\"https://voyago.test/tours/" + r.getSlug() + "\"")))
               .andExpect(content().string(containsString("追極光 &amp; 泡溫泉（冰島・6 天・每人 NT$88,000 起）")))
               .andExpect(content().string(containsString("<meta property=\"og:type\" content=\"article\"")))
               .andExpect(content().string(not(containsString("<script>alert(1)"))))
               .andExpect(content().string(containsString("voyago-spa-test")));   // 仍是同一份 SPA
        } finally {
            routes.deleteById(r.getId());
        }
    }

    @Test
    void unknownTour_returnsDefaultIndex_soFrontendShowsNotFound() throws Exception {
        mvc.perform(get("/tours/no-such-tour"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("default og title")))
           .andExpect(content().string(containsString("voyago-spa-test")));
    }

    @Test
    void replaceMeta_leavesHtmlUntouched_whenTagMissing() {
        String html = "<head><title>t</title></head>";
        assertEquals(html, TourPageController.replaceMeta(html, "property", "og:image", "x"));
    }
}
