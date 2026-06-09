package com.teamec2.paymentsystem.domain.cart.controller;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTest {

    private static final int BODY_STATUS = 200;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CartRepository cartRepository;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 장바구니상품담기_성공하면_저장된수량을확인할수있다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("오버핏 티셔츠", 39000, 10, ProductStatus.ON_SALE);

        // when
        // then
        mockMvc.perform(post("/api/carts/items")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 2
                                }
                                """.formatted(product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.cartItemId").isNumber())
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.productName").value("오버핏 티셔츠"))
                .andExpect(jsonPath("$.data.quantity").value(2))
                .andExpect(jsonPath("$.data.unitPrice").value(39000))
                .andExpect(jsonPath("$.data.lineAmount").value(78000))
                .andExpect(jsonPath("$.data.cartTotalAmount").value(78000));

        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElseThrow();

        assertThat(cartItem.getQuantity()).isEqualTo(2);
    }

    @Test
    void 장바구니상품담기_성공하면_장바구니에상품을저장한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("저장 테스트 상품", 10000, 10, ProductStatus.ON_SALE);

        // when
        장바구니상품_담기(accessToken(user), product.getId(), 1);

        // then
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElseThrow();

        assertThat(cartItem.getQuantity()).isEqualTo(1);
    }

    @Test
    void 장바구니상품담기_새상품이면_장바구니상품생성수정시각을저장한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("시간 테스트 상품", 10000, 10, ProductStatus.ON_SALE);

        // when
        장바구니상품_담기(accessToken(user), product.getId(), 1);

        // then
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElseThrow();

        assertThat(cartItem.getCreatedAt()).isNotNull();
        assertThat(cartItem.getUpdatedAt()).isNotNull();
    }

    @Test
    void 장바구니상품담기_같은상품이면_생성시각은유지하고수정시각만갱신한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("같은 상품 시간 테스트", 10000, 10, ProductStatus.ON_SALE);
        String accessToken = accessToken(user);

        장바구니상품_담기(accessToken, product.getId(), 1);

        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        CartItem firstCartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElseThrow();
        LocalDateTime firstCreatedAt = firstCartItem.getCreatedAt();
        LocalDateTime firstUpdatedAt = firstCartItem.getUpdatedAt();

        Thread.sleep(10);

        // when
        장바구니상품_담기(accessToken, product.getId(), 2);

        // then
        CartItem changedCartItem = cartItemRepository.findById(firstCartItem.getId()).orElseThrow();

        assertThat(changedCartItem.getCreatedAt()).isEqualTo(firstCreatedAt);
        assertThat(changedCartItem.getUpdatedAt()).isAfter(firstUpdatedAt);
    }

    @Test
    void 장바구니상품담기_같은상품이면_수량을합산한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("와이드 팬츠", 68000, 10, ProductStatus.ON_SALE);
        String accessToken = accessToken(user);

        장바구니상품_담기(accessToken, product.getId(), 2);

        // when
        장바구니상품_담기(accessToken, product.getId(), 3);

        // then
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        CartItem cartItem = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId()).orElseThrow();

        assertThat(cartItem.getQuantity()).isEqualTo(5);
    }

    @Test
    void 장바구니상품담기_합산수량이재고를초과하면_CART_STOCK_EXCEEDED를반환한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("스니커즈", 89000, 3, ProductStatus.ON_SALE);

        // when
        // then
        mockMvc.perform(post("/api/carts/items")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 4
                                }
                                """.formatted(product.getId())))
                .andExpect(status().is(ErrorCode.CART_STOCK_EXCEEDED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.CART_STOCK_EXCEEDED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.CART_STOCK_EXCEEDED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 장바구니상품담기_판매중이아닌상품이면_PRODUCT_NOT_ON_SALE을반환한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("품절 상품", 10000, 0, ProductStatus.SOLD_OUT);

        // when
        // then
        mockMvc.perform(post("/api/carts/items")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": 1
                                }
                                """.formatted(product.getId())))
                .andExpect(status().is(ErrorCode.PRODUCT_NOT_ON_SALE.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.PRODUCT_NOT_ON_SALE.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.PRODUCT_NOT_ON_SALE.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 장바구니조회_상품이있으면_상품목록과합계금액을반환한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("후드 집업", 55000, 10, ProductStatus.ON_SALE);
        장바구니상품_담기(accessToken(user), product.getId(), 2);

        // when
        // then
        mockMvc.perform(get("/api/carts")
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.cartId").isNumber())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].cartItemId").isNumber())
                .andExpect(jsonPath("$.data.items[0].productId").value(product.getId()))
                .andExpect(jsonPath("$.data.items[0].productName").value("후드 집업"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(55000))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(110000))
                .andExpect(jsonPath("$.data.items[0].stock").value(10))
                .andExpect(jsonPath("$.data.items[0].status").value(ProductStatus.ON_SALE.name()))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(110000));
    }

    @Test
    void 장바구니조회_상품이없으면_빈목록과장바구니식별자를반환한다() throws Exception {
        // given
        User user = 회원_저장();

        // when
        // then
        mockMvc.perform(get("/api/carts")
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.cartId").isNumber())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalQuantity").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(0));
    }

    @Test
    void 장바구니수량변경_본인장바구니상품이면_수량을변경한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("셔츠", 42000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 2);

        // when
        // then
        mockMvc.perform(patch("/api/carts/items/{cartItemId}", cartItem.getId())
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.cartItemId").value(cartItem.getId()))
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.quantity").value(4))
                .andExpect(jsonPath("$.data.unitPrice").value(42000))
                .andExpect(jsonPath("$.data.lineAmount").value(168000))
                .andExpect(jsonPath("$.data.cartTotalAmount").value(168000));

        CartItem changedCartItem = cartItemRepository.findById(cartItem.getId()).orElseThrow();
        assertThat(changedCartItem.getQuantity()).isEqualTo(4);
    }

    @Test
    void 장바구니수량변경_두부만수량을줄이면_애호박은그대로유지된다() throws Exception {
        // given
        User user = 회원_저장();
        Product tofu = 상품_저장("두부", 2000, 10, ProductStatus.ON_SALE);
        Product zucchini = 상품_저장("애호박", 1500, 10, ProductStatus.ON_SALE);
        CartItem tofuCartItem = 장바구니상품_저장(user, tofu, 2);
        CartItem zucchiniCartItem = 장바구니상품_저장(user, zucchini, 1);

        // when
        // then
        mockMvc.perform(patch("/api/carts/items/{cartItemId}", tofuCartItem.getId())
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.cartItemId").value(tofuCartItem.getId()))
                .andExpect(jsonPath("$.data.productId").value(tofu.getId()))
                .andExpect(jsonPath("$.data.productName").value("두부"))
                .andExpect(jsonPath("$.data.quantity").value(1))
                .andExpect(jsonPath("$.data.lineAmount").value(2000))
                .andExpect(jsonPath("$.data.cartTotalAmount").value(3500));

        CartItem changedTofu = cartItemRepository.findById(tofuCartItem.getId()).orElseThrow();
        CartItem unchangedZucchini = cartItemRepository.findById(zucchiniCartItem.getId()).orElseThrow();

        assertThat(changedTofu.getQuantity()).isEqualTo(1);
        assertThat(unchangedZucchini.getQuantity()).isEqualTo(1);
    }

    @Test
    void 장바구니수량변경_재고를초과하면_CART_STOCK_EXCEEDED를반환하고_수량을바꾸지않는다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("재고 제한 상품", 15000, 3, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 2);

        // when
        // then
        mockMvc.perform(patch("/api/carts/items/{cartItemId}", cartItem.getId())
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 4
                                }
                                """))
                .andExpect(status().is(ErrorCode.CART_STOCK_EXCEEDED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.CART_STOCK_EXCEEDED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.CART_STOCK_EXCEEDED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());

        CartItem unchangedCartItem = cartItemRepository.findById(cartItem.getId()).orElseThrow();
        assertThat(unchangedCartItem.getQuantity()).isEqualTo(2);
    }

    @Test
    void 장바구니수량변경_다른상품이있으면_전체합계금액을함께반환한다() throws Exception {
        // given
        User user = 회원_저장();
        Product firstProduct = 상품_저장("셔츠", 42000, 10, ProductStatus.ON_SALE);
        Product secondProduct = 상품_저장("데님 팬츠", 76000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, firstProduct, 2);
        장바구니상품_저장(user, secondProduct, 2);

        // when
        // then
        mockMvc.perform(patch("/api/carts/items/{cartItemId}", cartItem.getId())
                        .header("Authorization", "Bearer " + accessToken(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 4
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.cartItemId").value(cartItem.getId()))
                .andExpect(jsonPath("$.data.lineAmount").value(168000))
                .andExpect(jsonPath("$.data.cartTotalAmount").value(320000));
    }

    @Test
    void 장바구니수량변경_타인장바구니상품이면_CART_ITEM_ACCESS_DENIED를반환한다() throws Exception {
        // given
        User owner = 회원_저장();
        User otherUser = 회원_저장();
        Product product = 상품_저장("니트", 49000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(owner, product, 2);

        // when
        // then
        mockMvc.perform(patch("/api/carts/items/{cartItemId}", cartItem.getId())
                        .header("Authorization", "Bearer " + accessToken(otherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 3
                                }
                                """))
                .andExpect(status().is(ErrorCode.CART_ITEM_ACCESS_DENIED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.CART_ITEM_ACCESS_DENIED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.CART_ITEM_ACCESS_DENIED.getMessage()));
    }

    @Test
    void 장바구니상품삭제_본인장바구니상품이면_삭제한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("가디건", 59000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, product, 2);

        // when
        // then
        mockMvc.perform(delete("/api/carts/items/{cartItemId}", cartItem.getId())
                .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.cartItemId").value(cartItem.getId()))
                .andExpect(jsonPath("$.data.cartTotalAmount").value(0));

        assertThat(cartItemRepository.findById(cartItem.getId())).isEmpty();
    }

    @Test
    void 장바구니상품삭제_다른상품이남아있으면_남은상품합계금액을반환한다() throws Exception {
        // given
        User user = 회원_저장();
        Product firstProduct = 상품_저장("가디건", 59000, 10, ProductStatus.ON_SALE);
        Product secondProduct = 상품_저장("맨투맨", 39000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품_저장(user, firstProduct, 2);
        장바구니상품_저장(user, secondProduct, 3);

        // when
        // then
        mockMvc.perform(delete("/api/carts/items/{cartItemId}", cartItem.getId())
                .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.cartItemId").value(cartItem.getId()))
                .andExpect(jsonPath("$.data.cartTotalAmount").value(117000));

        assertThat(cartItemRepository.findById(cartItem.getId())).isEmpty();
    }

    @Test
    void 장바구니전체비우기_장바구니가있으면_모든상품을삭제한다() throws Exception {
        // given
        User user = 회원_저장();
        Product firstProduct = 상품_저장("맨투맨", 39000, 10, ProductStatus.ON_SALE);
        Product secondProduct = 상품_저장("데님 팬츠", 76000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, firstProduct, 1);
        장바구니상품_저장(user, secondProduct, 2);

        // when
        // then
        mockMvc.perform(delete("/api/carts")
                .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.deletedCount").value(2));

        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(cartItemRepository.findAllWithProductByCartId(cart.getId())).isEmpty();
    }

    @Test
    void 장바구니전체비우기_성공하면_장바구니상품을모두삭제한다() throws Exception {
        // given
        User user = 회원_저장();
        Product product = 상품_저장("전체 삭제 상품", 39000, 10, ProductStatus.ON_SALE);
        장바구니상품_저장(user, product, 1);

        // when
        mockMvc.perform(delete("/api/carts")
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk());

        // then
        Cart cart = cartRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(cartItemRepository.findAllByCartId(cart.getId())).isEmpty();
    }

    @Test
    void 장바구니API_토큰이없으면_UNAUTHORIZED를반환한다() throws Exception {
        // given

        // when
        // then
        mockMvc.perform(get("/api/carts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void 장바구니상품_담기(String accessToken, Long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/carts/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d,
                                  "quantity": %d
                                }
                                """.formatted(productId, quantity)))
                .andExpect(status().isCreated());
    }

    private CartItem 장바구니상품_저장(User user, Product product, int quantity) {
        Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow();

        return cartItemRepository.save(new CartItem(cart, product, quantity));
    }

    private User 회원_저장() {
        User user = userRepository.save(User.create(uniqueEmail(), "Password123!", "홍길동", "010-1234-5678"));
        cartRepository.save(new Cart(user));
        return user;
    }

    private Product 상품_저장(String name, int price, int stock, ProductStatus status) {
        return productRepository.save(new Product(name, price, stock, "테스트 상품입니다.", status, ProductCategory.TOP));
    }

    private String accessToken(User user) {
        return jwtTokenProvider.createAccessToken(user.getId());
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
