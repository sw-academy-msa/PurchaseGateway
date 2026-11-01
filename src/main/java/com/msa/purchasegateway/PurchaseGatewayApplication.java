package com.msa.purchasegateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 구매 풀의 외부 요청을 Product·Order Command로 전달하는 독립 Gateway입니다. */
@SpringBootApplication
public class PurchaseGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(PurchaseGatewayApplication.class, args);
    }
}