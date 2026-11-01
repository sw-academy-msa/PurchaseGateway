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
                .route("product-inquiry-write", route -> route.path("/api/v1/products/*/inquiries")
                        .and().method(org.springframework.http.HttpMethod.POST)
                        .filters(filters -> filters.removeRequestHeader("Origin"))
                        .uri("lb://product-command-service"))
                .route("product-command", route -> route.path(
                        "/api/v1/seller/products", "/api/v1/seller/products/**",
                        "/api/v1/admin/product-reviews", "/api/v1/admin/product-reviews/**")
                        .filters(filters -> filters.removeRequestHeader("Origin"))
                        .uri("lb://product-command-service"))
                // Order Command의 API 계약 확정 후 명시적 라우트와 권한 규칙을 추가합니다.
                .build();
    }
}