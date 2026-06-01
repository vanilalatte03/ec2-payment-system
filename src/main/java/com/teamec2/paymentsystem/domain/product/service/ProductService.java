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

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
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
