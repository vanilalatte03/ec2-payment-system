package com.teamec2.paymentsystem.domain.product.controller;

import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
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
    void 상품목록조회_상품이존재하면_기본정보와페이지정보를반환한다() throws Exception {
        // given
        상품_저장("오버핏 티셔츠", 39000, 10, "상의 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("와이드 팬츠", 68000, 3, "하의 상품", ProductStatus.ON_SALE, ProductCategory.BOTTOM);

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].productId").isNumber())
                .andExpect(jsonPath("$.data.content[0].name").exists())
                .andExpect(jsonPath("$.data.content[0].price").isNumber())
                .andExpect(jsonPath("$.data.content[0].stock").isNumber())
                .andExpect(jsonPath("$.data.content[0].category").exists())
                .andExpect(jsonPath("$.data.content[0].status").exists())
                .andExpect(jsonPath("$.data.content[0].createdAt").exists())
                .andExpect(jsonPath("$.data.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].updatedAt").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 상품목록조회_카테고리조건이있으면_해당카테고리상품만반환한다() throws Exception {
        // given
        상품_저장("오버핏 티셔츠", 39000, 10, "상의 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("와이드 팬츠", 68000, 3, "하의 상품", ProductStatus.ON_SALE, ProductCategory.BOTTOM);

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("category", "TOP")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("오버핏 티셔츠"))
                .andExpect(jsonPath("$.data.content[0].category").value("TOP"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 상품목록조회_가격범위조건이있으면_범위안의상품만반환한다() throws Exception {
        // given
        상품_저장("저가 상품", 9000, 10, "낮은 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("중간가 상품", 30000, 10, "중간 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("고가 상품", 70000, 10, "높은 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP);

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "10000")
                        .param("maxPrice", "50000")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("중간가 상품"))
                .andExpect(jsonPath("$.data.content[0].price").value(30000))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 상품목록조회_판매상태조건이있으면_해당상태상품만반환한다() throws Exception {
        // given
        상품_저장("판매중 상품", 30000, 10, "판매중 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("품절 상품", 40000, 0, "품절 상품", ProductStatus.SOLD_OUT, ProductCategory.TOP);

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("status", "SOLD_OUT")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("품절 상품"))
                .andExpect(jsonPath("$.data.content[0].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 상품목록조회_복합조건과가격오름차순정렬이있으면_조건에맞는상품을낮은가격순으로반환한다() throws Exception {
        // given
        상품_저장("조건 제외 저가 상품", 9000, 10, "가격 조건 제외", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("조건 포함 중간가 상품", 30000, 10, "조건 포함 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("조건 포함 고가 상품", 50000, 10, "조건 포함 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("상태 조건 제외 상품", 40000, 0, "품절 상품", ProductStatus.SOLD_OUT, ProductCategory.TOP);
        상품_저장("카테고리 조건 제외 상품", 20000, 10, "하의 상품", ProductStatus.ON_SALE, ProductCategory.BOTTOM);

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("category", "TOP")
                        .param("minPrice", "10000")
                        .param("maxPrice", "60000")
                        .param("status", "ON_SALE")
                        .param("sort", "PRICE_ASC")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].name").value("조건 포함 중간가 상품"))
                .andExpect(jsonPath("$.data.content[0].price").value(30000))
                .andExpect(jsonPath("$.data.content[1].name").value("조건 포함 고가 상품"))
                .andExpect(jsonPath("$.data.content[1].price").value(50000))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void 상품목록조회_최신순정렬이면_나중에등록된상품을먼저반환한다() throws Exception {
        // given
        상품_저장("먼저 등록된 상품", 10000, 10, "첫 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("나중에 등록된 상품", 20000, 10, "두 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP);

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("sort", "LATEST")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].name").value("나중에 등록된 상품"))
                .andExpect(jsonPath("$.data.content[1].name").value("먼저 등록된 상품"));
    }

    @Test
    void 상품목록조회_가격내림차순정렬이면_높은가격순으로반환한다() throws Exception {
        // given
        상품_저장("저가 상품", 10000, 10, "낮은 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("고가 상품", 50000, 10, "높은 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("중간가 상품", 30000, 10, "중간 가격 상품", ProductStatus.ON_SALE, ProductCategory.TOP);

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("sort", "PRICE_DESC")
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].name").value("고가 상품"))
                .andExpect(jsonPath("$.data.content[1].name").value("중간가 상품"))
                .andExpect(jsonPath("$.data.content[2].name").value("저가 상품"));
    }

    @Test
    void 상품목록조회_다음페이지가있으면_hasNext가true다() throws Exception {
        // given
        상품_저장("첫 번째 상품", 10000, 10, "첫 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("두 번째 상품", 20000, 10, "두 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP);
        상품_저장("세 번째 상품", 30000, 10, "세 번째 상품", ProductStatus.ON_SALE, ProductCategory.TOP);

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void 상품상세조회_존재하는상품이면_상세정보를반환한다() throws Exception {
        // given
        Product product = 상품_저장("오버핏 티셔츠", 39000, 10, "상세 설명입니다.", ProductStatus.ON_SALE, ProductCategory.TOP);

        // when
        // then
        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.name").value("오버핏 티셔츠"))
                .andExpect(jsonPath("$.data.price").value(39000))
                .andExpect(jsonPath("$.data.stock").value(10))
                .andExpect(jsonPath("$.data.description").value("상세 설명입니다."))
                .andExpect(jsonPath("$.data.category").value("TOP"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }

    @Test
    void 상품상세조회_존재하지않는상품이면_PRODUCT_NOT_FOUND를반환한다() throws Exception {
        // given
        long unknownProductId = 999999L;

        // when
        // then
        mockMvc.perform(get("/api/products/{id}", unknownProductId))
                .andExpect(status().is(ErrorCode.PRODUCT_NOT_FOUND.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_FOUND.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.PRODUCT_NOT_FOUND.getMessage()));
    }

    @Test
    void 상품목록조회_페이지번호가올바르지않으면_INVALID_PAGINATION을반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().is(ErrorCode.INVALID_PAGINATION.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGINATION.name()))
                .andExpect(jsonPath("$.message").value("잘못된 페이지 번호입니다."));
    }

    @Test
    void 상품목록조회_페이지크기가올바르지않으면_INVALID_PAGINATION을반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().is(ErrorCode.INVALID_PAGINATION.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGINATION.name()))
                .andExpect(jsonPath("$.message").value("잘못된 페이지 크기입니다."));
    }

    @Test
    void 상품목록조회_최소가격이음수면_VALIDATION_FAILED를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "-1"))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value("최소 가격은 0 이상이어야 합니다."));
    }

    @Test
    void 상품목록조회_최대가격이음수면_VALIDATION_FAILED를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("maxPrice", "-1"))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value("최대 가격은 0 이상이어야 합니다."));
    }

    @Test
    void 상품목록조회_최소가격이최대가격보다크면_VALIDATION_FAILED를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("minPrice", "70000")
                        .param("maxPrice", "10000"))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message").value("최소 가격은 최대 가격보다 클 수 없습니다."));
    }

    @Test
    void 상품목록조회_잘못된카테고리면_INVALID_ENUM_VALUE를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("category", "INVALID_CATEGORY"))
                .andExpect(status().is(ErrorCode.INVALID_ENUM_VALUE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ENUM_VALUE.name()))
                .andExpect(jsonPath("$.message").value("잘못된 카테고리입니다."));
    }

    @Test
    void 상품목록조회_잘못된판매상태면_INVALID_ENUM_VALUE를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().is(ErrorCode.INVALID_ENUM_VALUE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ENUM_VALUE.name()))
                .andExpect(jsonPath("$.message").value("잘못된 판매 상태입니다."));
    }

    @Test
    void 상품목록조회_잘못된정렬조건이면_INVALID_ENUM_VALUE를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/products")
                        .param("sort", "INVALID_SORT"))
                .andExpect(status().is(ErrorCode.INVALID_ENUM_VALUE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ENUM_VALUE.name()))
                .andExpect(jsonPath("$.message").value("잘못된 정렬 조건입니다."));
    }

    private Product 상품_저장(
            String name,
            int price,
            int stock,
            String description,
            ProductStatus status,
            ProductCategory category
    ) {
        return productRepository.save(new Product(name, price, stock, description, status, category));
    }
}
