# PurchaseGateway
Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.4 기반 구매용 Server WebFlux Gateway입니다.
일반 Gateway(8000)와 별도로 기본 8001 포트에서 실행합니다.

## 실행

```powershell
.\gradlew.bat test bootJar
.\gradlew.bat bootRun
```

- 상태: http://localhost:8001/actuator/health/readiness
- Eureka 기본 주소: http://localhost:8761/eureka/
- JWT 공개키 기본 주소: http://localhost:8080/.well-known/jwks.json
- bootRun은 .env를 자동으로 읽지 않습니다. IDE 또는 셸 환경변수로 설정합니다.
- Compose는 .env.example을 참고해 작성한 .env를 읽습니다.
- Docker 내부 localhost는 컨테이너 자신입니다. 서비스 주소는 호출 가능한 주소로 주입합니다.

## 구현 상태

JWT RS256·issuer·audience·만료·token_use 검증, 역할 검사, CORS, 추적 헤더,
공통 오류 응답, Eureka Client, LoadBalancer, Docker·CI 골격을 포함합니다.
개인키·DB·Refresh 인증·주문 Saga는 소유하지 않습니다.

현재 Product 판매자·심사 경로는 기존 Gateway에서 분리한 Command 라우트입니다.
일반 풀과 구매 풀을 분리해 선착순 요청의 부하와 장애 전파를 줄이는 구성을 따릅니다.
Order Command는 API가 확정되지 않아 라우팅하지 않습니다. AWS 배포·ALB 변경은 하지 않았습니다.
Gateway 분리만으로 공유 DB·Payment·ALB·Kafka의 장애까지 완전히 격리되지는 않습니다.

운영에서는 C/D의 Gateway 주소·ID를 각각 주입하고 Eureka A/B 주소를 모두 설정합니다.
같은 EC2의 서비스에 고정하지 않고 lb://product-command-service로 탐색합니다.
Compose 기본 바인딩은 localhost이므로 ALB 배포 전 사설 바인딩과 보안 그룹을 검토해야 합니다.
256MiB 컨테이너 메모리는 초기 검증값이며 실제 부하로 조정해야 합니다.
Docker HEALTHCHECK 실패만으로 자동 재시작되지 않습니다.

Git add/commit/push와 실제 배포는 사용자가 별도로 수행합니다.
## 확정된 배치와 DB 소유권

- 일반 A/B: 일반 Gateway, Eureka, Account, Product Query, Order Query, Payment, AI, Notification
- 구매 C/D: PurchaseGateway, Order Command, Product Command
- 일반 DB: 기존 RDS의 account/payment/ai/notification/product_query/orders_query
- Command DB: 별도 DB 전용 EC2 한 대의 PostgreSQL; orders/product와 각자의 Outbox·Saga·멱등 이력
- DB 전용 EC2는 구매 앱 EC2 안에 설치하지 않고 앱 ASG에도 포함하지 않습니다.
- C/D 복제본은 동일한 Command DB에 연결하며 인스턴스별 독립 원본 DB를 만들지 않습니다.
- Command 서비스는 각자 계정·스키마만 소유하고 서비스 간 공유 트랜잭션을 만들지 않습니다.
- PostgreSQL 데이터는 EBS에 영속 저장합니다. DB EC2 사양은 아직 미정입니다.
- Gateway는 어느 DB에도 접속하지 않으며 내부 HTTP·Eureka 탐색만 수행합니다.
- DB 분리는 일반 RDS에 대한 직접적인 자원 경합을 줄이지만 Order/Product 상호 영향과 공유 Payment/ALB/Kafka 영향은 남습니다.
- 실제 ALB 분기, 동시 요청 제한, 서비스 장애 시 동작은 API 구현 후 별도로 검증해야 합니다.
## 주문 결제 약관

BUYER GET /api/v1/orders/checkout-terms와 /api/v1/orders/checkout-terms/{version}을 Order Command로 전달합니다. 일반 주문 목록·상세 Query 경로는 추가하지 않습니다. 실제 ALB의 구매 대상 그룹 분기는 운영 배포 시 별도 적용합니다.
