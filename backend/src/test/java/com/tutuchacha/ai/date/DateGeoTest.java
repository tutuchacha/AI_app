package com.tutuchacha.ai.date;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.date.dto.GeoLocation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D-08：浏览器定位 → 高德逆地理回填城市；非法经纬度 400，高德失败降级为空结果。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DateGeoTest {

    private static final String DEMO = "geodemo";

    @MockitoBean
    private AmapClient amapClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void locateReturnsCityAndDistrict() throws Exception {
        when(amapClient.regeo(anyDouble(), anyDouble())).thenReturn(
                new GeoLocation("杭州市", "西湖区", "浙江省杭州市西湖区文三路 100 号", "120.153576,30.287459"));

        MvcResult result = mockMvc.perform(get("/api/date/geo/locate")
                        .param("lng", "120.153576")
                        .param("lat", "30.287459")
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();

        var data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("city").asText()).isEqualTo("杭州市");
        assertThat(data.path("district").asText()).isEqualTo("西湖区");
        assertThat(data.path("address").asText()).contains("文三路");
    }

    @Test
    void outOfRangeCoordinatesAreRejected() throws Exception {
        mockMvc.perform(get("/api/date/geo/locate")
                        .param("lng", "200")
                        .param("lat", "30")
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericCoordinatesAreRejected() throws Exception {
        mockMvc.perform(get("/api/date/geo/locate")
                        .param("lng", "abc")
                        .param("lat", "30")
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void amapFailureFallsBackToEmptyResult() throws Exception {
        when(amapClient.regeo(anyDouble(), anyDouble())).thenReturn(GeoLocation.empty());

        MvcResult result = mockMvc.perform(get("/api/date/geo/locate")
                        .param("lng", "120.15")
                        .param("lat", "30.28")
                        .with(user(DEMO).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();

        var data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("city").asText()).isEmpty();
        assertThat(data.path("district").asText()).isEmpty();
    }
}
