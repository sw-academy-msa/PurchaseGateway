package com.msa.purchasegateway.global.config.routing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;

/** 광고 금융의 최소 판매자 경로만 열고 구매 결제 전체 경로는 공개하지 않습니다. */
@Configuration
@ConditionalOnProperty(name = "gateway.advertising.enabled", havingValue = "true")
public class AdvertisingRoutingConfig {
    @Bean
    public RouteLocator advertisingRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(
                        "advertising-payment-get",
                        r ->
                                r.path(
                                                "/api/v1/payments/advertising/*",
                                                "/api/v1/payments/advertising/*/checkout")
                                        .and()
                                        .method(HttpMethod.GET)
                                        .filters(
                                                f ->
                                                        f.removeRequestHeader("Origin")
                                                                .removeRequestHeader("Cookie"))
                                        .uri("lb://payment-service"))
                .route(
                        "advertising-payment-completion",
                        r ->
                                r.path("/api/v1/payments/advertising/*/completion")
                                        .and()
                                        .method(HttpMethod.POST)
                                        .filters(
                                                f ->
                                                        f.removeRequestHeader("Origin")
                                                                .removeRequestHeader("Cookie"))
                                        .uri("lb://payment-service"))
                .build();
    }
}
