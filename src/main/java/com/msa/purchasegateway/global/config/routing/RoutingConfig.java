package com.msa.purchasegateway.global.config.routing;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 구매 풀에 허용된 Command 경로만 공개하며 일반 서비스와 Query는 연결하지 않습니다. */
@Configuration
public class RoutingConfig {
    @Bean
    public RouteLocator purchaseRoutes(RouteLocatorBuilder builder) {
        // 동거 컨테이너 주소에 고정하지 않고 Eureka·LoadBalancer로 복제본을 선택합니다.
        // Account 쿠키 인증은 일반 Gateway가 담당하며 이 서비스는 Bearer를 보존합니다.
        return builder.routes()
                // 상품 문의 생성 POST만 구매 Command로 보냅니다. 같은 상품 경로의 GET 조회는 공개하지 않습니다.
                .route(
                        "product-inquiry-write",
                        route ->
                                route.path("/api/v1/products/*/inquiries")
                                        .and()
                                        .method(org.springframework.http.HttpMethod.POST)
                                        .filters(filters -> filters.removeRequestHeader("Origin"))
                                        .uri("lb://product-command-service"))
                .route(
                        "product-command",
                        route ->
                                route.path(
                                                "/api/v1/seller/products",
                                                        "/api/v1/seller/products/**",
                                                "/api/v1/admin/product-reviews",
                                                        "/api/v1/admin/product-reviews/**")
                                        .filters(filters -> filters.removeRequestHeader("Origin"))
                                        .uri("lb://product-command-service"))
                // Order 작업용 GET도 Command로 보냅니다. 일반 주문 목록/상세는 노출하지 않습니다.
                .route(
                        "order-buyer-post",
                        route ->
                                route.path(
                                                "/api/v1/orders",
                                                "/api/v1/orders/*/payment-attempts",
                                                "/api/v1/orders/*/inquiries",
                                                "/api/v1/orders/*/inquiries/*/messages",
                                                "/api/v1/orders/*/inquiry-images",
                                                "/api/v1/orders/*/inquiry-images/*/completion")
                                        .and()
                                        .method(org.springframework.http.HttpMethod.POST)
                                        .filters(
                                                filters ->
                                                        filters.removeRequestHeader("Origin")
                                                                .removeRequestHeader("Cookie"))
                                        .uri("lb://order-command-service"))
                .route(
                        "order-buyer-get",
                        route ->
                                route.path(
                                                "/api/v1/orders/checkout-terms",
                                                "/api/v1/orders/checkout-terms/*",
                                                "/api/v1/orders/*/checkout",
                                                "/api/v1/orders/*/inquiries/*/editing",
                                                "/api/v1/orders/*/inquiry-images/*/download")
                                        .and()
                                        .method(org.springframework.http.HttpMethod.GET)
                                        .filters(
                                                filters ->
                                                        filters.removeRequestHeader("Origin")
                                                                .removeRequestHeader("Cookie"))
                                        .uri("lb://order-command-service"))
                .route(
                        "order-buyer-patch",
                        route ->
                                route.path("/api/v1/orders/*/shipping-address")
                                        .and()
                                        .method(org.springframework.http.HttpMethod.PATCH)
                                        .filters(
                                                filters ->
                                                        filters.removeRequestHeader("Origin")
                                                                .removeRequestHeader("Cookie"))
                                        .uri("lb://order-command-service"))
                .route(
                        "order-buyer-delete",
                        route ->
                                route.path("/api/v1/orders/*/inquiry-images/*")
                                        .and()
                                        .method(org.springframework.http.HttpMethod.DELETE)
                                        .filters(
                                                filters ->
                                                        filters.removeRequestHeader("Origin")
                                                                .removeRequestHeader("Cookie"))
                                        .uri("lb://order-command-service"))
                .route(
                        "order-seller-post",
                        route ->
                                route.path(
                                                "/api/v1/seller/orders/*/shipment",
                                                "/api/v1/seller/orders/*/delivery-completion",
                                                "/api/v1/seller/orders/*/inquiries/*/answers")
                                        .and()
                                        .method(org.springframework.http.HttpMethod.POST)
                                        .filters(
                                                filters ->
                                                        filters.removeRequestHeader("Origin")
                                                                .removeRequestHeader("Cookie"))
                                        .uri("lb://order-command-service"))
                .route(
                        "order-seller-get",
                        route ->
                                route.path(
                                                "/api/v1/seller/orders/*/fulfillment",
                                                "/api/v1/seller/orders/*/inquiries/*/editing",
                                                "/api/v1/seller/orders/*/inquiry-images/*/download")
                                        .and()
                                        .method(org.springframework.http.HttpMethod.GET)
                                        .filters(
                                                filters ->
                                                        filters.removeRequestHeader("Origin")
                                                                .removeRequestHeader("Cookie"))
                                        .uri("lb://order-command-service"))
                .build();
    }
}
