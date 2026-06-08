const STORAGE = {
  token: "ec2ShopAccessToken",
  user: "ec2ShopUser",
};

const CATEGORY_LABELS = {
  TOP: "상의",
  BOTTOM: "하의",
  OUTER: "아우터",
  BAG: "가방",
  SHOES: "신발",
  ACCESSORY: "액세서리",
};

const STATUS_LABELS = {
  ON_SALE: "재고 있음",
  SOLD_OUT: "품절",
};

const PRODUCT_IMAGES = {
  10001: "assets/products/10001-cotton-twill-cap.jpg",
  10002: "assets/products/10002-striped-socks-3pack.jpg",
  10003: "assets/products/10003-oversized-crewneck-tshirt.jpg",
  10004: "assets/products/10004-washed-wide-denim-pants.jpg",
  10005: "assets/products/10005-canvas-mini-eco-bag.jpg",
  10006: "assets/products/10006-lambswool-oversized-knit.jpg",
  10007: "assets/products/10007-nylon-cargo-jogger-pants.jpg",
  10008: "assets/products/10008-leather-minimal-crossbody-bag.jpg",
  10009: "assets/products/10009-chunky-running-sneakers.jpg",
  10010: "assets/products/10010-wool-blend-single-coat.jpg",
  10011: "assets/products/10011-stretch-slim-chino-pants.jpg",
  10012: "assets/products/10012-cotton-waffle-tee.jpg",
};

const LOCAL_PRODUCTS = [
  {
    productId: 10001,
    name: "코튼 트윌 볼캡",
    price: 5000,
    stock: 200,
    description: "데일리 필수템 코튼 트윌 볼캡. 가볍고 편안한 착용감으로 어떤 코디에도 포인트.",
    status: "ON_SALE",
    category: "ACCESSORY",
  },
  {
    productId: 10002,
    name: "베이직 스트라이프 삭스 3팩",
    price: 6900,
    stock: 300,
    description: "데일리 필수 스트라이프 양말 3켤레 세트. 코튼 혼방으로 부드럽고 통기성 우수.",
    status: "ON_SALE",
    category: "ACCESSORY",
  },
  {
    productId: 10003,
    name: "오버핏 코튼 크루넥 티셔츠",
    price: 39000,
    stock: 120,
    description: "부드러운 코튼 소재의 오버핏 크루넥 티셔츠. 데일리로 입기 좋은 베이직 아이템.",
    status: "ON_SALE",
    category: "TOP",
  },
  {
    productId: 10004,
    name: "워시드 와이드 데님 팬츠",
    price: 68000,
    stock: 45,
    description: "빈티지 워싱 처리된 와이드 핏 데님. 편안한 핏감과 트렌디한 실루엣.",
    status: "ON_SALE",
    category: "BOTTOM",
  },
  {
    productId: 10005,
    name: "캔버스 미니 에코백",
    price: 8900,
    stock: 150,
    description: "가벼운 캔버스 소재의 미니 에코백. 간단한 외출과 서브백으로 활용도 만점.",
    status: "ON_SALE",
    category: "BAG",
  },
  {
    productId: 10006,
    name: "램스울 오버사이즈 니트",
    price: 89000,
    stock: 30,
    description: "부드러운 램스울 혼방 니트. 여유로운 오버사이즈 핏으로 레이어드에 최적.",
    status: "ON_SALE",
    category: "TOP",
  },
  {
    productId: 10007,
    name: "나일론 카고 조거팬츠",
    price: 58000,
    stock: 60,
    description: "경량 나일론 소재의 카고 조거팬츠. 스트릿 무드의 실용적인 디자인.",
    status: "ON_SALE",
    category: "BOTTOM",
  },
  {
    productId: 10008,
    name: "레더 미니멀 크로스백",
    price: 128000,
    stock: 20,
    description: "소프트 레더 소재의 미니멀 크로스백. 깔끔한 디자인으로 다양한 코디에 매치.",
    status: "ON_SALE",
    category: "BAG",
  },
  {
    productId: 10009,
    name: "청키 러닝 스니커즈",
    price: 159000,
    stock: 25,
    description: "볼드한 청키 솔의 러닝 스니커즈. 뛰어난 쿠셔닝과 스타일리시한 디자인.",
    status: "ON_SALE",
    category: "SHOES",
  },
  {
    productId: 10010,
    name: "울 블렌드 싱글 코트",
    price: 198000,
    stock: 0,
    description: "고급 울 블렌드 소재의 싱글 코트. 클래식한 실루엣으로 격식있는 룩 완성.",
    status: "SOLD_OUT",
    category: "OUTER",
  },
  {
    productId: 10011,
    name: "스트레치 슬림 치노팬츠",
    price: 48000,
    stock: 80,
    description: "스트레치 소재로 편안한 슬림 치노팬츠. 오피스부터 캐주얼까지 활용도 높은 아이템.",
    status: "ON_SALE",
    category: "BOTTOM",
  },
  {
    productId: 10012,
    name: "코튼 와플 반팔티",
    price: 9900,
    stock: 180,
    description: "와플 조직의 코튼 반팔티. 은은한 텍스처감으로 심플하지만 디테일이 살아있는 아이템.",
    status: "ON_SALE",
    category: "TOP",
  },
];

const state = {
  token: localStorage.getItem(STORAGE.token),
  user: readStoredUser(),
  products: [],
  productPage: null,
  cart: null,
  pointBalance: 0,
  currentPage: 0,
  loading: false,
};

const els = {};

document.addEventListener("DOMContentLoaded", () => {
  cacheElements();
  bindEvents();
  renderSession();
  loadProducts(0);
  if (state.token) {
    refreshCart(false);
    refreshAccount(false);
  }
});

function cacheElements() {
  [
    "productGrid",
    "productTitle",
    "productMeta",
    "pageMeta",
    "prevPageBtn",
    "nextPageBtn",
    "categoryFilter",
    "statusFilter",
    "sortFilter",
    "searchInput",
    "productDrawer",
    "productDetail",
    "cartDrawer",
    "cartItems",
    "cartCount",
    "cartSubtotal",
    "usePointInput",
    "pointHint",
    "checkoutTotal",
    "authDialog",
    "loginEmail",
    "loginPassword",
    "signupName",
    "signupEmail",
    "signupPassword",
    "signupPhone",
    "authTabs",
    "loginPane",
    "signupPane",
    "authAccountPane",
    "authUserName",
    "authUserEmail",
    "authUserId",
    "authPointBalance",
    "logoutButton",
    "pointTransactions",
    "toast",
  ].forEach((id) => {
    els[id] = document.getElementById(id);
  });
}

function bindEvents() {
  document.addEventListener("click", handleClick);
  document.addEventListener("input", handleInput);
  document.addEventListener("change", handleChange);

  els.categoryFilter.addEventListener("change", () => {
    updateCategoryNav();
    loadProducts(0);
  });

  [els.statusFilter, els.sortFilter].forEach((el) => {
    el.addEventListener("change", () => loadProducts(0));
  });

  els.searchInput.addEventListener("input", () => renderProducts());
  els.prevPageBtn.addEventListener("click", () => loadProducts(state.currentPage - 1));
  els.nextPageBtn.addEventListener("click", () => loadProducts(state.currentPage + 1));

  [els.productDrawer, els.cartDrawer].forEach((drawer) => {
    drawer.addEventListener("click", (event) => {
      if (event.target === drawer) {
        closeDrawers();
      }
    });
  });
}

async function handleClick(event) {
  const actionTarget = event.target.closest("[data-action]");
  if (actionTarget) {
    const action = actionTarget.dataset.action;
    if (action === "scroll-products") return scrollToSection("products");
    if (action === "scroll-account") return scrollToSection("account");
    if (action === "focus-search") return focusSearch();
    if (action === "open-auth") return openAuthDialog();
    if (action === "open-cart") return openCart();
    if (action === "close-cart") return closeDrawer(els.cartDrawer);
    if (action === "close-product") return closeDrawer(els.productDrawer);
    if (action === "refresh-account") return refreshAccount();
    if (action === "login") return login();
    if (action === "signup") return signup();
    if (action === "logout") return logout();
    if (action === "detail-qty-minus") return adjustDetailQuantity(-1);
    if (action === "detail-qty-plus") return adjustDetailQuantity(1);
    if (action === "add-detail-cart") return addDetailProductToCart();
    if (action === "remove-cart-item") return deleteCartItem(Number(actionTarget.dataset.cartItemId));
    if (action === "cart-qty") return updateCartQuantity(actionTarget);
    if (action === "use-all-points") return useAllPoints();
    if (action === "create-order") return createOrder();
    if (action === "clear-cart") return clearCart();
  }

  const categoryTarget = event.target.closest("[data-category-nav]");
  if (categoryTarget) {
    els.categoryFilter.value = categoryTarget.dataset.categoryNav;
    updateCategoryNav();
    scrollToSection("products");
    return loadProducts(0);
  }

  const productTarget = event.target.closest("[data-product-id]");
  if (productTarget) {
    return openProductDetail(Number(productTarget.dataset.productId));
  }

  const authTab = event.target.closest("[data-auth-tab]");
  if (authTab) {
    return switchAuthTab(authTab.dataset.authTab);
  }
}

function handleInput(event) {
  if (event.target === els.usePointInput) {
    updateCheckoutTotal();
  }
}

function handleChange(event) {
  const input = event.target.closest("[data-cart-qty-input]");
  if (!input) return;
  const cartItemId = Number(input.dataset.cartItemId);
  const quantity = Math.max(1, Number(input.value || 1));
  updateCartItem(cartItemId, quantity);
}

async function api(path, options = {}) {
  const headers = {
    Accept: "application/json",
    ...(options.headers || {}),
  };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (options.auth !== false && state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  const response = await fetch(path, {
    method: options.method || "GET",
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  let payload = null;
  try {
    payload = await response.json();
  } catch (_) {
    payload = null;
  }

  if (!response.ok || payload?.code) {
    const error = new Error(payload?.message || `요청 실패 (${response.status})`);
    error.code = payload?.code;
    error.status = response.status;
    throw error;
  }

  return payload?.data;
}

async function loadProducts(page = 0) {
  if (state.loading || page < 0) return;
  state.loading = true;
  state.currentPage = page;
  els.productGrid.innerHTML = `<div class="empty-state">상품을 불러오는 중입니다.</div>`;

  const params = new URLSearchParams({
    page: String(page),
    size: "12",
    sort: els.sortFilter.value || "LATEST",
  });

  appendParam(params, "category", els.categoryFilter.value);
  appendParam(params, "status", els.statusFilter.value);

  try {
    const data = await api(`/api/products?${params.toString()}`, { auth: false });
    state.productPage = data.totalElements === 0 ? localProductPage(page) : data;
    state.products = data.content || [];
    if (data.totalElements === 0) {
      state.products = state.productPage.content;
    }
    renderProducts();
  } catch (error) {
    els.productMeta.textContent = "상품 목록을 불러오지 못했습니다.";
    els.productGrid.innerHTML = `<div class="error-state">${escapeHtml(formatError(error))}</div>`;
    showToast(formatError(error), "error");
  } finally {
    state.loading = false;
  }
}

function renderProducts() {
  updateProductHeading();

  const keyword = els.searchInput.value.trim().toLowerCase();
  const products = keyword
    ? state.products.filter((product) => product.name.toLowerCase().includes(keyword))
    : state.products;

  if (!products.length) {
    els.productGrid.innerHTML = `<div class="empty-state">조건에 맞는 상품이 없습니다.</div>`;
  } else {
    els.productGrid.innerHTML = products.map(renderProductCard).join("");
  }

  const page = state.productPage;
  if (page) {
    els.productMeta.textContent = `${page.totalElements}개의 상품`;
    els.pageMeta.textContent = `${page.page + 1} / ${Math.max(page.totalPages, 1)}`;
    els.prevPageBtn.disabled = page.page <= 0;
    els.nextPageBtn.disabled = !page.hasNext;
  }
}

function renderProductCard(product) {
  const soldOut = product.status === "SOLD_OUT" || product.stock <= 0;
  const categoryLabel = CATEGORY_LABELS[product.category] || product.category;
  return `
    <button class="product-card" type="button" data-product-id="${product.productId}">
      <span class="product-image-frame">
        <img src="${productImage(product.productId)}" alt="${escapeHtml(product.name)}" />
      </span>
      <span class="product-card-body">
        <span class="product-badge${soldOut ? " is-sold-out" : ""}">${soldOut ? "품절" : categoryLabel}</span>
        <h3 class="product-name">${escapeHtml(product.name)}</h3>
        <p class="product-price">${formatCurrency(product.price)}</p>
      </span>
    </button>
  `;
}

async function openProductDetail(productId) {
  els.productDetail.innerHTML = `<div class="empty-state">상품 상세를 불러오는 중입니다.</div>`;
  openDrawer(els.productDrawer);
  if (state.productPage?.localFallback) {
    const localProduct = LOCAL_PRODUCTS.find((product) => product.productId === productId);
    if (localProduct) {
      renderProductDetail(localProduct);
      return;
    }
  }
  try {
    const product = await api(`/api/products/${productId}`, { auth: false });
    renderProductDetail(product);
  } catch (error) {
    const localProduct = LOCAL_PRODUCTS.find((product) => product.productId === productId);
    if (localProduct) {
      renderProductDetail(localProduct);
      return;
    }
    els.productDetail.innerHTML = `<div class="error-state">${escapeHtml(formatError(error))}</div>`;
  }
}

function renderProductDetail(product) {
  const soldOut = product.status === "SOLD_OUT" || product.stock <= 0;
  els.productDetail.dataset.productId = product.productId;
  els.productDetail.innerHTML = `
    <div class="detail-layout">
      <div class="detail-gallery">
        <div class="detail-main-image">
          <img src="${productImage(product.productId)}" alt="${escapeHtml(product.name)}" />
        </div>
      </div>
      <div class="detail-info">
        <p class="breadcrumb">컬렉션 / ${CATEGORY_LABELS[product.category] || product.category}</p>
        <h2>${escapeHtml(product.name)}</h2>
        <p class="detail-price">${formatCurrency(product.price)}</p>
        <p class="detail-description">${escapeHtml(product.description || "")}</p>
        <div class="quantity-row">
          <span>수량</span>
          <div class="quantity-control">
            <button type="button" data-action="detail-qty-minus">-</button>
            <input id="detailQuantity" type="number" min="1" max="${Math.max(product.stock, 1)}" value="1" />
            <button type="button" data-action="detail-qty-plus">+</button>
          </div>
        </div>
        <div class="detail-actions">
          <button class="primary-button full" type="button" data-action="add-detail-cart" ${soldOut ? "disabled" : ""}>장바구니 담기</button>
        </div>
      </div>
    </div>
  `;
}

async function addDetailProductToCart() {
  const productId = Number(els.productDetail.dataset.productId);
  const quantityInput = document.getElementById("detailQuantity");
  const quantity = Math.max(1, Number(quantityInput?.value || 1));
  await addCartItem(productId, quantity);
}

function adjustDetailQuantity(delta) {
  const input = document.getElementById("detailQuantity");
  if (!input) return;
  const min = Number(input.min || 1);
  const max = Number(input.max || 99);
  const next = Math.max(min, Math.min(max, Number(input.value || min) + delta));
  input.value = String(next);
}

async function addCartItem(productId, quantity, pendingMessage) {
  if (!requireLogin()) return;
  if (pendingMessage) showToast(pendingMessage);
  try {
    const result = await api("/api/carts/items", {
      method: "POST",
      body: { productId, quantity },
    });
    showToast(`${result.productName} ${result.quantity}개가 장바구니에 담겼습니다.`);
    await refreshCart(false);
  } catch (error) {
    showToast(formatError(error), "error");
  }
}

async function refreshCart(showErrors = true) {
  if (!state.token) {
    state.cart = null;
    renderCart();
    return;
  }

  try {
    state.cart = await api("/api/carts");
    renderCart();
  } catch (error) {
    if (showErrors) showToast(formatError(error), "error");
    if (error.status === 401) {
      clearSession();
      renderSession();
    }
    renderCart();
  }
}

function renderCart() {
  const cart = state.cart;
  const count = cart?.totalQuantity || 0;
  els.cartCount.textContent = String(count);
  els.cartSubtotal.textContent = formatCurrency(cart?.totalAmount || 0);

  if (!state.token) {
    els.cartItems.innerHTML = `<div class="empty-state">로그인이 필요합니다.</div>`;
  } else if (!cart?.items?.length) {
    els.cartItems.innerHTML = `<div class="empty-state">장바구니가 비어 있습니다.</div>`;
  } else {
    els.cartItems.innerHTML = cart.items.map(renderCartItem).join("");
  }

  updateCheckoutTotal();
}

function renderCartItem(item) {
  return `
    <article class="cart-item">
      <img src="${productImage(item.productId)}" alt="${escapeHtml(item.productName)}" />
      <div>
        <h3>${escapeHtml(item.productName)}</h3>
        <p>${formatCurrency(item.unitPrice)}</p>
        <div class="quantity-control">
          <button type="button" data-action="cart-qty" data-cart-item-id="${item.cartItemId}" data-next-quantity="${item.quantity - 1}">-</button>
          <input type="number" min="1" max="${item.stock}" value="${item.quantity}" data-cart-qty-input data-cart-item-id="${item.cartItemId}" />
          <button type="button" data-action="cart-qty" data-cart-item-id="${item.cartItemId}" data-next-quantity="${item.quantity + 1}">+</button>
        </div>
      </div>
      <button class="icon-button" type="button" data-action="remove-cart-item" data-cart-item-id="${item.cartItemId}" aria-label="삭제">
        <span class="material-symbols-outlined">close</span>
      </button>
    </article>
  `;
}

function updateCartQuantity(button) {
  const cartItemId = Number(button.dataset.cartItemId);
  const nextQuantity = Number(button.dataset.nextQuantity);
  if (nextQuantity < 1) {
    return deleteCartItem(cartItemId);
  }
  return updateCartItem(cartItemId, nextQuantity);
}

async function updateCartItem(cartItemId, quantity) {
  try {
    await api(`/api/carts/items/${cartItemId}`, {
      method: "PATCH",
      body: { quantity },
    });
    await refreshCart(false);
  } catch (error) {
    showToast(formatError(error), "error");
    await refreshCart(false);
  }
}

async function deleteCartItem(cartItemId) {
  try {
    await api(`/api/carts/items/${cartItemId}`, { method: "DELETE" });
    await refreshCart(false);
    showToast("상품을 삭제했습니다.");
  } catch (error) {
    showToast(formatError(error), "error");
  }
}

async function clearCart() {
  if (!requireLogin()) return;
  try {
    await api("/api/carts", { method: "DELETE" });
    await refreshCart(false);
    showToast("장바구니를 비웠습니다.");
  } catch (error) {
    showToast(formatError(error), "error");
  }
}

async function createOrder() {
  if (!requireLogin()) return;
  if (!state.cart?.items?.length) {
    showToast("장바구니에 상품이 없습니다.", "error");
    return;
  }

  const usedPointAmount = normalizeUsePoint();
  const cartItemIds = state.cart.items.map((item) => item.cartItemId);

  try {
    const orderResult = await api("/api/orders", {
      method: "POST",
      body: { cartItemIds, usedPointAmount },
    });

    if (orderResult.nextAction === "CONFIRM_POINT_ONLY") {
      const confirmed = await confirmPayment(
        orderResult.order.orderId,
        orderResult.payment.portonePaymentId
      );
      showToast(`주문 ${confirmed.orderNumber} 결제가 완료되었습니다.`);
      await refreshCart(false);
      await refreshAccount(false);
      closeDrawer(els.cartDrawer);
      return;
    }

    const params = new URLSearchParams({
      orderId: String(orderResult.order.orderId),
      paymentId: orderResult.payment.portonePaymentId,
      amount: String(orderResult.payment.pgAmount),
      orderName: buildOrderName(orderResult.order.items),
      payerName: state.user?.name || "홍길동",
      payerEmail: state.user?.email || "test@example.com",
    });

    sessionStorage.setItem("ec2ShopPendingPayment", JSON.stringify(orderResult));
    window.location.href = `/payment.html?${params.toString()}`;
  } catch (error) {
    showToast(formatError(error), "error");
    await refreshCart(false);
    await refreshAccount(false);
  }
}

async function confirmPayment(orderId, portonePaymentId) {
  return api("/api/payments/confirm", {
    method: "POST",
    body: { orderId, portonePaymentId },
  });
}

async function login() {
  try {
    const data = await api("/api/auth/login", {
      method: "POST",
      auth: false,
      body: {
        email: els.loginEmail.value.trim(),
        password: els.loginPassword.value,
      },
    });
    state.token = data.accessToken;
    state.user = data.user;
    localStorage.setItem(STORAGE.token, state.token);
    localStorage.setItem(STORAGE.user, JSON.stringify(state.user));
    renderSession();
    closeAuthDialog();
    await refreshCart(false);
    await refreshAccount(false);
    showToast(`${state.user.name}님, 로그인되었습니다.`);
  } catch (error) {
    showToast(formatError(error), "error");
  }
}

async function signup() {
  try {
    const request = {
      email: els.signupEmail.value.trim(),
      password: els.signupPassword.value,
      name: els.signupName.value.trim(),
      phone: els.signupPhone.value.trim(),
    };
    await api("/api/auth/signup", {
      method: "POST",
      auth: false,
      body: request,
    });
    els.loginEmail.value = request.email;
    els.loginPassword.value = request.password;
    switchAuthTab("login");
    await login();
  } catch (error) {
    showToast(formatError(error), "error");
  }
}

async function logout() {
  if (state.token) {
    try {
      await api("/api/auth/logout", { method: "POST" });
    } catch (_) {
      // Local logout should still clear the browser session.
    }
  }
  clearSession();
  renderSession();
  renderCart();
  refreshAccount(false);
  closeAuthDialog();
  showToast("로그아웃되었습니다.");
}

async function refreshAccount(showErrors = true) {
  if (!state.token) {
    renderLoggedOutAccount();
    return;
  }

  try {
    const [balance, transactions] = await Promise.all([
      api("/api/points/balance"),
      api("/api/points/transactions?page=0&size=8"),
    ]);
    state.pointBalance = Number(balance.balance || 0);
    renderAuthAccount(balance.userId);
    renderTransactions(transactions.content || []);
    updateCheckoutTotal();
  } catch (error) {
    if (showErrors) showToast(formatError(error), "error");
    if (error.status === 401) {
      clearSession();
      renderSession();
      renderCart();
      return;
    }
    renderSession();
  }
}

function renderTransactions(transactions) {
  if (!transactions.length) {
    els.pointTransactions.innerHTML = `<div class="empty-state">거래 내역이 없습니다.</div>`;
    return;
  }

  els.pointTransactions.innerHTML = transactions.map((tx) => `
    <div class="transaction-row">
      <strong>${escapeHtml(pointTypeLabel(tx.type))}</strong>
      <span>${formatNumber(tx.amount)} P</span>
      <span>${formatDate(tx.createdAt)}</span>
    </div>
  `).join("");
}

function updateCheckoutTotal() {
  const subtotal = Number(state.cart?.totalAmount || 0);
  const usablePoint = Math.min(state.pointBalance, subtotal);
  const usePointAmount = normalizeUsePoint(false);
  els.pointHint.textContent = `보유 포인트 ${formatNumber(state.pointBalance)}P / 최대 ${formatNumber(usablePoint)}P`;
  els.checkoutTotal.textContent = formatCurrency(Math.max(subtotal - usePointAmount, 0));
}

function normalizeUsePoint(writeBack = true) {
  const subtotal = Number(state.cart?.totalAmount || 0);
  const usablePoint = Math.min(state.pointBalance, subtotal);
  let value = Number(els.usePointInput.value || 0);
  if (!Number.isFinite(value) || value < 0) value = 0;
  value = Math.floor(Math.min(value, usablePoint));
  if (writeBack) els.usePointInput.value = String(value);
  return value;
}

function useAllPoints() {
  const subtotal = Number(state.cart?.totalAmount || 0);
  els.usePointInput.value = String(Math.min(state.pointBalance, subtotal));
  updateCheckoutTotal();
}

function renderSession() {
  const loggedIn = Boolean(state.token);
  els.authTabs.classList.toggle("hidden", loggedIn);
  els.loginPane.classList.toggle("hidden", loggedIn);
  els.signupPane.classList.toggle("hidden", loggedIn);
  els.authAccountPane.classList.toggle("hidden", !loggedIn);
  els.logoutButton.classList.toggle("hidden", !loggedIn);

  if (loggedIn) {
    renderAuthAccount();
    return;
  }

  switchAuthTab("login");
  renderLoggedOutAccount();
}

function renderAuthAccount(fallbackUserId) {
  if (!state.token) return;
  const userId = state.user?.userId || fallbackUserId;
  els.authUserName.textContent = state.user?.name || "사용자";
  els.authUserEmail.textContent = state.user?.email || "-";
  els.authUserId.textContent = userId ? `#${userId}` : "-";
  els.authPointBalance.textContent = `${formatNumber(state.pointBalance)} P`;
}

function renderLoggedOutAccount() {
  state.pointBalance = 0;
  els.pointTransactions.innerHTML = `<div class="empty-state">로그인 후 거래 내역을 확인할 수 있습니다.</div>`;
  els.authUserName.textContent = "비로그인";
  els.authUserEmail.textContent = "-";
  els.authUserId.textContent = "-";
  els.authPointBalance.textContent = "-";
  updateCheckoutTotal();
}

function clearSession() {
  state.token = null;
  state.user = null;
  state.cart = null;
  state.pointBalance = 0;
  localStorage.removeItem(STORAGE.token);
  localStorage.removeItem(STORAGE.user);
}

function requireLogin() {
  if (state.token) return true;
  openAuthDialog();
  showToast("로그인이 필요합니다.", "error");
  return false;
}

function openAuthDialog() {
  renderSession();
  if (state.token) {
    refreshAccount(false);
  }
  if (els.authDialog.open) return;
  if (els.authDialog.showModal) {
    els.authDialog.showModal();
  } else {
    els.authDialog.setAttribute("open", "");
  }
}

function closeAuthDialog() {
  if (els.authDialog.open) els.authDialog.close();
}

function switchAuthTab(tab) {
  document.querySelectorAll("[data-auth-tab]").forEach((button) => {
    button.classList.toggle("is-active", button.dataset.authTab === tab);
  });
  els.loginPane.classList.toggle("is-active", tab === "login");
  els.signupPane.classList.toggle("is-active", tab === "signup");
}

function openCart() {
  if (!state.token) {
    openAuthDialog();
    return;
  }
  refreshCart(false);
  refreshAccount(false);
  openDrawer(els.cartDrawer);
}

function openDrawer(drawer) {
  closeDrawers();
  drawer.classList.add("is-open");
  drawer.setAttribute("aria-hidden", "false");
  document.body.style.overflow = "hidden";
}

function closeDrawer(drawer) {
  drawer.classList.remove("is-open");
  drawer.setAttribute("aria-hidden", "true");
  if (!document.querySelector(".drawer.is-open")) {
    document.body.style.overflow = "";
  }
}

function closeDrawers() {
  document.querySelectorAll(".drawer.is-open").forEach(closeDrawer);
}

function scrollToSection(id) {
  document.getElementById(id)?.scrollIntoView({ block: "start" });
}

function updateCategoryNav() {
  const selectedCategory = els.categoryFilter.value;
  document.querySelectorAll("[data-category-nav]").forEach((button) => {
    button.classList.toggle("is-active", button.dataset.categoryNav === selectedCategory);
  });
  updateProductHeading();
}

function updateProductHeading() {
  const selectedCategory = els.categoryFilter.value;
  els.productTitle.textContent = selectedCategory
    ? CATEGORY_LABELS[selectedCategory] || selectedCategory
    : "전체 상품";
}

function focusSearch() {
  scrollToSection("products");
  els.searchInput.focus();
}

function appendParam(params, key, value) {
  if (value !== undefined && value !== null && String(value).trim() !== "") {
    params.set(key, String(value).trim());
  }
}

function localProductPage(page = 0) {
  const size = 12;
  const category = els.categoryFilter.value;
  const status = els.statusFilter.value;
  const sort = els.sortFilter.value || "LATEST";

  let content = LOCAL_PRODUCTS.filter((product) => {
    if (category && product.category !== category) return false;
    if (status && product.status !== status) return false;
    return true;
  });

  content = content.sort((left, right) => {
    if (sort === "PRICE_ASC") return left.price - right.price;
    if (sort === "PRICE_DESC") return right.price - left.price;
    return right.productId - left.productId;
  });

  const totalElements = content.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  const safePage = Math.min(Math.max(page, 0), totalPages - 1);
  const start = safePage * size;

  return {
    content: content.slice(start, start + size),
    page: safePage,
    size,
    totalElements,
    totalPages,
    hasNext: safePage + 1 < totalPages,
    localFallback: true,
  };
}

function buildOrderName(items = []) {
  if (!items.length) return "EC2샵 주문";
  const first = items[0].productName;
  return items.length === 1 ? first : `${first} 외 ${items.length - 1}건`;
}

function productImage(productId) {
  return PRODUCT_IMAGES[productId] || "assets/products/product-sheet.png";
}

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE.user));
  } catch (_) {
    return null;
  }
}

function formatCurrency(value) {
  return `${formatNumber(Number(value || 0))}원`;
}

function formatNumber(value) {
  return new Intl.NumberFormat("ko-KR").format(value || 0);
}

function formatDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 10);
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function pointTypeLabel(type) {
  return {
    USE: "사용",
    EARN: "적립",
    USE_CANCEL: "사용 취소",
    USE_RESTORE: "사용 복구",
    EARN_CANCEL: "적립 취소",
  }[type] || type;
}

function formatError(error) {
  return error?.code ? `${error.code}: ${error.message}` : error?.message || "요청 처리 중 오류가 발생했습니다.";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

let toastTimer = null;

function showToast(message, type = "info") {
  clearTimeout(toastTimer);
  els.toast.textContent = message;
  els.toast.classList.toggle("is-error", type === "error");
  els.toast.classList.add("is-visible");
  toastTimer = setTimeout(() => {
    els.toast.classList.remove("is-visible");
  }, 3200);
}
