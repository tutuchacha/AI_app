package com.tutuchacha.ai.date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutuchacha.ai.date.dto.DateSpot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 高德 Web 服务（place/text）最小封装：只按城市 + 关键词查 POI。
 * 任何异常都降级为空列表，保证「建议生成」不被地点查询拖垮；key 空则直接跳过。
 */
@Component
public class AmapClient {

    private static final Logger log = LoggerFactory.getLogger(AmapClient.class);

    private final RestClient restClient;
    private final String key;
    private final ObjectMapper objectMapper;

    public AmapClient(DateProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(4000);
        this.restClient = RestClient.builder()
                .baseUrl(properties.amapBaseUrlOrDefault())
                .requestFactory(factory)
                .build();
        this.key = properties.amapKeyOrEmpty();
        this.objectMapper = objectMapper;
    }

    public List<DateSpot> search(String city, String keyword, int limit) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(city) || !StringUtils.hasText(keyword)) {
            return List.of();
        }
        try {
            String raw = restClient.get()
                    .uri(builder -> builder.path("/v3/place/text")
                            .queryParam("key", key)
                            .queryParam("keywords", keyword.trim())
                            .queryParam("city", city.trim())
                            .queryParam("offset", Math.max(1, Math.min(limit, 10)))
                            .queryParam("page", 1)
                            .queryParam("extensions", "base")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode body = raw == null ? null : objectMapper.readTree(raw);
            if (body == null || !"1".equals(body.path("status").asText())) {
                log.warn("amap search failed: city={} keyword={} info={}", city, keyword,
                        body == null ? "empty" : body.path("info").asText());
                return List.of();
            }
            List<DateSpot> spots = new ArrayList<>();
            for (JsonNode poi : body.path("pois")) {
                if (spots.size() >= limit) {
                    break;
                }
                String name = poi.path("name").asText("");
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                spots.add(new DateSpot(
                        name,
                        poi.path("address").asText(""),
                        categoryOf(poi.path("type").asText("")),
                        poi.path("location").asText(""),
                        ratingOf(poi.path("biz_ext").path("rating"))));
            }
            return spots;
        } catch (Exception ex) {
            log.warn("amap search error: city={} keyword={} msg={}", city, keyword, ex.getMessage());
            return List.of();
        }
    }

    private String categoryOf(String type) {
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String[] parts = type.split(";");
        return parts[parts.length - 1].trim();
    }

    private String ratingOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.isNumber() ? node.asText() : node.asText("");
        return "[]".equals(value) ? "" : value;
    }
}
