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
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 高德 Web 服务最小封装：地理编码 + 关键词搜索 + 周边搜索。
 * 任何异常都降级为空结果，保证建议/问答不被地点查询拖垮；key 空则直接跳过。
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

    /** 按城市 + 关键词做文本搜索。 */
    public List<DateSpot> search(String city, String keyword, int limit) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(city) || !StringUtils.hasText(keyword)) {
            return List.of();
        }
        return query(builder -> builder.path("/v3/place/text")
                        .queryParam("key", key)
                        .queryParam("keywords", keyword.trim())
                        .queryParam("city", city.trim())
                        .queryParam("offset", clamp(limit))
                        .queryParam("page", 1)
                        .queryParam("extensions", "base")
                        .build(),
                "text", city + "/" + keyword, limit);
    }

    /** 围绕某个经纬度按关键词搜周边，按距离排序，带 distance。 */
    public List<DateSpot> around(String location, String keyword, int radius, int limit) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(location) || !StringUtils.hasText(keyword)) {
            return List.of();
        }
        return query(builder -> builder.path("/v3/place/around")
                        .queryParam("key", key)
                        .queryParam("location", location.trim())
                        .queryParam("keywords", keyword.trim())
                        .queryParam("radius", Math.max(500, Math.min(radius, 50000)))
                        .queryParam("offset", clamp(limit))
                        .queryParam("page", 1)
                        .queryParam("extensions", "base")
                        .queryParam("sortrule", "distance")
                        .build(),
                "around", location + "/" + keyword, limit);
    }

    /**
     * 区域中心：先地理编码；若只到区县/城市粒度（如「西湖」落到区政府），
     * 改用该区域首个 POI 的中心，避免周边搜索整体偏到行政区中心。
     */
    public String geocode(String city, String address) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(address)) {
            return "";
        }
        try {
            String raw = restClient.get()
                    .uri(builder -> {
                        builder.path("/v3/geocode/geo")
                                .queryParam("key", key)
                                .queryParam("address", address.trim());
                        if (StringUtils.hasText(city)) {
                            builder.queryParam("city", city.trim());
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .body(String.class);
            JsonNode body = raw == null ? null : objectMapper.readTree(raw);
            if (body == null || !"1".equals(body.path("status").asText())) {
                log.warn("amap geocode failed: city={} address={} info={}", city, address,
                        body == null ? "empty" : body.path("info").asText());
                return "";
            }
            JsonNode geocodes = body.path("geocodes");
            if (!geocodes.isArray() || geocodes.isEmpty()) {
                return "";
            }
            JsonNode first = geocodes.get(0);
            String location = first.path("location").asText("");
            if (!StringUtils.hasText(location) || !coarse(first.path("level").asText(""))) {
                return location;
            }
            String poi = firstPoiLocation(city, address);
            return StringUtils.hasText(poi) ? poi : location;
        } catch (Exception ex) {
            log.warn("amap geocode error: city={} address={} msg={}", city, address, ex.getMessage());
            return "";
        }
    }

    /** 区县级以下精度不够，改用区域名搜 POI 取第一个中心。 */
    private String firstPoiLocation(String city, String area) {
        return search(city, area, 3).stream()
                .map(DateSpot::location)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private boolean coarse(String level) {
        return switch (level) {
            case "国家", "省", "市", "区县", "乡镇", "村庄" -> true;
            default -> false;
        };
    }

    private List<DateSpot> query(Function<UriBuilder, URI> uriFunction, String api, String scene, int limit) {
        try {
            String raw = restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .body(String.class);
            JsonNode body = raw == null ? null : objectMapper.readTree(raw);
            if (body == null || !"1".equals(body.path("status").asText())) {
                log.warn("amap {} failed: scene={} info={}", api, scene,
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
                        ratingOf(poi.path("biz_ext").path("rating")),
                        poi.path("distance").asText("")));
            }
            return spots;
        } catch (Exception ex) {
            log.warn("amap {} error: scene={} msg={}", api, scene, ex.getMessage());
            return List.of();
        }
    }

    private int clamp(int limit) {
        return Math.max(1, Math.min(limit, 10));
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
