package com.teamec2.paymentsystem.global.pagination;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Controller: page=0, size=10 수신
 * Service: 정렬 조건 결정
 * PageableFactory: 값 검증 후 pageRequest 생성
 * Repository: Page 반환
 * PageResponse.from(): API 응답 형태로 변환
 */

public final class PageableFactory {

    private static final int MAX_SIZE = 100;

    private PageableFactory() {
    }

    public static Pageable create(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_PAGINATION, "잘못된 페이지 번호입니다.");
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_PAGINATION, "잘못된 페이지 크기입니다.");
        }

        return PageRequest.of(page, size, sort);
    }
}
