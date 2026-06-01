package com.teamec2.paymentsystem.domain.product.controller;

import com.teamec2.paymentsystem.domain.product.dto.ProductDetailResponse;
import com.teamec2.paymentsystem.domain.product.dto.ProductResponse;
import com.teamec2.paymentsystem.domain.product.dto.ProductSearchCondition;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductSort;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.service.ProductService;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.pagination.PageResponse;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> productList(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        ProductSearchCondition condition = new ProductSearchCondition(
                parseEnum(category, ProductCategory.class, "잘못된 카테고리입니다."),
                minPrice,
                maxPrice,
                parseEnum(status, ProductStatus.class, "잘못된 판매 상태입니다.")
        );
        ProductSort productSort = parseEnum(sort, ProductSort.class, "잘못된 정렬 조건입니다.");

        return ResponseEntity.ok(ApiResponse.success(productService.findAll(condition, productSort, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> productDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.findById(id)));
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String errorMessage) {
        if (value == null) {
            return null;
        }

        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ENUM_VALUE, errorMessage);
        }
    }
}
