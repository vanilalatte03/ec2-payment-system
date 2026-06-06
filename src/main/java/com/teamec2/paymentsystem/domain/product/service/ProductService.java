package com.teamec2.paymentsystem.domain.product.service;

import com.teamec2.paymentsystem.domain.product.dto.ProductDetailResponse;
import com.teamec2.paymentsystem.domain.product.dto.ProductListResponse;
import com.teamec2.paymentsystem.domain.product.dto.ProductSearchCondition;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductSort;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.pagination.PageResponse;
import com.teamec2.paymentsystem.global.pagination.PageableFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    // 상품 목록 조회의 전체 흐름을 담당합니다.
    // 가격 조건 검증, 페이지 정보 생성, 검색 조건 적용, 응답 DTO 변환을 순서대로 처리합니다.
    public PageResponse<ProductListResponse> findProducts(ProductSearchCondition condition, ProductSort sort, int page, int size) {
        validatePriceRange(condition);

        Pageable pageable = createPageable(sort, page, size);
        Page<ProductListResponse> products = findProductsByCondition(condition, pageable)
                .map(this::toListResponse);

        return PageResponse.from(products);
    }

    // 상품 상세 조회 API에 필요한 응답을 만듭니다.
    // 상품이 없으면 getProductById에서 PRODUCT_NOT_FOUND 예외가 발생합니다.
    public ProductDetailResponse findProductDetail(Long productId) {
        Product product = getProductById(productId);

        return toDetailResponse(product);
    }

    // 다른 도메인에서도 상품 엔티티가 필요할 수 있어서 public으로 열어둔 조회 메서드입니다.
    // 단순 findById가 아니라, 없을 때 도메인 예외로 바꿔주는 역할까지 포함합니다.
    public Product getProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    // page, size, sort 값을 Spring Data JPA가 이해하는 Pageable 객체로 변환합니다.
    // page나 size가 잘못된 경우 PageableFactory에서 INVALID_PAGINATION 예외가 발생합니다.
    private Pageable createPageable(ProductSort sort, int page, int size) {
        return PageableFactory.create(page, size, sort.toSort());
    }

    // 검색 조건과 페이지 조건을 함께 적용해서 상품 엔티티 목록을 조회합니다.
    private Page<Product> findProductsByCondition(ProductSearchCondition condition, Pageable pageable) {
        return productRepository.findAll(createSearchSpecification(condition), pageable);
    }

    // 목록 화면에 필요한 상품 정보만 담은 DTO로 변환합니다.
    // 상세 설명과 updatedAt은 목록에서 사용하지 않기 때문에 제외합니다.
    private ProductListResponse toListResponse(Product product) {
        return new ProductListResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                product.getCategory().toString(),
                product.getStatus().toString(),
                product.getCreatedAt()
        );
    }

    // 상세 화면에 필요한 상품 정보를 DTO로 변환합니다.
    // 목록 응답과 달리 description, updatedAt까지 포함합니다.
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

    // 가격 검색 조건이 정상적인 범위인지 확인합니다.
    // null은 조건을 사용하지 않는다는 뜻이므로 검증 대상에서 제외합니다.
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

    // ProductSearchCondition을 JPA Specification으로 변환합니다.
    // Specification은 조건이 있을 때만 WHERE 절을 동적으로 추가할 때 사용합니다.
    private Specification<Product> createSearchSpecification(ProductSearchCondition condition) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            addCategoryCondition(predicates, root, criteriaBuilder, condition);
            addMinPriceCondition(predicates, root, criteriaBuilder, condition);
            addMaxPriceCondition(predicates, root, criteriaBuilder, condition);
            addStatusCondition(predicates, root, criteriaBuilder, condition);

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // 카테고리 조건이 있으면 category = ? 조건을 추가합니다.
    private void addCategoryCondition(
            List<Predicate> predicates,
            Root<Product> root,
            CriteriaBuilder criteriaBuilder,
            ProductSearchCondition condition
    ) {
        if (condition.category() != null) {
            predicates.add(criteriaBuilder.equal(root.get("category"), condition.category()));
        }
    }

    // 최소 가격 조건이 있으면 price >= ? 조건을 추가합니다.
    private void addMinPriceCondition(
            List<Predicate> predicates,
            Root<Product> root,
            CriteriaBuilder criteriaBuilder,
            ProductSearchCondition condition
    ) {
        if (condition.minPrice() != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), condition.minPrice()));
        }
    }

    // 최대 가격 조건이 있으면 price <= ? 조건을 추가합니다.
    private void addMaxPriceCondition(
            List<Predicate> predicates,
            Root<Product> root,
            CriteriaBuilder criteriaBuilder,
            ProductSearchCondition condition
    ) {
        if (condition.maxPrice() != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), condition.maxPrice()));
        }
    }

    // 판매 상태 조건이 있으면 status = ? 조건을 추가합니다.
    private void addStatusCondition(
            List<Predicate> predicates,
            Root<Product> root,
            CriteriaBuilder criteriaBuilder,
            ProductSearchCondition condition
    ) {
        if (condition.status() != null) {
            predicates.add(criteriaBuilder.equal(root.get("status"), condition.status()));
        }
    }
}
