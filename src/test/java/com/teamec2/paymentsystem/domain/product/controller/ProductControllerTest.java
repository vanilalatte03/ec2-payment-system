package com.teamec2.paymentsystem.domain.product.controller;

import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    private static final int BODY_STATUS = 200;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 목록을 필터링, 가격 오름차순 정렬, 페이지네이션하여 조회한다")
    void listProductsWithFilterSortAndPagination() throws Exception {
        productRepository.save(new Product(
                "오버핏 티셔츠", 39000, 10, "상의 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));
        productRepository.save(new Product(
                "코튼 반팔티", 9900, 5, "상의 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));
        productRepository.save(new Product(
                "와이드 팬츠", 68000, 3, "하의 상품", ProductStatus.ON_SALE, ProductCategory.BOTTOM
        ));

        mockMvc.perform(get("/api/products")
                        .param("category", "TOP")
                        .param("minPrice", "10000")
                        .param("status", "ON_SALE")
                        .param("sort", "PRICE_ASC")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("오버핏 티셔츠"))
                .andExpect(jsonPath("$.data.content[0].price").value(39000))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("상품 목록을 최신순으로 조회한다")
    void listProductsSortByLatest() throws Exception {
        productRepository.save(new Product(
                "먼저 등록된 상품", 10000, 10, "첫 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));
        productRepository.save(new Product(
                "나중에 등록된 상품", 20000, 10, "두 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));

        mockMvc.perform(get("/api/products")
                        .param("sort", "LATEST")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].name").value("나중에 등록된 상품"))
                .andExpect(jsonPath("$.data.content[1].name").value("먼저 등록된 상품"));
    }

    @Test
    @DisplayName("상품 목록을 가격 내림차순으로 조회한다")
    void listProductsSortByPriceDesc() throws Exception {
        productRepository.save(new Product(
                "저가 상품", 10000, 10, "낮은 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));
        productRepository.save(new Product(
                "고가 상품", 50000, 10, "높은 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));
        productRepository.save(new Product(
                "중간가 상품", 30000, 10, "중간 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));

        mockMvc.perform(get("/api/products")
                        .param("sort", "PRICE_DESC")
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].name").value("고가 상품"))
                .andExpect(jsonPath("$.data.content[1].name").value("중간가 상품"))
                .andExpect(jsonPath("$.data.content[2].name").value("저가 상품"));
    }

    @Test
    @DisplayName("다음 페이지가 있으면 hasNext가 true다")
    void listProductsHasNextTrue() throws Exception {
        productRepository.save(new Product(
                "첫 번째 상품", 10000, 10, "첫 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));
        productRepository.save(new Product(
                "두 번째 상품", 20000, 10, "두 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));
        productRepository.save(new Product(
                "세 번째 상품", 30000, 10, "세 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP
        ));

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("페이지 번호가 올바르지 않으면 원인 메시지를 반환한다")
    void listProductsFailWithInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().is(ErrorCode.INVALID_PAGINATION.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGINATION.name()))
                .andExpect(jsonPath("$.message").value("잘못된 페이지 번호입니다."));
    }

    @Test
    @DisplayName("페이지 크기가 올바르지 않으면 원인 메시지를 반환한다")
    void listProductsFailWithInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().is(ErrorCode.INVALID_PAGINATION.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGINATION.name()))
                .andExpect(jsonPath("$.message").value("잘못된 페이지 크기입니다."));
    }

    @Test
    @DisplayName("최소 가격이 음수면 원인 메시지를 반환한다")
    void listProductsFailWithNegativeMinPrice() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "-1"))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value("최소 가격은 0 이상이어야 합니다."));
    }

    @Test
    @DisplayName("최대 가격이 음수면 원인 메시지를 반환한다")
    void listProductsFailWithNegativeMaxPrice() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("maxPrice", "-1"))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value("최대 가격은 0 이상이어야 합니다."));
    }

    @Test
    @DisplayName("최소 가격이 최대 가격보다 크면 원인 메시지를 반환한다")
    void listProductsFailWithInvalidPriceRange() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "70000")
                        .param("maxPrice", "10000"))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value("최소 가격은 최대 가격보다 클 수 없습니다."));
    }

    @Test
    @DisplayName("잘못된 카테고리면 원인 메시지를 반환한다")
    void listProductsFailWithInvalidCategory() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("category", "INVALID_CATEGORY"))
                .andExpect(status().is(ErrorCode.INVALID_ENUM_VALUE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ENUM_VALUE.name()))
                .andExpect(jsonPath("$.message").value("잘못된 카테고리입니다."));
    }

    @Test
    @DisplayName("잘못된 판매 상태면 원인 메시지를 반환한다")
    void listProductsFailWithInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().is(ErrorCode.INVALID_ENUM_VALUE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ENUM_VALUE.name()))
                .andExpect(jsonPath("$.message").value("잘못된 판매 상태입니다."));
    }

    @Test
    @DisplayName("잘못된 정렬 조건이면 원인 메시지를 반환한다")
    void listProductsFailWithInvalidSort() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("sort", "INVALID_SORT"))
                .andExpect(status().is(ErrorCode.INVALID_ENUM_VALUE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ENUM_VALUE.name()))
                .andExpect(jsonPath("$.message").value("잘못된 정렬 조건입니다."));
    }
}
