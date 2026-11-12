package com.mongle.backend.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * application.yml의 mongle.weather.* — 위치는 서버 설정으로 고정한다(사용자 좌표를 받지 않는다).
 * 기본값은 서울. 배포하면서 사용자 위치를 받게 되면 그때 요청 파라미터를 추가한다.
 */
@Component
@ConfigurationProperties(prefix = "mongle.weather")
public class WeatherProperties {

    private String baseUrl = "https://api.open-meteo.com/v1";
    private double latitude = 37.5665;
    private double longitude = 126.9780;
    private String timezone = "Asia/Seoul";
    private int timeoutMs = 3000;
    private int cacheMinutes = 10;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getCacheMinutes() { return cacheMinutes; }
    public void setCacheMinutes(int cacheMinutes) { this.cacheMinutes = cacheMinutes; }
}
