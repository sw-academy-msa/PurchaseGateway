# 광고 결제 경계

`gateway.advertising.enabled`는 기본 false다. 환경 변수는 `GATEWAY_ADVERTISING_ENABLED`, 예정 SSM 이름은 `/stockdrop/prod/purchase-gateway/GATEWAY_ADVERTISING_ENABLED`다. 이 변경은 실제 파라미터를 생성하거나 조회하지 않는다.

광고 라우트는 SELLER의 GET `/api/v1/payments/advertising/{paymentId}`, GET `/{paymentId}/checkout`, POST `/{paymentId}/completion`만 payment-service로 전달한다. Bearer 및 Idempotency-Key를 보존하고 Cookie와 Origin은 제거한다. 다른 payments 경로 및 내부 취소 API는 공개하지 않는다. 본인 소유권과 사용자 주체는 Payment가 다시 검증한다.

상품 광고 신청은 기존 seller/products 경계를 사용한다. 신규 결제 귀환은 구매 주문 승인 경로와 분리한다. 일반 Gateway의 구매 또는 멤버십 경로는 변경하지 않는다.

Payment 및 Product를 기능 OFF로 배포하고 전용 토픽 및 ACL과 V5 및 V6를 준비한 뒤 이 라우트를 켠다. 소비와 복구가 준비된 후 Payment 신규 접수, Product 신규 접수, FE 순으로 활성화한다. 롤백은 FE와 서버 신규 접수만 먼저 중단하고 이 라우트는 기존 completion과 금융 상태 조회가 drain될 때까지 유지한다. main 배포와 운영 설정 변경은 수행하지 않는다.

검증은 ProductRoutingIntegrationTest에서 실제 로컬 JWT 공개키 검증과 LoadBalancer HTTP fixture로 판매자 전용 광고 경로 및 금지된 결제 경로를 확인한다. 기존 CORS 정책과 구매 및 상품 회귀는 전체 빌드에서 실행한다.
