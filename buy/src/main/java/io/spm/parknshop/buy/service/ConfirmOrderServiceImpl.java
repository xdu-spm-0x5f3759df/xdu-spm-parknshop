package io.spm.parknshop.buy.service;

import io.spm.parknshop.buy.domain.ConfirmOrderDO;
import io.spm.parknshop.buy.domain.ConfirmOrderResult;
import io.spm.parknshop.buy.domain.OrderPreview;
import io.spm.parknshop.buy.domain.OrderProductUnit;
import io.spm.parknshop.buy.domain.OrderStoreGroupUnit;
import io.spm.parknshop.cart.domain.CartProduct;
import io.spm.parknshop.cart.domain.CartUnit;
import io.spm.parknshop.cart.domain.ShoppingCart;
import io.spm.parknshop.cart.service.CartService;
import io.spm.parknshop.common.exception.ErrorConstants;
import io.spm.parknshop.common.exception.ServiceException;
import io.spm.parknshop.common.util.ExceptionUtils;
import io.spm.parknshop.delivery.domain.DeliveryAddress;
import io.spm.parknshop.delivery.domain.DeliveryTemplate;
import io.spm.parknshop.delivery.service.DeliveryAddressService;
import io.spm.parknshop.delivery.service.DeliveryTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Eric Zhao
 */
@Service
public class ConfirmOrderServiceImpl implements ConfirmOrderService {

  @Autowired
  private CartService cartService;
  @Autowired
  private DeliveryAddressService deliveryAddressService;
  @Autowired
  private DeliveryTemplateService deliveryTemplateService;

  @Override
  public Mono<OrderPreview> previewOrder(Long userId) {
    return checkUserId(userId)
      .flatMap(v -> cartService.getCartForUser(userId))
      .flatMap(this::aggregateOrderPreview);
  }

  private Mono<OrderPreview> aggregateOrderPreview(ShoppingCart cart) {
    return Flux.fromIterable(cart.getCart())
      .map(CartUnit::getStoreId)
      .flatMap(storeId -> deliveryTemplateService.getTemplateByStoreId(storeId).take(1))
      .collectList()
      .map(templates -> buildOrderPreviewInternal(cart, templates));
  }

  private OrderPreview buildOrderPreviewInternal(ShoppingCart cart, List<DeliveryTemplate> deliveryTemplates) {
    // Prepare delivery template cache.
    final Map<Long, DeliveryTemplate> map = new HashMap<>(deliveryTemplates.size());
    deliveryTemplates.forEach(e -> map.putIfAbsent(e.getStoreId(), e));
    // Start aggregate.
    List<OrderStoreGroupUnit> storeGroups = cart.getCart().stream()
      .map(unit -> wrapStoreGroupUnit(unit, map))
      .collect(Collectors.toList());
    int totalAmount = storeGroups.stream()
      .mapToInt(OrderStoreGroupUnit::getTotalAmount)
      .sum();
    double totalFreight = storeGroups.stream()
      .mapToDouble(OrderStoreGroupUnit::getTotalFreight)
      .sum();
    double totalPrice = storeGroups.stream()
      .mapToDouble(OrderStoreGroupUnit::getTotalPrice)
      .sum();
    return new OrderPreview().setStoreGroups(storeGroups).setTotalAmount(totalAmount)
      .setTotalFreight(totalFreight).setTotalPrice(totalPrice);
  }

  private OrderStoreGroupUnit wrapStoreGroupUnit(CartUnit cartUnit, Map<Long, DeliveryTemplate> deliveryMap) {
    Long storeId = cartUnit.getStoreId();
    // Filter checked products for the order.
    List<OrderProductUnit> products = cartUnit.getProducts().stream()
      .filter(CartProduct::isChecked)
      .map(this::wrapProductUnit)
      .collect(Collectors.toList());
    int totalAmount = products.stream()
      .mapToInt(OrderProductUnit::getAmount)
      .sum();
    DeliveryTemplate deliveryTemplate = deliveryMap.get(storeId);
    double totalFreight = deliveryTemplate.getDefaultPrice();
    double totalPrice = totalFreight + products.stream()
      .mapToDouble(e -> e.getPrice() * e.getAmount())
      .sum();
    return new OrderStoreGroupUnit().setStoreId(storeId).setStoreName(cartUnit.getStoreName())
      .setProducts(products).setTotalAmount(totalAmount).setDeliveryTemplate(deliveryTemplate)
      .setTotalFreight(totalFreight).setTotalPrice(totalPrice);
  }

  private OrderProductUnit wrapProductUnit(CartProduct cartProduct) {
    return new OrderProductUnit()
      .setProductId(cartProduct.getProductId())
      .setProductName(cartProduct.getProduct().getProduct().getName())
      .setAmount(cartProduct.getAmount())
      .setInventory(cartProduct.getProduct().getInventory())
      .setPrice(cartProduct.getProduct().getProduct().getPrice())
      .setPicUri(cartProduct.getProduct().getProduct().getPicUri());
  }

  @Override
  public Mono<ConfirmOrderResult> confirmOrder(Long userId, ConfirmOrderDO requestDO) {
    return checkConfirmRequest(userId, requestDO)
      .flatMap(v -> checkAddressValid(requestDO.getAddressId()))
      .map(e -> new ConfirmOrderResult()); // TODO
  }

  private Mono<DeliveryAddress> checkAddressValid(Long addressId) {
    return deliveryAddressService.getById(addressId)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .switchIfEmpty(Mono.error(new ServiceException(ErrorConstants.USER_DELIVERY_ADDRESS_NOT_EXIST, "The address does not exist")));
  }

  private Mono<?> checkConfirmRequest(Long userId, ConfirmOrderDO requestDO) {
    return checkUserId(userId)
      .flatMap(v -> checkRequestValid(requestDO));
  }

  private Mono<?> checkRequestValid(ConfirmOrderDO requestDO) {
    return Optional.ofNullable(requestDO)
      .map(e -> requestDO.getUserId())
      .map(e -> requestDO.getAddressId())
      .map(Mono::just)
      .orElse(Mono.error(ExceptionUtils.invalidParam("confirm request")));
  }

  private Mono<Long> checkUserId(Long userId) {
    if (Objects.isNull(userId) || userId <= 0) {
      return Mono.error(ExceptionUtils.invalidParam("userId"));
    }
    return Mono.just(userId);
  }
}