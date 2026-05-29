# API 명세

이 폴더는 결제 시스템의 API 명세를 도메인별로 나눈 문서입니다.

공통 응답 형식, 인증 방식, 공통 Enum, 공통 에러 코드는 [common.md](./common.md)에만 정의합니다. 각 도메인 문서에는 해당 API의 요청/응답 데이터와 발생 가능한 에러 코드만 적습니다.

## 문서 목록

| 문서 | 설명 |
| --- | --- |
| [common.md](./common.md) | Base URL, 인증, 응답 wrapper, 페이지네이션, Enum, 에러 코드 |
| [auth.md](./auth.md) | 회원가입, 로그인, 로그아웃 |
| [products.md](./products.md) | 상품 목록 조회, 상품 단건 조회 |
| [carts.md](./carts.md) | 장바구니 담기, 조회, 수량 변경, 삭제, 전체 비우기 |
| [orders.md](./orders.md) | 주문서 미리보기, 주문 생성, 내 주문 조회, 상세 조회, 결제대기 주문 취소 |
| [payments.md](./payments.md) | 결제 확정, 포인트 전액 결제 확정 |
| [points.md](./points.md) | 포인트 잔액 조회, 포인트 거래 내역 조회 |
| [refunds.md](./refunds.md) | 부분/전액 환불 요청 |
| [webhooks.md](./webhooks.md) | PortOne 웹훅 수신 |
| [memberships.md](./memberships.md) | 도전 기능: 멤버십 등급/누적 결제 금액 조회 |
| [subscriptions.md](./subscriptions.md) | 도전 기능: 빌링키 결제 수단, 구독 시작/조회/해지 |

## 엔드포인트 요약

| 도메인 | 기능 | Method | Path | 인증 |
| --- | --- | --- | --- | --- |
| 인증 | 회원가입 | POST | `/api/auth/signup` | 불필요 |
| 인증 | 로그인 | POST | `/api/auth/login` | 불필요 |
| 인증 | 로그아웃 | POST | `/api/auth/logout` | 필요 |
| 상품 | 상품 목록 조회 | GET | `/api/products` | 불필요 |
| 상품 | 상품 단건 조회 | GET | `/api/products/{productId}` | 불필요 |
| 장바구니 | 상품 담기 | POST | `/api/carts/items` | 필요 |
| 장바구니 | 내 장바구니 조회 | GET | `/api/carts` | 필요 |
| 장바구니 | 장바구니 상품 수량 변경 | PATCH | `/api/carts/items/{cartItemId}` | 필요 |
| 장바구니 | 장바구니 상품 삭제 | DELETE | `/api/carts/items/{cartItemId}` | 필요 |
| 장바구니 | 장바구니 전체 비우기 | DELETE | `/api/carts` | 필요 |
| 주문 | 주문서 미리보기 | GET | `/api/orders/preview` | 필요 |
| 주문 | 주문 생성 | POST | `/api/orders` | 필요 |
| 주문 | 내 주문 목록 조회 | GET | `/api/orders` | 필요 |
| 주문 | 주문 상세 조회 | GET | `/api/orders/{orderId}` | 필요 |
| 주문 | 결제대기 주문 취소 | POST | `/api/orders/{orderId}/cancel` | 필요 |
| 결제 | 결제 확정 | POST | `/api/payments/confirm` | 필요 |
| 포인트 | 포인트 잔액 조회 | GET | `/api/points/balance` | 필요 |
| 포인트 | 포인트 거래 내역 조회 | GET | `/api/points/transactions` | 필요 |
| 환불 | 환불 요청 | POST | `/api/refunds` | 필요 |
| 웹훅 | PortOne 웹훅 수신 | POST | `/api/webhooks/portone` | 서명 검증 |
| 멤버십 | 내 멤버십 조회 | GET | `/api/memberships/me` | 필요 |
| 구독 | 결제 수단 등록 | POST | `/api/billing-methods` | 필요 |
| 구독 | 내 결제 수단 조회 | GET | `/api/billing-methods` | 필요 |
| 구독 | 구독 시작 | POST | `/api/subscriptions` | 필요 |
| 구독 | 내 구독 조회 | GET | `/api/subscriptions/me` | 필요 |
| 구독 | 구독 해지 | POST | `/api/subscriptions/{subscriptionId}/cancel` | 필요 |

## 설계 메모

- 장바구니는 인증 회원당 1개를 전제로 하므로 URL에 `cartId`를 노출하지 않습니다. 모든 장바구니 API는 토큰의 회원 기준으로 동작합니다.
- 주문 생성 시 주문과 결제를 동시에 만들고, 재고는 이 시점에 선차감합니다.
- 장바구니는 주문 생성 시점이 아니라 결제 완료 시점에 비웁니다.
- 결제 확정 API와 웹훅은 같은 도메인 서비스를 호출해야 하며, `portonePaymentId` 기준으로 멱등하게 처리합니다.
- 포인트 잔액은 `user.point_snap`과 `point_transaction` 원장을 함께 관리합니다. 환불로 적립분을 회수할 때 현재 잔액이 부족하면 음수 잔액을 허용하는 정책을 문서화했습니다.
- 정기 결제 스케줄러는 공개 API가 아닙니다. 매일 00:00 KST에 내부 배치로 실행합니다.
