package com.tutuchacha.ai.date.dto;

/**
 * 逆地理结果：浏览器坐标 → 城市 / 区县 / 详细地址。
 * 高德失败或未配置 key 时返回空串，前端据此提示「手动选择」，不阻断保存。
 */
public record GeoLocation(String city, String district, String address, String location) {

    public static GeoLocation empty() {
        return new GeoLocation("", "", "", "");
    }
}
