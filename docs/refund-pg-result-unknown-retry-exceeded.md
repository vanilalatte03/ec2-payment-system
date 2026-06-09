# PG_RESULT_UNKNOWN 환불 재시도 초과 시 자동 FAILED 처리 대신 운영자 확인 대상으로 남긴 이유

## 0. 핵심 정리

```text
문제:
PG_RESULT_UNKNOWN 환불이 재시도 초과 후에도 Refund 상태와 OrderItem 예약 수량을 유지하면서,
새 환불 요청이 REFUND_IN_PROGRESS로 계속 막힐 수 있었다.

처음 생각한 해결:
재시도 초과 시 Refund를 FAILED로 전환하고,
OrderItem 예약 수량과 포인트 회수 예약을 해제하려고 했다.

재검토한 이유:
PG_RESULT_UNKNOWN은 환불 실패가 아니라,
우리 서버가 PG 취소 성공 여부를 확정하지 못한 상태다.

즉, 서버는 타임아웃이나 미확정 응답을 받았지만,
PortOne에서는 실제 취소가 이미 성공했을 가능성이 있다.

최종 처리:
RefundOutbox는 FAILED로 멈추되,
Refund는 PG_RESULT_UNKNOWN 상태로 유지한다.
OrderItem 예약 수량과 포인트 회수 예약도 해제하지 않는다.
동일 결제에 대한 새 환불 요청도 계속 차단한다.

이 상태는 자동 실패가 아니라,
운영자가 PortOne 관리자 콘솔/API로 실제 취소 결과를 확인해야 하는 보류 상태로 본다.
```

---

## 1. 문제 상황

환불 처리 중 PortOne 취소 결과가 명확하지 않은 경우가 있다.

예를 들어 다음과 같은 상황이다.

* PortOne 취소 API 호출 중 타임아웃 발생
* PortOne에서 `REQUESTED`처럼 최종 성공/실패가 확정되지 않은 상태 반환
* 서버가 PG 취소 성공 여부를 확정하지 못함

처음에는 이런 경우를 단순히 재시도 대상으로 보고, 재시도 횟수를 초과하면 최종 실패로 처리하면 된다고 생각했다.

하지만 환불에서는 단순히 "서버가 실패 응답을 받았다"는 사실만으로 실제 PG 취소 실패를 확정할 수 없다.

예를 들어 서버 입장에서는 타임아웃이 발생했지만, 실제로는 PortOne에서 취소 처리가 성공했을 수 있다.

그래서 `PG_RESULT_UNKNOWN` 상태를 추가했다.

```java
if (refund.isProcessing()) {
    refund.markPgResultUnknown(message);
}

outbox.markRetry(message, LocalDateTime.now());
```

이 구조에서는 PG 취소 결과를 확정하지 못한 환불을 `PG_RESULT_UNKNOWN`으로 표시하고, Outbox를 다시 재시도 대상으로 돌린다.

하지만 문제가 있었다.

`RefundOutbox`는 최대 재시도 횟수를 초과하면 `FAILED`로 바뀔 수 있었지만, 연결된 `Refund`와 `OrderItem`은 함께 정리되지 않았다.

그 결과 다음과 같은 상태가 발생할 수 있었다.

```text
RefundOutbox.status = FAILED
Refund.status = PG_RESULT_UNKNOWN
OrderItem.refundReservedQuantity = 유지
포인트 회수 예약 = 유지
```

이 상태가 되면 사용자가 다시 환불 요청을 해도 `RefundService`의 진행 중 환불 검증에 걸려 새 환불 요청을 할 수 없다.

```java
private void validateNoActiveRefund(Payment payment) {
    boolean exists = refundRepository.existsByPayment_IdAndStatusIn(
            payment.getId(),
            List.of(RefundStatus.PROCESSING, RefundStatus.PG_RESULT_UNKNOWN)
    );

    if (exists) {
        throw new BusinessException(ErrorCode.REFUND_IN_PROGRESS);
    }
}
```

처음에는 이 상태를 단순히 "새 환불 요청이 계속 막히는 문제"로 보았다.

하지만 다시 검토해보니, 이 차단은 중복 환불을 막기 위해 필요한 안전장치이기도 했다.

---

## 2. 원인

원인은 Outbox의 재시도 실패 처리와 환불 도메인 상태 관리의 의미를 명확히 구분하지 못한 데 있었다.

기존 구조에서는 `RefundOutbox.markRetry()`가 내부에서 최대 재시도 횟수를 초과하면 Outbox만 `FAILED`로 변경할 수 있었다.

```java
if (this.retryCount > MAX_RETRY_COUNT) {
    markFailed("최대 재시도 횟수를 초과했습니다. 마지막 오류: " + reason);
    return;
}
```

하지만 `RefundOutbox`는 작업표 역할을 담당한다.

즉, Outbox의 `FAILED`는 다음 의미에 가깝다.

```text
이 Outbox 작업은 더 이상 자동 재시도하지 않는다.
```

반면 `Refund.status = FAILED`는 더 강한 의미를 가진다.

```text
이 환불은 최종 실패로 확정되었다.
```

이 둘은 같은 의미가 아니다.

특히 `PG_RESULT_UNKNOWN` 상태에서는 실제 PG 취소 결과를 모른다.

따라서 Outbox가 재시도 한도를 초과했다고 해서 Refund까지 자동으로 FAILED 처리하면 위험할 수 있다.

자동으로 Refund를 FAILED 처리하고 예약 수량을 해제하면, 사용자는 같은 상품에 대해 다시 환불 요청을 보낼 수 있다.

하지만 실제로 PortOne에서는 이미 취소가 성공했을 수 있다.

이 경우 같은 상품 또는 같은 금액에 대해 중복 환불이 발생할 수 있다.

---

## 3. 처음 생각한 해결 방향과 재검토

처음에는 Outbox 재시도 횟수를 초과하면 환불을 최종 실패로 확정하고, Refund와 OrderItem 예약 수량을 함께 정리하는 방향을 생각했다.

```text
1. RefundOutbox.markRetry()가 재시도 예약 성공 여부를 boolean으로 반환한다.
2. 재시도 가능하면 true를 반환한다.
3. 재시도 횟수를 초과하면 Outbox를 FAILED로 바꾸고 false를 반환한다.
4. RefundProcessingTxService는 markRetry() 결과가 false이면 Refund를 FAILED 처리한다.
5. OrderItem 예약 수량과 포인트 회수 예약도 함께 해제한다.
```

이렇게 하면 다음 상태 불일치를 해결할 수 있다고 생각했다.

```text
RefundOutbox는 FAILED인데 Refund는 PG_RESULT_UNKNOWN으로 남는 문제
OrderItem 예약 수량이 계속 잡혀 있는 문제
새 환불 요청이 REFUND_IN_PROGRESS로 계속 막히는 문제
```

하지만 이 방식은 `PG_RESULT_UNKNOWN`의 의미를 충분히 고려하지 못한 해결이었다.

`PG_RESULT_UNKNOWN`은 "환불 실패"가 아니라 "PG 취소 성공 여부를 서버가 확정하지 못한 상태"다.

따라서 실제로는 PortOne에서 이미 취소가 성공했을 가능성이 있다.

이 상태에서 우리 서버가 자동으로 Refund를 FAILED 처리하고 예약 수량을 해제하면, 같은 상품에 대해 새 환불 요청이 가능해진다.

그 결과 실제 PG에서는 이미 한 번 취소된 건에 대해 중복 환불을 시도할 위험이 생긴다.

그래서 최종적으로는 자동 FAILED 처리보다, 중복 환불 방지를 우선하는 방향으로 결정했다.

---

## 4. 수정 내용

### 4-1. RefundOutbox.markRetry()가 boolean을 반환하도록 수정

`RefundOutbox`는 재시도 예약 가능 여부를 호출자에게 알려주도록 수정했다.

```java
public boolean markRetry(String reason, LocalDateTime now) {
    if (now == null) {
        throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    if (this.status != RefundOutboxStatus.PROCESSING) {
        throw new BusinessException(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
    }

    this.retryCount++;

    if (this.retryCount > MAX_RETRY_COUNT) {
        markFailed("최대 재시도 횟수를 초과했습니다. 마지막 오류: " + reason);
        return false;
    }

    this.status = RefundOutboxStatus.PENDING;
    this.lastErrorMessage = normalizeErrorMessage(reason);

    long delayMinutes = Math.min(
            (long) (BASE_RETRY_DELAY_MINUTES * Math.pow(2, retryCount - 1)),
            MAX_RETRY_DELAY_MINUTES
    );

    long jitterSeconds = ThreadLocalRandom.current().nextLong(0, MAX_JITTER_SECONDS + 1);

    this.nextAttemptAt = now.plusMinutes(delayMinutes).plusSeconds(jitterSeconds);
    this.processingStartedAt = null;

    return true;
}
```

핵심은 반환값이다.

```text
true  -> 재시도 예약 성공
false -> 재시도 횟수 초과로 Outbox FAILED 전환
```

이렇게 하면 `RefundProcessingTxService`가 Outbox의 재시도 초과 여부를 알 수 있다.

---

### 4-2. RefundProcessingTxService에서 재시도 초과 여부를 확인

`retryAsPgResultUnknown()`에서 `markRetry()` 결과를 받아 재시도 초과 여부를 확인하도록 수정했다.

```java
@Transactional
public void retryAsPgResultUnknown(Long outboxId, String reason) {
    RefundOutbox outbox = findOutboxForUpdate(outboxId);
    Refund refund = outbox.getRefund();

    String message = reason == null || reason.isBlank()
            ? "PG 취소 결과를 확정하지 못했습니다."
            : reason;

    if (refund.isProcessing()) {
        refund.markPgResultUnknown(message);
    }

    boolean retryScheduled = outbox.markRetry(message, LocalDateTime.now());

    if (!retryScheduled) {
        /*
         * PG_RESULT_UNKNOWN은 실제 PG에서 이미 환불 성공했을 가능성이 있습니다.
         * 따라서 자동으로 Refund FAILED 처리하거나 예약 수량/포인트를 해제하면
         * 같은 상품을 다시 환불할 수 있어 중복 환불 위험이 생깁니다.
         * 재시도 초과 시에는 Outbox만 FAILED로 멈추고,
         * Refund는 PG_RESULT_UNKNOWN으로 남겨 운영자가 PortOne 관리자 콘솔/API로 확인해야 합니다.
         */
        return;
    }
}
```

처음에는 `retryScheduled == false`일 때 Refund를 FAILED 처리하려고 했다.

하지만 최종적으로는 자동 실패 정리를 하지 않고, 운영자 확인 대상으로 남기는 방향으로 변경했다.

---

### 4-3. 재시도 초과 시 자동 FAILED 처리하지 않도록 변경

재시도 초과 시 최종 상태는 다음과 같다.

```text
RefundOutbox.status = FAILED
Refund.status = PG_RESULT_UNKNOWN 유지
OrderItem.refundReservedQuantity 유지
포인트 회수 예약 유지
새 환불 요청 차단 유지
```

즉, 이 상태는 자동 실패가 아니라 "운영자 확인이 필요한 보류 상태"로 본다.

이렇게 처리하는 이유는 `PG_RESULT_UNKNOWN` 상태에서 실제 PG 취소가 성공했을 가능성이 있기 때문이다.

자동으로 예약 수량을 해제하면 동일 상품에 대한 새 환불 요청이 가능해지고, 이미 PG에서 취소된 건을 다시 환불할 위험이 있다.

---

## 5. 수정 후 흐름

수정 후 전체 흐름은 다음과 같다.

```text
PortOne 취소 결과 미확정
↓
Refund.status = PG_RESULT_UNKNOWN
↓
RefundOutbox.markRetry()
↓
재시도 가능하면:
    RefundOutbox.status = PENDING
    retryCount 증가
    nextAttemptAt 갱신

재시도 초과하면:
    RefundOutbox.status = FAILED
    Refund.status = PG_RESULT_UNKNOWN 유지
    OrderItem 예약 수량 유지
    포인트 회수 예약 유지
    새 환불 요청 차단
    운영자 확인 필요
```

즉, `PG_RESULT_UNKNOWN` 상태를 자동으로 `FAILED`로 정리하지 않는다.

PG 취소 성공 여부가 불명확한 상태에서 예약 수량을 해제하면 중복 환불 위험이 있기 때문이다.

따라서 재시도 초과 후에는 자동 처리를 멈추고, 운영자가 PortOne 관리자 콘솔 또는 취소 상태 조회 API를 통해 실제 취소 결과를 확인해야 한다.

---

## 6. 결과

수정 후에는 다음 기준을 명확히 했다.

```text
1. PG_RESULT_UNKNOWN은 실패가 아니라 결과 미확정 상태로 본다.
2. 재시도 초과 후 Outbox는 FAILED로 멈춘다.
3. Refund는 PG_RESULT_UNKNOWN으로 유지한다.
4. OrderItem 예약 수량은 해제하지 않는다.
5. 포인트 회수 예약도 해제하지 않는다.
6. 동일 결제에 대한 새 환불 요청은 계속 차단한다.
7. 운영자가 PortOne의 실제 취소 결과를 확인한 뒤 수동으로 정리해야 한다.
```

처음에는 새 환불 요청이 계속 막히는 것을 문제로 보고 자동 FAILED 처리를 고려했다.

하지만 `PG_RESULT_UNKNOWN` 상태에서 가장 위험한 문제는 "환불 요청이 막히는 것"보다 "이미 PG 취소가 성공했을 수 있는 건을 다시 환불하는 것"이었다.

따라서 최종적으로는 사용자 재요청을 열어주는 것보다 중복 환불을 방지하는 방향을 선택했다.

---

## 7. 배운 점

이번 문제를 통해 Outbox는 "작업 실행 상태"를 관리하는 역할이고, Refund는 "환불 도메인 상태"를 관리하는 역할이라는 점을 다시 정리했다.

Outbox가 FAILED가 되었다고 해서 Refund도 반드시 FAILED가 되어야 하는 것은 아니다.

특히 외부 PG와 통신하는 환불 로직에서는 서버가 인지한 결과와 실제 PG 처리 결과가 다를 수 있다.

예를 들어 서버는 타임아웃을 받았지만, 실제 PG 취소는 성공했을 수 있다.

이런 상황에서 서버 내부 상태만 보고 환불을 자동 실패 처리하면 중복 환불 위험이 생긴다.

이번 케이스를 통해 비동기 재시도 로직에서는 단순히 "재시도 횟수를 제한했다"로 끝나면 안 된다는 점을 배웠다.

재시도 실패 후에는 다음을 함께 고려해야 한다.

```text
- Outbox 상태
- Refund 상태
- OrderItem 예약 수량
- 포인트 회수 예약
- PG 실제 취소 결과
- 새 환불 요청 차단 조건
- 운영자 확인 흐름
```

처음에는 비동기 처리라는 개념이 낯설고 어려웠다.

내가 설계한 비동기 처리는 Outbox를 스케줄러가 잡고, exponential backoff + jitter 방식으로 재시도하면서 PortOne에 취소 요청을 보내는 구조였다.

하지만 외부 PG와 연결된 비동기 처리에서는 "자동 재시도"만 중요한 것이 아니라, 재시도 한도를 초과했을 때 도메인 상태를 어떻게 보존하고 운영자가 어떻게 확인할 수 있게 할지도 중요하다는 점을 알게 되었다.

---

## 8. 추가로 고려할 점

이번 문제를 정리하면서 처음에는 재시도 초과 시 Refund를 FAILED로 바꾸고, OrderItem 예약 수량과 포인트 회수 예약을 해제하는 방향을 생각했다.

하지만 최종적으로는 `PG_RESULT_UNKNOWN` 상태를 자동으로 FAILED 처리하지 않기로 했다.

이유는 `PG_RESULT_UNKNOWN`이 "환불 실패"가 아니라 "우리 서버가 PG 취소 성공 여부를 확정하지 못한 상태"이기 때문이다.

예를 들어 서버는 타임아웃을 받았지만, PortOne에서는 실제 취소가 성공했을 수 있다.

이 상태에서 서버가 자동으로 Refund를 FAILED 처리하고 예약 수량을 해제하면, 사용자는 같은 상품에 대해 새 환불 요청을 다시 보낼 수 있다.

그 경우 이미 PG에서 취소된 건에 대해 중복 환불이 발생할 수 있다.

따라서 현재 프로젝트에서는 재시도 초과 후 다음 상태를 유지하기로 했다.

```text
RefundOutbox.status = FAILED
Refund.status = PG_RESULT_UNKNOWN
OrderItem.refundReservedQuantity = 유지
포인트 회수 예약 = 유지
새 환불 요청 = 차단
```

이 상태는 자동 실패가 아니라 운영자 확인이 필요한 보류 상태로 본다.

운영자는 PortOne 관리자 콘솔 또는 취소 상태 조회 API를 통해 실제 취소 결과를 확인한 뒤, 환불 성공 처리 또는 실패 처리 여부를 수동으로 결정해야 한다.

실무에서는 이 흐름을 더 안전하게 운영하기 위해 다음 보완이 필요할 수 있다.

```text
- PortOne 취소 상태 조회 API를 통한 자동 대조
- 일정 시간 이후 정산/결제 내역 대조
- MANUAL_REVIEW 같은 별도 상태 추가
- 운영자 어드민 화면에서 PG_RESULT_UNKNOWN 환불 목록 확인
- 운영자 수동 성공/실패 처리 기능
- 처리 결과에 따라 예약 수량과 포인트 회수 예약을 정리하는 관리 기능
```

결국 이번 케이스에서 배운 점은, 비동기 재시도 로직에서는 "재시도 실패 후 무조건 도메인을 실패 처리한다"가 항상 정답은 아니라는 점이다.

특히 외부 PG처럼 실제 처리 결과와 서버가 인지한 결과가 어긋날 수 있는 시스템에서는, 자동 정리보다 중복 환불 방지가 더 중요한 경우가 있다.
