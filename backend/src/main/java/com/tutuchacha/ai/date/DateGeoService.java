package com.tutuchacha.ai.date;

import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.date.dto.GeoLocation;
import org.springframework.stereotype.Service;

/**
 * D-08：浏览器定位回填城市。只做参数校验 + 逆地理，失败返回空结果（不抛 502），
 * 让用户仍能手选城市。
 */
@Service
public class DateGeoService {

    private final AmapClient amapClient;

    public DateGeoService(AmapClient amapClient) {
        this.amapClient = amapClient;
    }

    public GeoLocation locate(double lng, double lat) {
        if (!Double.isFinite(lng) || !Double.isFinite(lat) || Math.abs(lng) > 180 || Math.abs(lat) > 90) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "经纬度不合法");
        }
        return amapClient.regeo(lng, lat);
    }
}
