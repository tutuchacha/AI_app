package com.tutuchacha.ai.date.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 高德 POI 精简结果；location 为 "lng,lat"，用于前端跳转高德。 */
@Schema(description = "约会地点推荐")
public record DateSpot(
        @Schema(description = "地点名称") String name,
        @Schema(description = "地址") String address,
        @Schema(description = "类别，取高德 type 最后一段") String category,
        @Schema(description = "经纬度 lng,lat") String location,
        @Schema(description = "评分，高德没给则为空") String rating,
        @Schema(description = "距查询中心多少米，仅周边搜索有值") String distance
) {

    /** 文本搜索没有距离。 */
    public static DateSpot text(String name, String address, String category, String location, String rating) {
        return new DateSpot(name, address, category, location, rating, "");
    }
}
