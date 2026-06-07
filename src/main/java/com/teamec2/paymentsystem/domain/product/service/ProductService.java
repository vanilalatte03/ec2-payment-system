package com.teamec2.paymentsystem.domain.product.service;

import com.teamec2.paymentsystem.domain.product.dto.ProductDetailResponse;
import com.teamec2.paymentsystem.domain.product.dto.ProductResponse;
import com.teamec2.paymentsystem.domain.product.dto.ProductSearchCondition;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductSort;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.pagination.PageResponse;
import com.teamec2.paymentsystem.global.pagination.PageableFactory;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public PageResponse<ProductResponse> findAll(ProductSearchCondition condition, ProductSort sort, int page, int size) {
        validatePriceRange(condition);

        Pageable pageable = PageableFactory.create(page, size, sort.toSort());
        Page<ProductResponse> products = productRepository.findAll(toSpecification(condition), pageable)
                .map(this::toResponse);

        return PageResponse.from(products);
    }

    public ProductDetailResponse findById(Long id) {
        Product product = findProductEntity(id);

        return toDetailResponse(product);
    }

    public Product findProductEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    /**
     * 상품 ID 목록으로 상품 row를 쓰기 잠금으로 조회한다.
     *
     * <p>주문 생성이나 보상 취소처럼 재고를 변경하는 흐름에서 사용한다.
     * 호출자는 {@link ProductRepository}를 직접 참조하지 않고 이 메서드를 통해
     * ID 오름차순으로 잠긴 상품을 가져온다.
     *
     * @param productIds 잠금으로 조회할 상품 ID 목록
     * @return 상품 ID를 key로 하는 잠금 획득 완료 상품 맵
     */
    @Transactional
    public Map<Long, Product> findProductsByIdsForUpdate(List<Long> productIds) {
        List<Long> distinctProductIds = distinctSortedProductIds(productIds);

        if (distinctProductIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Product> lockedProducts = productRepository.findAllByIdInForUpdate(distinctProductIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        if (lockedProducts.size() != distinctProductIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return lockedProducts;
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getCategory().toString(),
                product.getStatus().toString(),
                product.getCreatedAt()
        );
    }

    private ProductDetailResponse toDetailResponse(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getDescription(),
                product.getCategory().toString(),
                product.getStatus().toString(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private void validatePriceRange(ProductSearchCondition condition) {
        Integer minPrice = condition.minPrice();
        Integer maxPrice = condition.maxPrice();

        if (minPrice != null && minPrice < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "최소 가격은 0 이상이어야 합니다.");
        }

        if (maxPrice != null && maxPrice < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "최대 가격은 0 이상이어야 합니다.");
        }

        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "최소 가격은 최대 가격보다 클 수 없습니다.");
        }
    }

    /**
     * 상품 잠금 순서를 고정하기 위해 null이 아닌 상품 ID를 중복 제거 후 오름차순 정렬한다.
     *
     * @param productIds 원본 상품 ID 목록
     * @return 잠금 조회에 사용할 정렬된 상품 ID 목록
     */
    private List<Long> distinctSortedProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        return productIds.stream()
                .distinct()
                .sorted()
                .toList();
    }

    private Specification<Product> toSpecification(ProductSearchCondition condition) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (condition.category() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), condition.category()));
            }

            if (condition.minPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), condition.minPrice()));
            }

            if (condition.maxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), condition.maxPrice()));
            }

            if (condition.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), condition.status()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
