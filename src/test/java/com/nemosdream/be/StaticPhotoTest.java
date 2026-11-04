package com.nemosdream.be;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 선수 사진 정적 서빙 (명세 2-2). DB 가 필요 없다. */
@SpringBootTest
@AutoConfigureMockMvc
class StaticPhotoTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void 선수_사진을_static_players_경로로_캐시와_함께_준다() throws Exception {
        mockMvc.perform(get("/static/players/50030.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/webp")))
                .andExpect(header().string("Cache-Control", containsString("max-age=604800")));
    }

    @Test
    void 없는_사진은_404() throws Exception {
        mockMvc.perform(get("/static/players/1.webp")).andExpect(status().isNotFound());
    }
}
