package com.msa.purchasegateway.global.config.routing;

import com.msa.purchasegateway.PurchaseGatewayApplication;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.*;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 실제 RS256/JWKS와 Gateway 프록시로 Order 경로·메서드·역할 경계를 검증합니다. */
@SpringBootTest(
        classes = PurchaseGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderRoutingIntegrationTest {
    static final RSAKey KEY;
    static final HttpServer JWKS;
    static final HttpServer ORDER;
    static final AtomicInteger CALLS = new AtomicInteger();

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("order-route-fixture").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext(
                    "/.well-known/jwks.json",
                    e -> {
                        byte[] b =
                                new JWKSet(KEY.toPublicJWK())
                                        .toString()
                                        .getBytes(StandardCharsets.UTF_8);
                        e.getResponseHeaders().set("Content-Type", "application/json");
                        e.sendResponseHeaders(200, b.length);
                        e.getResponseBody().write(b);
                        e.close();
                    });
            JWKS.start();
            ORDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ORDER.createContext(
                    "/",
                    e -> {
                        CALLS.incrementAndGet();
                        boolean clean =
                                !e.getRequestHeaders().containsKey("Origin")
                                        && !e.getRequestHeaders().containsKey("Cookie")
                                        && !e.getRequestHeaders().containsKey("X-User-Id")
                                        && e.getRequestHeaders().containsKey("Authorization");
                        byte[] b =
                                ("{\"service\":\"order-fixture\",\"clean\":" + clean + "}")
                                        .getBytes(StandardCharsets.UTF_8);
                        e.getResponseHeaders().set("Content-Type", "application/json");
                        e.sendResponseHeaders(200, b.length);
                        e.getResponseBody().write(b);
                        e.close();
                    });
            ORDER.start();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add(
                "gateway.auth.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/.well-known/jwks.json");
        r.add(
                "spring.cloud.discovery.client.simple.instances.order-command-service[0].uri",
                () -> "http://127.0.0.1:" + ORDER.getAddress().getPort());
    }

    @LocalServerPort int port;
    WebTestClient client;

    @BeforeEach
    void setup() {
        // CI의 첫 요청은 JWKS 조회·HTTP 클라이언트 초기화로 기본 5초 대기를 넘을 수 있어 응답 대기 시간을 늘립니다.
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).responseTimeout(Duration.ofSeconds(30)).build();
    }

    @AfterAll
    static void stop() {
        JWKS.stop(0);
        ORDER.stop(0);
    }

    @Test
    void buyerMutationsAndCommandReadsAreRoutedButQueryIsClosed() throws Exception {
        String token = token("BUYER");
        String seller = token("SELLER");
        // BUYER 전용 경로는 성공 응답과 함께 SELLER 단독 역할의 접근 거절도 확인합니다.
        for (String path :
                List.of(
                        "/api/v1/orders",
                        "/api/v1/orders/order-1/payment-attempts",
                        "/api/v1/orders/order-1/inquiries",
                        "/api/v1/orders/order-1/inquiries/inquiry-1/messages",
                        "/api/v1/orders/order-1/inquiry-images",
                        "/api/v1/orders/order-1/inquiry-images/image-1/completion")) {
            proxy(HttpMethod.POST, path, token);
            forbidden(HttpMethod.POST, path, seller);
        }
        for (String path :
                List.of(
                        "/api/v1/orders/checkout-terms",
                        "/api/v1/orders/checkout-terms/checkout-v1",
                        "/api/v1/orders/order-1/checkout",
                        "/api/v1/orders/order-1/inquiries/inquiry-1/editing",
                        "/api/v1/orders/order-1/inquiry-images/image-1/download")) {
            proxy(HttpMethod.GET, path, token);
            forbidden(HttpMethod.GET, path, seller);
        }
        proxy(HttpMethod.PATCH, "/api/v1/orders/order-1/shipping-address", token);
        forbidden(HttpMethod.PATCH, "/api/v1/orders/order-1/shipping-address", seller);
        proxy(HttpMethod.DELETE, "/api/v1/orders/order-1/inquiry-images/image-1", token);
        forbidden(HttpMethod.DELETE, "/api/v1/orders/order-1/inquiry-images/image-1", seller);
        for (String path :
                List.of(
                        "/api/v1/orders",
                        "/api/v1/orders/order-1",
                        "/api/v1/products",
                        "/internal/v1/stock-reservations")) {
            client.get()
                    .uri(path)
                    .headers(h -> h.setBearerAuth(token))
                    .exchange()
                    .expectStatus()
                    .isForbidden();
        }
    }

    @Test
    void sellerCanFulfillOwnCommandsAndBuyerCannotEnterSellerPaths() throws Exception {
        String seller = token("SELLER");
        String buyer = token("BUYER");
        for (String path :
                List.of(
                        "/api/v1/seller/orders/order-1/shipment",
                        "/api/v1/seller/orders/order-1/delivery-completion",
                        "/api/v1/seller/orders/order-1/inquiries/inquiry-1/answers")) {
            proxy(HttpMethod.POST, path, seller);
            client.post()
                    .uri(path)
                    .headers(h -> h.setBearerAuth(buyer))
                    .exchange()
                    .expectStatus()
                    .isForbidden();
        }
        // 판매자 업무용 GET도 BUYER 단독 역할로 접근하면 Order에 전달되지 않아야 합니다.
        for (String path :
                List.of(
                        "/api/v1/seller/orders/order-1/fulfillment",
                        "/api/v1/seller/orders/order-1/inquiries/inquiry-1/editing",
                        "/api/v1/seller/orders/order-1/inquiry-images/image-1/download")) {
            proxy(HttpMethod.GET, path, seller);
            forbidden(HttpMethod.GET, path, buyer);
        }
    }

    @Test
    void anonymousAndWrongMethodDoNotCallOrder() throws Exception {
        int before = CALLS.get();
        client.post().uri("/api/v1/orders").exchange().expectStatus().isUnauthorized();
        client.delete()
                .uri("/api/v1/orders/order-1/checkout")
                .headers(h -> h.setBearerAuth(tokenUnchecked()))
                .exchange()
                .expectStatus()
                .isForbidden();
        Assertions.assertEquals(before, CALLS.get());
    }

    /** 역할 거절은 403뿐 아니라 실제 Order 호출이 없다는 것까지 검증합니다. */
    private void forbidden(HttpMethod method, String path, String token) {
        int before = CALLS.get();
        client.method(method)
                .uri(path)
                .headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus()
                .isForbidden();
        Assertions.assertEquals(before, CALLS.get());
    }

    private void proxy(HttpMethod method, String path, String token) {
        client.method(method)
                .uri(path)
                .headers(h -> h.setBearerAuth(token))
                .header("Origin", "http://localhost:5173")
                .header("Cookie", "fixture=ignored")
                .header("X-User-Id", "forged")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.service")
                .isEqualTo("order-fixture")
                .jsonPath("$.clean")
                .isEqualTo(true);
    }

    private String tokenUnchecked() {
        try {
            return token("BUYER");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String token(String role) throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .issuer("http://localhost:8080")
                        .audience("stockdrop-api")
                        .subject(UUID.randomUUID().toString())
                        .issueTime(new Date())
                        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                        .claim("roles", List.of(role))
                        .claim("token_use", "access")
                        .build();
        var signed =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                        claims);
        signed.sign(new RSASSASigner(KEY));
        return signed.serialize();
    }
}
