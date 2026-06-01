package com.teamec2.paymentsystem.global.pagination;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
