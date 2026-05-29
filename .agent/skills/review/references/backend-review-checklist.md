# Backend Review Checklist

## Layering

* Controller, Service, Repository 책임이 분리되어 있는가
* Entity와 DTO가 분리되어 있는가
* 외부 API Client가 Service 내부에 과하게 섞이지 않았는가
* 도메인 간 의존 방향이 과하게 꼬이지 않았는가

## Validation

* Request DTO 검증이 충분한가
* 음수 금액, 0원 결제, 빈 상품 목록, 잘못된 수량을 막는가
* 인증 사용자와 요청 리소스의 소유자가 일치하는가
* Enum 파라미터 오류 처리가 가능한가

## Security

* 인증이 필요한 API가 보호되어 있는가
* 내 주문/내 결제/내 포인트만 접근 가능한가
* 민감 정보가 응답이나 로그에 노출되지 않는가
* billingKey, token, secret 등이 로그에 찍히지 않는가

## Transaction Consistency

* 결제 성공 시 주문 상태, 결제 상태, 재고, 포인트가 일관되게 변경되는가
* 결제 실패 시 잘못된 상태가 남지 않는가
* 환불 시 결제 상태, 주문 상태, 포인트 복구, 적립 취소가 일관되게 처리되는가
* 외부 API 성공 후 내부 처리 실패에 대한 보상 트랜잭션이 고려되어 있는가

## Idempotency

결제/환불/웹훅은 중복 요청을 반드시 고려한다.

확인 대상:

* 같은 portonePaymentId로 confirm이 여러 번 들어오는 경우
* Client Confirm과 Webhook이 동시에 들어오는 경우
* webhookId가 제공되는 경우 같은 webhookId가 여러 번 들어오는 경우
* 이미 환불된 결제를 다시 환불 요청하는 경우

권장:

* `portone_payment_id` unique
* `webhook_id`는 PortOne 이벤트 식별자를 안정적으로 받을 수 있을 때만 unique
* 상태 기반 중복 처리
* 이미 처리된 요청은 가능한 한 안전하게 성공 응답
