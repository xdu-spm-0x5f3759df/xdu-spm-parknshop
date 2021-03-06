package io.spm.parknshop.cart.service;

import io.spm.parknshop.cart.domain.CartEvent;
import io.spm.parknshop.cart.domain.CartEventType;
import io.spm.parknshop.cart.domain.CartProduct;
import io.spm.parknshop.cart.domain.CartUnit;
import io.spm.parknshop.cart.domain.SimpleCartProduct;
import io.spm.parknshop.cart.domain.ShoppingCart;
import io.spm.parknshop.cart.repository.CartRepository;
import io.spm.parknshop.common.exception.ErrorConstants;
import io.spm.parknshop.common.exception.ServiceException;
import io.spm.parknshop.common.util.ExceptionUtils;
import io.spm.parknshop.inventory.service.InventoryService;
import io.spm.parknshop.product.domain.ProductStatus;
import io.spm.parknshop.product.service.ProductQueryService;
import io.spm.parknshop.product.service.ProductService;
import io.spm.parknshop.store.service.StoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Eric Zhao
 * @author four
 */
@Service
public class CartServiceImpl implements CartService {

  @Autowired
  private CartRepository cartRepository;

  @Autowired
  private ProductService productService;
  @Autowired
  private ProductQueryService productQueryService;
  @Autowired
  private StoreService storeService;
  @Autowired
  private InventoryService inventoryService;

  @Override
  public Mono<Boolean> clearCartCheckout(Long userId) {
    return checkUserId(userId)
      .flatMapMany(v -> cartRepository.getCart(userId))
      .filter(e -> !e.isChecked())
      .collectList()
      .flatMap(list -> cartRepository.clearCart(userId)
        .then(cartRepository.putCart(userId, list))
      );
  }

  @Override
  public Mono<ShoppingCart> updateCart(Long userId, CartEvent cartEvent) {
    if (Objects.isNull(cartEvent)) {
      return Mono.error(ExceptionUtils.invalidParam("cart operation"));
    }
    switch (cartEvent.getEventType()) {
      case CartEventType.ADD_CART:
        return addCart(userId, cartEvent);
      case CartEventType.UPDATE_AMOUNT:
        return updateAmount(userId, cartEvent);
      case CartEventType.DECREASE_CART:
        return decreaseCart(userId, cartEvent);
      case CartEventType.REMOVE:
        return removeFromCart(userId, cartEvent);
      case CartEventType.CHECK:
        return markCheckProduct(userId, cartEvent);
      default:
        return Mono.error(ExceptionUtils.invalidParam("cart operation (unknown operation type)"));
    }
  }

  private Mono<ShoppingCart> updateAmount(Long userId, CartEvent cartEvent) {
    return checkParams(userId, cartEvent)
      .then(cartEventShouldBe(cartEvent, CartEventType.UPDATE_AMOUNT))
      .then(checkProductInventory(cartEvent.getProductId(), cartEvent.getAmount()))
      .then(cartRepository.getCartProduct(userId, cartEvent.getProductId())
        .map(e -> e.setAmount(cartEvent.getAmount()).setChecked(true))
        .switchIfEmpty(newCreated(cartEvent))
        .flatMap(e -> cartRepository.putCartProduct(userId, e))
        .flatMap(e -> getCartForUser(userId))
      );
  }

  private Mono<ShoppingCart> markCheckProduct(Long userId, CartEvent cartEvent) {
    return cartEventShouldBe(cartEvent, CartEventType.CHECK)
      .then(cartRepository.getCartProduct(userId, cartEvent.getProductId())
        .map(e -> e.setChecked(!e.isChecked()))
        .switchIfEmpty(Mono.error(new ServiceException(ErrorConstants.PRODUCT_NOT_EXIST_IN_CART, "Product is not in your cart")))
        .flatMap(e -> cartRepository.putCartProduct(userId, e))
        .flatMap(e -> getCartForUser(userId))
      );
  }

  private Mono<ShoppingCart> addCart(Long userId, CartEvent cartEvent) {
    return checkParams(userId, cartEvent)
      .then(cartEventShouldBe(cartEvent, CartEventType.ADD_CART))
      .then(cartRepository.getCartProduct(userId, cartEvent.getProductId())
        .map(e -> e.plusAmount(cartEvent.getAmount()).setChecked(true))
        .flatMap(cartProduct -> checkProductInventory(cartEvent.getProductId(), cartProduct.getAmount()).map(e -> cartProduct))
        .switchIfEmpty(checkProductInventory(cartEvent.getProductId(), cartEvent.getAmount())
          .then(newCreated(cartEvent)))
        .flatMap(e -> cartRepository.putCartProduct(userId, e))
        .flatMap(e -> getCartForUser(userId))
      );
  }

  private Mono<ShoppingCart> removeFromCart(Long userId, CartEvent cartEvent) {
    return cartEventShouldBe(cartEvent, CartEventType.REMOVE)
      .then(checkProductExists(cartEvent.getProductId()))
      .then(cartRepository.deleteCartProduct(userId, cartEvent.getProductId()))
      .then(getCartForUser(userId));
  }

  private Mono<ShoppingCart> decreaseCart(Long userId, CartEvent cartEvent) {
    return checkParams(userId, cartEvent)
      .then(cartEventShouldBe(cartEvent, CartEventType.DECREASE_CART))
      .then(checkProductExists(cartEvent.getProductId()))
      .then(cartRepository.getCartProduct(userId, cartEvent.getProductId())
        .map(e -> e.decreaseAmount(cartEvent.getAmount()))
        .switchIfEmpty(Mono.error(new ServiceException(ErrorConstants.PRODUCT_NOT_EXIST_IN_CART, "Product is not in your cart")))
        .flatMap(e -> doDelete(userId, e))
        .flatMap(e -> getCartForUser(userId))
      );
  }

  @Override
  public Mono<ShoppingCart> getCartForUser(Long userId) {
    return checkUserId(userId)
      .flatMapMany(v -> cartRepository.getCart(userId))
      .concatMap(this::aggregateCartProduct)
      .groupBy(e -> e.getProduct().getProduct().getStoreId())
      .concatMap(stream -> stream.collectList()
        .map(e -> buildCartUnit(e, stream.key())))
      .reduce(new ShoppingCart(), this::aggregateShoppingCart);
  }

  private ShoppingCart aggregateShoppingCart(ShoppingCart cart, CartUnit cartUnit) {
    cart.getCart().add(cartUnit);
    return cart.setTotalPrice(cart.getTotalPrice() + cartUnit.getTotalPrice())
      .setTotalAmount(cart.getTotalAmount() + cartUnit.getTotalAmount());
  }

  private CartUnit buildCartUnit(List<CartProduct> products, long storeId) {
    // TODO: What if all products in the shop is unavailable, or the shop is in blacklist?
    String storeName = products.get(0).getProduct().getStoreName();
    double totalPrice = products.stream()
      .filter(CartProduct::isChecked)
      .mapToDouble(e -> e.getProduct().getProduct().getPrice() * e.getAmount())
      .sum();
    int amount = products.stream()
      .filter(CartProduct::isChecked)
      .mapToInt(CartProduct::getAmount)
      .sum();
    return new CartUnit().setProducts(products)
      .setStoreId(storeId).setStoreName(storeName)
      .setTotalPrice(totalPrice).setTotalAmount(amount);
  }

  private Mono<CartProduct> aggregateCartProduct(SimpleCartProduct simpleProduct) {
    return productQueryService.getProduct(simpleProduct.getId())
      .filter(Optional::isPresent)
      .map(Optional::get)
      .filter(e -> ProductStatus.isAvailable(e.getProduct().getStatus()))
      .map(product -> new CartProduct().setProductId(simpleProduct.getId())
        .setAmount(simpleProduct.getAmount())
        .setProduct(product)
        .setChecked(product.getInventory() >= simpleProduct.getAmount() && simpleProduct.isChecked())
      );
  }

  /**
   * Check if the product exists, then get inventory of the product.
   *
   * @param productId product ID
   * @param n         least amount (guarantee greater than 0)
   * @return async result
   */
  private Mono<Integer> checkProductInventory(/*@NonNull*/ Long productId, /*@Positive*/ Integer n) {
    return inventoryService.getInventoryAmount(productId)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .switchIfEmpty(Mono.error(new ServiceException(ErrorConstants.PRODUCT_NOT_EXIST, "Invalid product inventory status")))
      .flatMap(amount -> {
        if (amount < n) {
          return Mono.error(new ServiceException(ErrorConstants.PRODUCT_NO_INVENTORY, String.format("Inventory of product %d is insufficient", productId)));
        } else {
          return Mono.just(amount);
        }
      });
  }

  private Mono<?> checkProductExists(Long productId) {
    return productService.getById(productId)
      .flatMap(productService::filterNormal);
  }

  private Mono<?> doDelete(/*@NonNull*/ Long userId, SimpleCartProduct cartProduct) {
    if (cartProduct.getAmount() <= 0) {
      return cartRepository.deleteCartProduct(userId, cartProduct.getId());
    } else {
      return cartRepository.putCartProduct(userId, cartProduct);
    }
  }

  private Mono<SimpleCartProduct> newCreated(CartEvent cartEvent) {
    return Mono.just(new SimpleCartProduct(cartEvent.getProductId(), cartEvent.getAmount()).setChecked(true));
  }

  private Mono<?> checkParams(Long userId, CartEvent cartEvent) {
    return checkUserId(userId)
      .then(checkCartEvent(cartEvent));
  }

  private Mono<?> checkCartEvent(CartEvent cartEvent) {
    return Optional.ofNullable(cartEvent)
      .map(e -> cartEvent.getProductId())
      .map(e -> cartEvent.getAmount())
      .map(Mono::just)
      .orElse(Mono.error(ExceptionUtils.invalidParam("cart operation")))
      .then(checkAmount(cartEvent));
  }

  private Mono<?> cartEventShouldBe(CartEvent event, int type) {
    if (event.getEventType() != type) {
      return Mono.error(ExceptionUtils.invalidParam("cart event type mismatch"));
    }
    return Mono.just(type);
  }

  private Mono<?> checkAmount(/*@NonNull*/ CartEvent cartEvent) {
    if (cartEvent.getAmount() <= 0) {
      return Mono.error(ExceptionUtils.invalidParam("product amount must be positive"));
    }
    return Mono.just(cartEvent);
  }

  private Mono<?> checkUserId(Long userId) {
    if (Objects.isNull(userId) || userId <= 0) {
      return Mono.error(ExceptionUtils.invalidParam("id"));
    }
    return Mono.just(userId);
  }
}
