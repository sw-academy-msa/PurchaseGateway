package com.msa.purchasegateway.global.config.security;

import com.msa.purchasegateway.global.error.GatewayErrors;

import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.*;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/** 구매 Command 경계를 보호합니다. 최신 권한·소유권은 각 Command에서도 다시 검사합니다. */
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain security(
            ServerHttpSecurity http, AuthProperties properties, GatewayErrors errors) {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http
                // Gateway는 세션·Refresh 인증을 하지 않으므로 이중 CSRF 토큰을 생성하지 않습니다.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(cors(properties)))
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(
                        handler ->
                                handler.authenticationEntryPoint(
                                                (exchange, exception) ->
                                                        errors.write(
                                                                exchange,
                                                                HttpStatus.UNAUTHORIZED,
                                                                "COMMON_UNAUTHORIZED",
                                                                "인증이 필요합니다."))
                                        .accessDeniedHandler(
                                                (exchange, exception) ->
                                                        errors.write(
                                                                exchange,
                                                                HttpStatus.FORBIDDEN,
                                                                "COMMON_FORBIDDEN",
                                                                "요청을 수행할 권한이 없습니다.")))
                .authorizeExchange(
                        auth ->
                                auth.pathMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .pathMatchers(
                                                HttpMethod.GET,
                                                "/actuator/health",
                                                "/actuator/health/liveness",
                                                "/actuator/health/readiness")
                                        .permitAll()
                                        .pathMatchers("/actuator/**")
                                        .denyAll()
                                        // 상품 문의는 BUYER의 쓰기이며 상품 목록·상세 GET은 Query 경계에 남깁니다.
                                        .pathMatchers(
                                                HttpMethod.POST, "/api/v1/products/*/inquiries")
                                        .hasRole("BUYER")
                                        .pathMatchers(
                                                "/api/v1/seller/products",
                                                "/api/v1/seller/products/**")
                                        .hasRole("SELLER")
                                        .pathMatchers(
                                                "/api/v1/admin/product-reviews",
                                                "/api/v1/admin/product-reviews/**")
                                        .hasRole("ADMIN")
                                        // Order 역할과 실제 HTTP 메서드를 제한합니다. 소유권은 Order에서도 재검증합니다.
                                        .pathMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/orders",
                                                "/api/v1/orders/*/payment-attempts",
                                                "/api/v1/orders/*/inquiries",
                                                "/api/v1/orders/*/inquiries/*/messages",
                                                "/api/v1/orders/*/inquiry-images",
                                                "/api/v1/orders/*/inquiry-images/*/completion")
                                        .hasRole("BUYER")
                                        .pathMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/orders/checkout-terms",
                                                "/api/v1/orders/checkout-terms/*",
                                                "/api/v1/orders/*/checkout",
                                                "/api/v1/orders/*/inquiries/*/editing",
                                                "/api/v1/orders/*/inquiry-images/*/download")
                                        .hasRole("BUYER")
                                        .pathMatchers(
                                                HttpMethod.PATCH,
                                                "/api/v1/orders/*/shipping-address")
                                        .hasRole("BUYER")
                                        .pathMatchers(
                                                HttpMethod.DELETE,
                                                "/api/v1/orders/*/inquiry-images/*")
                                        .hasRole("BUYER")
                                        .pathMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/seller/orders/*/shipment",
                                                "/api/v1/seller/orders/*/delivery-completion",
                                                "/api/v1/seller/orders/*/inquiries/*/answers")
                                        .hasRole("SELLER")
                                        .pathMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/seller/orders/*/fulfillment",
                                                "/api/v1/seller/orders/*/inquiries/*/editing",
                                                "/api/v1/seller/orders/*/inquiry-images/*/download")
                                        .hasRole("SELLER")
                                        // 나머지 일반 서비스와 Query 경로는 공개하지 않습니다.
                                        .anyExchange()
                                        .denyAll())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                new ReactiveJwtAuthenticationConverterAdapter(
                                                                        converter)))
                                        .authenticationEntryPoint(
                                                (exchange, exception) ->
                                                        errors.write(
                                                                exchange,
                                                                HttpStatus.UNAUTHORIZED,
                                                                "COMMON_UNAUTHORIZED",
                                                                "인증이 필요합니다.")))
                .build();
    }

    private UrlBasedCorsConfigurationSource cors(AuthProperties properties) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
