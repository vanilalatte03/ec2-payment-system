-- 개발용 더미데이터
-- 회원 공통 비밀번호: sparta1234
-- BCrypt 해시: $2b$10$GPazGXUOf.Ek2M3YYbt4GuGluvmUlm9.dA6M7MVQca4CQ0/iufLP.
--
-- 목적
-- - 회원, 상품, 장바구니, 주문, 결제, 포인트, 환불 필수 기능을 바로 테스트할 수 있게 한다.
-- - 테이블명은 현재 개발 스키마의 복수형 네이밍을 기준으로 한다.
-- - 더미데이터 ID는 10001~19999 범위를 사용한다.

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM refund_items WHERE id BETWEEN 10001 AND 19999;
DELETE FROM refunds WHERE id BETWEEN 10001 AND 19999;
DELETE FROM point_transactions WHERE id BETWEEN 10001 AND 19999;
DELETE FROM payments WHERE id BETWEEN 10001 AND 19999;
DELETE FROM order_items WHERE id BETWEEN 10001 AND 19999;
DELETE FROM orders WHERE id BETWEEN 10001 AND 19999;
DELETE FROM cart_items WHERE id BETWEEN 10001 AND 19999;
DELETE FROM carts WHERE id BETWEEN 10001 AND 19999;
DELETE FROM products WHERE id BETWEEN 10001 AND 19999;
DELETE FROM users WHERE id BETWEEN 10001 AND 19999;

SET FOREIGN_KEY_CHECKS = 1;

-- 회원 데이터
INSERT INTO users (
    id,
    name,
    email,
    password,
    phone,
    point_balance,
    created_at
) VALUES
    (
        10001,
        '르탄이',
        'sparta@nbcamp.com',
        '$2b$10$GPazGXUOf.Ek2M3YYbt4GuGluvmUlm9.dA6M7MVQca4CQ0/iufLP.',
        '010-1234-5678',
        235,
        NOW()
    ),
    (
        10002,
        '내일배움',
        'tester@nbcamp.com',
        '$2b$10$GPazGXUOf.Ek2M3YYbt4GuGluvmUlm9.dA6M7MVQca4CQ0/iufLP.',
        '010-2222-3333',
        0,
        NOW()
    );

-- 상품 데이터
INSERT INTO products (
    id,
    name,
    price,
    stock,
    description,
    status,
    category,
    created_at,
    updated_at
) VALUES
    (
        10001,
        '코튼 트윌 볼캡',
        5000,
        200,
        '데일리 필수템 코튼 트윌 볼캡. 가볍고 편안한 착용감으로 어떤 코디에도 포인트.',
        'ON_SALE',
        'ACCESSORY',
        NOW(),
        NOW()
    ),
    (
        10002,
        '베이직 스트라이프 삭스 3팩',
        6900,
        300,
        '데일리 필수 스트라이프 양말 3켤레 세트. 코튼 혼방으로 부드럽고 통기성 우수.',
        'ON_SALE',
        'ACCESSORY',
        NOW(),
        NOW()
    ),
    (
        10003,
        '오버핏 코튼 크루넥 티셔츠',
        39000,
        120,
        '부드러운 코튼 소재의 오버핏 크루넥 티셔츠. 데일리로 입기 좋은 베이직 아이템.',
        'ON_SALE',
        'TOP',
        NOW(),
        NOW()
    ),
    (
        10004,
        '워시드 와이드 데님 팬츠',
        68000,
        45,
        '빈티지 워싱 처리된 와이드 핏 데님. 편안한 핏감과 트렌디한 실루엣.',
        'ON_SALE',
        'BOTTOM',
        NOW(),
        NOW()
    ),
    (
        10005,
        '캔버스 미니 에코백',
        8900,
        150,
        '가벼운 캔버스 소재의 미니 에코백. 간단한 외출과 서브백으로 활용도 만점.',
        'ON_SALE',
        'BAG',
        NOW(),
        NOW()
    ),
    (
        10006,
        '램스울 오버사이즈 니트',
        89000,
        30,
        '부드러운 램스울 혼방 니트. 여유로운 오버사이즈 핏으로 레이어드에 최적.',
        'ON_SALE',
        'TOP',
        NOW(),
        NOW()
    ),
    (
        10007,
        '나일론 카고 조거팬츠',
        58000,
        60,
        '경량 나일론 소재의 카고 조거팬츠. 스트릿 무드의 실용적인 디자인.',
        'ON_SALE',
        'BOTTOM',
        NOW(),
        NOW()
    ),
    (
        10008,
        '레더 미니멀 크로스백',
        128000,
        20,
        '소프트 레더 소재의 미니멀 크로스백. 깔끔한 디자인으로 다양한 코디에 매치.',
        'ON_SALE',
        'BAG',
        NOW(),
        NOW()
    ),
    (
        10009,
        '청키 러닝 스니커즈',
        159000,
        25,
        '볼드한 청키 솔의 러닝 스니커즈. 뛰어난 쿠셔닝과 스타일리시한 디자인.',
        'ON_SALE',
        'SHOES',
        NOW(),
        NOW()
    ),
    (
        10010,
        '울 블렌드 싱글 코트',
        198000,
        0,
        '고급 울 블렌드 소재의 싱글 코트. 클래식한 실루엣으로 격식있는 룩 완성.',
        'SOLD_OUT',
        'OUTER',
        NOW(),
        NOW()
    ),
    (
        10011,
        '스트레치 슬림 치노팬츠',
        48000,
        80,
        '스트레치 소재로 편안한 슬림 치노팬츠. 오피스부터 캐주얼까지 활용도 높은 아이템.',
        'ON_SALE',
        'BOTTOM',
        NOW(),
        NOW()
    ),
    (
        10012,
        '코튼 와플 반팔티',
        9900,
        180,
        '와플 조직의 코튼 반팔티. 은은한 텍스처감으로 심플하지만 디테일이 살아있는 아이템.',
        'ON_SALE',
        'TOP',
        NOW(),
        NOW()
    );

-- 장바구니 데이터
INSERT INTO carts (
    id,
    user_id,
    version,
    created_at,
    updated_at
) VALUES
    (10001, 10001, 0, NOW(), NOW()),
    (10002, 10002, 0, NOW(), NOW());

INSERT INTO cart_items (
    id,
    cart_id,
    product_id,
    quantity,
    created_at,
    updated_at
) VALUES
    (10001, 10001, 10001, 2, NOW(), NOW()),
    (10002, 10001, 10005, 1, NOW(), NOW()),
    (10003, 10002, 10003, 1, NOW(), NOW());

-- 주문 데이터
-- 10001: 결제 완료 주문
-- 10002: 결제 대기 주문, 결제 확정/주문 취소 테스트용
-- 10003: 부분 환불된 주문
-- 10004: 전액 환불되어 취소된 주문
-- 10005: 결제 실패로 취소된 주문
INSERT INTO orders (
    id,
    user_id,
    order_number,
    total_amount,
    used_point,
    status,
    created_at,
    updated_at
) VALUES
    (10001, 10001, 'ORD-20260529-10001', 78000, 1000, 'COMPLETED', NOW(), NOW()),
    (10002, 10001, 'ORD-20260529-10002', 89000, 0, 'PAYMENT_PENDING', NOW(), NOW()),
    (10003, 10001, 'ORD-20260529-10003', 94500, 0, 'COMPLETED', NOW(), NOW()),
    (10004, 10001, 'ORD-20260529-10004', 128000, 20000, 'CANCELED', NOW(), NOW()),
    (10005, 10002, 'ORD-20260529-10005', 68000, 0, 'CANCELED', NOW(), NOW());

INSERT INTO order_items (
    id,
    order_id,
    product_id,
    source_cart_item_id,
    product_name,
    price,
    quantity,
    created_at,
    updated_at
) VALUES
    (10001, 10001, 10003, 10001, '오버핏 코튼 크루넥 티셔츠', 39000, 2, NOW(), NOW()),
    (10002, 10002, 10006, 10002, '램스울 오버사이즈 니트', 89000, 1, NOW(), NOW()),
    (10003, 10003, 10002, 10003, '베이직 스트라이프 삭스 3팩', 6900, 1, NOW(), NOW()),
    (10004, 10003, 10011, 10004, '스트레치 슬림 치노팬츠', 48000, 1, NOW(), NOW()),
    (10005, 10003, 10012, 10005, '코튼 와플 반팔티', 9900, 4, NOW(), NOW()),
    (10006, 10004, 10008, 10006, '레더 미니멀 크로스백', 128000, 1, NOW(), NOW()),
    (10007, 10005, 10004, 10007, '워시드 와이드 데님 팬츠', 68000, 1, NOW(), NOW());

-- 결제 데이터
INSERT INTO payments (
    id,
    order_id,
    portone_payment_id,
    status,
    payment_type,
    total_amount,
    used_point_amount,
    pg_amount,
    reward_point_amount,
    approved_at,
    failed_at,
    created_at,
    updated_at
) VALUES
    (
        10001,
        10001,
        'pay_dev_20260529_10001',
        'COMPLETED',
        'POINT_CARD',
        78000,
        1000,
        77000,
        770,
        NOW(),
        NULL,
        NOW(),
        NOW()
    ),
    (
        10002,
        10002,
        'pay_dev_20260529_10002',
        'PENDING',
        'CARD',
        89000,
        0,
        89000,
        0,
        NULL,
        NULL,
        NOW(),
        NOW()
    ),
    (
        10003,
        10003,
        'pay_dev_20260529_10003',
        'PARTIAL_REFUNDED',
        'CARD',
        94500,
        0,
        94500,
        945,
        NOW(),
        NULL,
        NOW(),
        NOW()
    ),
    (
        10004,
        10004,
        'pay_dev_20260529_10004',
        'FULL_REFUNDED',
        'POINT_CARD',
        128000,
        20000,
        108000,
        1080,
        NOW(),
        NULL,
        NOW(),
        NOW()
    ),
    (
        10005,
        10005,
        'pay_dev_20260529_10005',
        'FAILED',
        'CARD',
        68000,
        0,
        68000,
        0,
        NULL,
        NOW(),
        NOW(),
        NOW()
    );

-- 환불 데이터
INSERT INTO refunds (
    id,
    order_id,
    payment_id,
    reason,
    refund_amount,
    point_refund_amount,
    pg_refund_amount,
    status,
    created_at,
    refunded_at
) VALUES
      (
          10001,
          10003,
          10003,
          '사이즈 변경으로 일부 상품 환불',
          48000,
          0,
          48000,
          'COMPLETED',
          NOW(),
          NOW()
      ),
      (
          10002,
          10004,
          10004,
          '고객 요청으로 전체 환불',
          128000,
          20000,
          108000,
          'COMPLETED',
          NOW(),
          NOW()
      );

-- 포인트 거래 데이터
-- 회원 10001의 point_balance는 아래 원장 합계 235와 일치하며, 음수 포인트 잔액 금지 정책을 따른다.
INSERT INTO point_transactions (
    id,
    user_id,
    payment_id,
    refund_id,
    type,
    idempotency_key,
    amount,
    created_at
) VALUES
      (10001, 10001, 10001, NULL,  'USE',         'PAYMENT:10001:USE',          1000, NOW()),
      (10002, 10001, 10001, NULL,  'EARN',        'PAYMENT:10001:EARN',          770, NOW()),
      (10003, 10001, 10003, NULL,  'EARN',        'PAYMENT:10003:EARN',          945, NOW()),
      (10004, 10001, 10003, 10001, 'EARN_CANCEL', 'REFUND:10001:EARN_CANCEL',    480, NOW()),
      (10005, 10001, 10004, NULL,  'USE',         'PAYMENT:10004:USE',         20000, NOW()),
      (10006, 10001, 10004, NULL,  'EARN',        'PAYMENT:10004:EARN',         1080, NOW()),
      (10007, 10001, 10004, 10002, 'USE_RESTORE', 'REFUND:10002:USE_RESTORE',  20000, NOW()),
      (10008, 10001, 10004, 10002, 'EARN_CANCEL', 'REFUND:10002:EARN_CANCEL',   1080, NOW());

-- 환불 상품 데이터
INSERT INTO refund_items (
    id,
    refund_id,
    order_item_id,
    refund_quantity,
    unit_price,
    refund_amount,
    point_refund_amount,
    pg_refund_amount,
    created_at
) VALUES
      (10001, 10001, 10004, 1,  48000,  48000,     0,  48000, NOW()),
      (10002, 10002, 10006, 1, 128000, 128000, 20000, 108000, NOW());
