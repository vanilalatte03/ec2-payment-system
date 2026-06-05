# ERD

결제 시스템의 ERD를 이미지 기준으로 정리한 문서입니다.

## 관계도

```mermaid
erDiagram
    users ||--|| carts : owns
    users ||--o{ orders : places
    users ||--o{ point_transactions : has

    carts ||--o{ cart_items : contains
    products ||--o{ cart_items : added_to

    orders ||--o{ order_items : contains
    products ||--o{ order_items : ordered_as
    orders ||--|| payments : paid_by
    orders ||--o{ refunds : refunded_by

    payments ||--o{ point_transactions : creates
    payments ||--o{ refunds : refunded_by
    payments ||--o{ webhook_events : receives
    refunds o|--o{ point_transactions : creates
    refunds o|--o{ webhook_events : receives
    refunds ||--o{ refund_items : contains
    order_items ||--o{ refund_items : refunded_as

    users {
        BIGINT id PK "유저 ID"
        VARCHAR email "유저 이메일"
        VARCHAR password "유저 비밀번호"
        VARCHAR name "이름"
        VARCHAR phone "전화번호"
        BIGINT point_balance "포인트 잔액 스냅샷"
        DATETIME created_at "생성일시"
    }

    carts {
        BIGINT id PK "장바구니 ID"
        BIGINT user_id FK "유저 ID"
        BIGINT version "낙관락 버전"
        DATETIME created_at "생성일시"
        DATETIME updated_at "수정일시"
    }

    cart_items {
        BIGINT id PK "장바구니 상품 ID"
        BIGINT product_id FK "상품 ID"
        BIGINT cart_id FK "장바구니 ID"
        INT quantity "수량"
        DATETIME created_at "생성일시"
        DATETIME updated_at "수정일시"
    }

    products {
        BIGINT id PK "상품 ID"
        VARCHAR name "상품명"
        INT price "판매가"
        INT stock "재고 수량"
        VARCHAR description "상품 설명"
        VARCHAR status "판매 상태"
        VARCHAR category "카테고리"
        DATETIME created_at "생성일시"
        DATETIME updated_at "수정일시"
    }

    orders {
        BIGINT id PK "주문 ID"
        BIGINT user_id FK "유저 ID"
        VARCHAR order_number "주문번호"
        BIGINT total_amount "주문 총액"
        BIGINT used_point_amount "사용 포인트"
        VARCHAR status "주문 상태"
        DATETIME created_at "생성일시"
        DATETIME updated_at "수정일시"
    }

    order_items {
        BIGINT id PK "주문상품ID"
        BIGINT order_id FK "주문 ID"
        BIGINT product_id FK "상품 ID"
        BIGINT source_cart_item_id "원본 장바구니 상품 ID"
        VARCHAR product_name "상품명"
        INT price "가격"
        INT quantity "수량"
        INT refunded_quantity "환불 완료 수량"
        INT refund_reserved_quantity "환불 처리 중 예약 수량"
        VARCHAR status "주문상품 상태"
        DATETIME created_at "생성일시"
        DATETIME updated_at "수정일시"
    }

    order_number_sequences {
        BIGINT id PK "주문번호 시퀀스 ID"
        DATE order_date "주문일"
        INT last_number "마지막 번호"
    }

    payments {
        BIGINT id PK "결제ID"
        BIGINT order_id FK "주문 ID"
        VARCHAR portone_payment_id "포트원 ID"
        VARCHAR status "결제 상태"
        VARCHAR payment_type "결제 타입"
        BIGINT total_amount "총 금액"
        BIGINT used_point_amount "사용 포인트"
        BIGINT pg_amount "PG 결제 금액"
        BIGINT reward_point_amount "적립 포인트"
        DATETIME approved_at "결제 완료일시"
        DATETIME failed_at "결제 실패일시"
        DATETIME created_at "생성일시"
        DATETIME updated_at "수정일시"
    }

    point_transactions {
        BIGINT id PK "포인트거래ID"
        BIGINT payment_id FK "결제ID"
        BIGINT user_id FK "유저 ID"
        BIGINT refund_id FK "환불ID, nullable"
        VARCHAR type "거래타입"
        VARCHAR idempotency_key "멱등 키"
        BIGINT amount "거래금액"
        DATETIME created_at "생성일시"
    }

    refunds {
        BIGINT id PK "환불ID"
        VARCHAR idempotency_key "멱등 키"
        VARCHAR portone_payment_id "포트원 ID"
        BIGINT order_id FK "주문ID"
        BIGINT payment_id FK "결제ID"
        VARCHAR reason "환불사유"
        BIGINT refund_amount "총 환불 금액"
        BIGINT point_refund_amount "포인트 환불 금액"
        BIGINT pg_refund_amount "PG 환불 금액"
        VARCHAR status "환불 상태"
        DATETIME created_at "생성일시"
        DATETIME refunded_at "환불완료일시"
        VARCHAR failed_reason "실패 사유"
        VARCHAR pg_result_unknown_reason "PG 결과 미확정 사유"
    }

    webhook_events {
        BIGINT id PK "이벤트 ID"
        BIGINT refund_id FK "환불ID, nullable"
        BIGINT payment_id FK "결제ID, nullable"
        VARCHAR webhook_id "웹훅 ID"
        VARCHAR status "이벤트 상태"
        VARCHAR type "이벤트 타입"
        VARCHAR portone_payment_id "포트원 ID"
        VARCHAR failure_reason "실패 사유"
        DATETIME processed_at "처리 완료 일시"
        TEXT raw_payload "원본 페이로드"
        DATETIME created_at "생성일시"
        DATETIME updated_at "수정일시"
    }

    refund_items {
        BIGINT id PK "환불상품ID"
        BIGINT refund_id FK "환불ID"
        BIGINT order_item_id FK "주문상품ID"
        INT refund_quantity "환불수량"
        INT unit_price "상품 단가"
        BIGINT refund_amount "상품별 총 환불 금액"
        BIGINT point_refund_amount "포인트 환불금액"
        BIGINT pg_refund_amount "PG 환불 금액"
        DATETIME created_at "생성일시"
    }

```

## 테이블 정의

### users

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고  |
| --- | --- | --- | --- |--------|
| 유저 ID | id            | BIGINT | NOT NULL | PK     |
| 유저 이메일 | email         | VARCHAR(50) | NOT NULL | UNIQUE |
| 유저 비밀번호 | password      | VARCHAR(255) | NOT NULL |        |
| 이름 | name          | VARCHAR(20) | NOT NULL |        |
| 전화번호 | phone         | VARCHAR(50) | NOT NULL |        |
| 포인트 잔액 스냅샷 | point_balance | BIGINT | NOT NULL |        |
| 생성일시 | created_at    | DATETIME | NULL |        |

### carts

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 장바구니 ID | id | BIGINT | NOT NULL | PK |
| 유저 ID | user_id | BIGINT | NOT NULL | FK: users.id |
| 낙관락 버전 | version | BIGINT | NOT NULL | 장바구니 변경 충돌 감지용 |
| 생성일시 | created_at | DATETIME | NOT NULL |  |
| 수정일시 | updated_at | DATETIME | NULL |  |

### cart_items

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 장바구니 상품 ID | id | BIGINT | NOT NULL | PK |
| 상품 ID | product_id | BIGINT | NOT NULL | FK: products.id, UNIQUE(cart_id, product_id) |
| 장바구니 ID | cart_id | BIGINT | NOT NULL | FK: carts.id, UNIQUE(cart_id, product_id) |
| 수량 | quantity | INT | NOT NULL |  |
| 생성일시 | created_at | DATETIME | NOT NULL |  |
| 수정일시 | updated_at | DATETIME | NULL |  |

한 장바구니에는 같은 상품이 한 줄만 존재합니다. 같은 상품을 다시 담으면 새 row를 만들지 않고 기존 `cart_items.quantity`를 합산합니다.
결제 완료 시에는 주문에 포함된 `cart_items`만 삭제되며, 같은 장바구니에 남아 있는 미주문 상품 row는 유지됩니다.

### products

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 상품 ID | id | BIGINT | NOT NULL | PK |
| 상품명 | name | VARCHAR(100) | NOT NULL |  |
| 판매가 | price | INT | NOT NULL |  |
| 재고 수량 | stock | INT | NOT NULL |  |
| 상품 설명 | description | VARCHAR(255) | NOT NULL |  |
| 판매 상태 | status | VARCHAR(30) | NOT NULL | ON_SALE, SOLD_OUT, DISCONTINUED |
| 카테고리 | category | VARCHAR(30) | NOT NULL |  |
| 생성일시 | created_at | DATETIME | NOT NULL |  |
| 수정일시 | updated_at | DATETIME | NULL |  |

### orders

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 주문 ID | id           | BIGINT | NOT NULL | PK |
| 유저 ID | user_id      | BIGINT | NOT NULL | FK: users.id |
| 주문번호 | order_number | VARCHAR | NOT NULL |  |
| 주문 총액 | total_amount | BIGINT | NOT NULL |  |
| 사용 포인트 | used_point_amount | BIGINT | NOT NULL |  |
| 주문 상태 | status       | VARCHAR | NOT NULL | PAYMENT_PENDING, COMPLETED, PARTIAL_CANCELED, CANCELED |
| 생성일시 | created_at   | DATETIME | NOT NULL |  |
| 수정일시 | updated_at   | DATETIME | NULL |  |

### order_items

| 논리명 | 컬럼명          | 타입 | NULL | 제약/비고 |
| --- |--------------| --- | --- | --- |
| 주문상품ID | id           | BIGINT | NOT NULL | PK |
| 주문 ID | order_id     | BIGINT | NOT NULL | FK: orders.id |
| 상품 ID | product_id   | BIGINT | NOT NULL | FK: products.id |
| 원본 장바구니 상품 ID | source_cart_item_id | BIGINT | NOT NULL | 주문 생성 당시 원본 장바구니 상품 ID 스냅샷 |
| 상품명 | product_name | VARCHAR(100) | NOT NULL |  |
| 가격 | price        | INT | NOT NULL |  |
| 수량 | quantity     | INT | NOT NULL |  |
| 환불 완료 수량 | refunded_quantity | INT | NOT NULL | 기본값 0. 최종 환불 완료된 수량 |
| 환불 예약 수량 | refund_reserved_quantity | INT | NOT NULL | 기본값 0. `PROCESSING`, `PG_RESULT_UNKNOWN` 환불로 선점된 수량 |
| 주문상품 상태 | status | VARCHAR(30) | NOT NULL | ORDERED, CANCELED |
| 생성일시 | created_at   | DATETIME | NOT NULL |  |
| 수정일시 | updated_at   | DATETIME | NULL |  |

### order_number_sequences

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 주문번호 시퀀스 ID | id | BIGINT | NOT NULL | PK |
| 주문일 | order_date | DATE | NOT NULL | UNIQUE |
| 마지막 번호 | last_number | INT | NOT NULL | 날짜별 주문번호 증가값 |

### payments

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 결제ID | id | BIGINT | NOT NULL | PK |
| 주문 ID | order_id | BIGINT | NOT NULL | FK: orders.id, UNIQUE |
| 포트원 ID | portone_payment_id | VARCHAR(100) | NOT NULL | UNIQUE |
| 결제 상태 | status | VARCHAR(30) | NOT NULL | PENDING, COMPLETED, FAILED, PARTIAL_REFUNDED, FULL_REFUNDED |
| 결제 타입 | payment_type | VARCHAR(30) | NOT NULL | CARD, POINT_ONLY, POINT_CARD |
| 총 금액 | total_amount | BIGINT | NOT NULL | used_point_amount + pg_amount |
| 사용 포인트 | used_point_amount | BIGINT | NOT NULL | used_point_amount >= 0 |
| PG 결제 금액 | pg_amount | BIGINT | NOT NULL | pg_amount >= 0 |
| 적립 포인트 | reward_point_amount | BIGINT | NULL | reward_point_amount >= 0 |
| 결제 완료일시 | approved_at | DATETIME | NULL |  |
| 결제 실패일시 | failed_at | DATETIME | NULL |  |
| 생성일시 | created_at | DATETIME | NOT NULL |  |
| 수정일시 | updated_at | DATETIME | NOT NULL |  |

### point_transactions
제약 조건:
- `UNIQUE (idempotency_key)`

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 포인트거래ID | id         | BIGINT | NOT NULL | PK                                  |
| 결제ID    | payment_id | BIGINT | NOT NULL | FK: payments.id                     |
| 유저 ID   | user_id    | BIGINT | NOT NULL | FK: users.id                        |
| 환불ID | refund_id | BIGINT | NULL | FK: refunds.id. `USE_RESTORE`, `EARN_CANCEL`인 경우 저장 |
| 거래타입    | type       | VARCHAR | NOT NULL | USE_RESERVE, USE, USE_CANCEL, EARN, USE_RESTORE, EARN_CANCEL |
| 멱등 키 | idempotency_key | VARCHAR | NOT NULL | UNIQUE. 결제성 거래는 `PAYMENT:{paymentId}:{type}`, 환불성 거래는 `REFUND:{refundId}:{type}` |
| 거래금액    | amount     | BIGINT | NOT NULL | `EARN_CANCEL`은 멱등 기록을 위해 0 허용, 그 외 타입은 1 이상 |
| 생성일시    | created_at | DATETIME | NOT NULL | 포인트 거래 발생 시각                        |

### refunds

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 환불ID | id | BIGINT | NOT NULL | PK                            |
| 멱등 키 | idempotency_key | VARCHAR(100) | NOT NULL | 같은 결제 안에서 중복 환불 생성을 막기 위해 `UNIQUE(payment_id, idempotency_key)` 적용 |
| 포트원 ID | portone_payment_id | VARCHAR(100) | NOT NULL | 환불 생성 시점의 `payment.portone_payment_id` 스냅샷 |
| 주문ID | order_id | BIGINT | NOT NULL | FK: orders.id                 |
| 결제ID | payment_id | BIGINT | NOT NULL | FK: payments.id               |
| 환불사유 | reason | VARCHAR(255) | NOT NULL |                               |
| 총 환불 금액 | refund_amount | BIGINT | NOT NULL | 포인트 + PG 포함 총 환불 금액           |
| 포인트 환불 금액 | point_refund_amount | BIGINT | NOT NULL | 고객에게 복구되는 포인트 금액              |
| PG 환불 금액 | pg_refund_amount | BIGINT | NOT NULL |                               |
| 환불 상태 | status | VARCHAR(30) | NOT NULL | PROCESSING, COMPLETED, FAILED, PG_RESULT_UNKNOWN |
| 생성일시 | created_at | DATETIME | NOT NULL | 환불 요청이 생성된 시각                 |
| 환불완료일시 | refunded_at | DATETIME | NULL | 실제 PG 환불이 완료된 시각              |
| 실패 사유 | failed_reason | VARCHAR(500) | NULL | 실패 확정 시 저장 |
| PG 결과 미확정 사유 | pg_result_unknown_reason | VARCHAR(500) | NULL | 타임아웃 등으로 PG 취소 성공 여부를 모를 때 저장 |

### webhook_events
제약 조건:
- `UNIQUE (webhook_id)`

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 이벤트 ID | id | BIGINT | NOT NULL | PK |
| 환불ID | refund_id | BIGINT | NULL | FK: refunds.id. 환불 웹훅 처리 시 저장 |
| 결제ID | payment_id | BIGINT | NULL | FK: payments.id. 결제 매칭 실패 또는 무시 이벤트면 NULL 가능 |
| 웹훅 ID | webhook_id | VARCHAR(200) | NOT NULL | UNIQUE. PortOne `webhook-id` 헤더 값, 웹훅 메시지 멱등 키 |
| 이벤트 상태 | status | VARCHAR(30) | NOT NULL | RECEIVED, PROCESSED, IGNORE, FAILED |
| 이벤트 타입 | type | VARCHAR(50) | NOT NULL | 예: Transaction.Paid, Transaction.Failed, Transaction.Cancelled |
| 포트원 ID | portone_payment_id | VARCHAR(100) | NULL | PortOne 결제 ID. 결제 ID를 추출할 수 없는 무시 이벤트면 NULL 가능 |
| 실패 사유 | failure_reason | VARCHAR(500) | NULL | 처리 실패 또는 무시 사유 |
| 처리 완료 일시 | processed_at | DATETIME | NULL | 처리 완료 시각 |
| 원본 페이로드 | raw_payload | TEXT | NULL | 서명 검증에 사용한 원본 본문 |
| 생성일시 | created_at | DATETIME | NOT NULL | 수신 이벤트 생성 시각 |
| 수정일시 | updated_at | DATETIME | NOT NULL | 수신 이벤트 수정 시각 |

### refund_items

| 논리명 | 컬럼명 | 타입 | NULL | 제약/비고 |
| --- | --- | --- | --- | --- |
| 환불상품ID | id | BIGINT | NOT NULL | PK |
| 환불ID | refund_id | BIGINT | NOT NULL | FK: refunds.id |
| 주문상품ID | order_item_id | BIGINT | NOT NULL | FK: order_items.id |
| 환불수량 | refund_quantity | INT | NOT NULL | 1 이상 |
| 상품 단가 | unit_price | INT | NOT NULL | 환불 당시 주문 상품 단가 |
| 상품별 총 환불 금액 | refund_amount | BIGINT | NOT NULL | 해당 주문상품 기준 총 환불 금액 |
| 포인트 환불금액 | point_refund_amount | BIGINT | NOT NULL |  |
| PG 환불 금액 | pg_refund_amount | BIGINT | NOT NULL |  |
| 생성일시 | created_at | DATETIME | NOT NULL |  |

## 관계 요약

| 관계 | 설명 |
| --- | --- |
| user - cart | 유저는 장바구니를 가진다. |
| user - order | 유저는 여러 주문을 생성할 수 있다. |
| user - point_transaction | 유저는 여러 포인트 거래 내역을 가진다. |
| cart - cart_item | 장바구니는 여러 장바구니 상품을 담는다. |
| product - cart_item | 상품은 장바구니 상품으로 담길 수 있다. |
| order - order_item | 주문은 여러 주문 상품을 가진다. |
| product - order_item | 상품은 주문 상품으로 기록된다. |
| order - payment | 주문은 결제와 1:1로 연결된다. |
| order - refund | 주문은 여러 환불 내역을 가질 수 있다. |
| payment - point_transaction | 결제는 포인트 거래 내역을 생성할 수 있다. |
| payment - refund | 결제는 여러 환불 내역을 가질 수 있다. |
| payment - webhook_event | 결제는 여러 웹훅 수신 이벤트와 연결될 수 있다. |
| refund - point_transaction | 환불은 사용 포인트 복구 및 적립 포인트 회수 거래 내역을 생성할 수 있다. |
| refund - webhook_event | 환불은 여러 웹훅 수신 이벤트와 연결될 수 있다. |
| refund - refund_item | 환불은 여러 환불 상품을 가진다. |
| order_item - refund_item | 주문 상품은 환불 상품으로 참조된다. |
