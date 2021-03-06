package io.spm.parknshop.api.controller;

import io.spm.parknshop.api.util.AuthUtils;
import io.spm.parknshop.seller.service.SellerService;
import io.spm.parknshop.seller.service.SellerUserService;
import io.spm.parknshop.store.domain.Store;
import io.spm.parknshop.store.domain.StoreDTO;
import io.spm.parknshop.store.service.StoreService;
import io.spm.parknshop.user.domain.LoginVO;
import io.spm.parknshop.user.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/")
public class SellerApiController {

  @Autowired
  private SellerService sellerService;
  @Autowired
  private SellerUserService sellerUserService;
  @Autowired
  private StoreService storeService;

  @PostMapping("/seller/apply/apply_store")
  public Mono<Long> apiApplyStore(ServerWebExchange exchange, @RequestBody StoreDTO store) {
    return AuthUtils.getSellerId(exchange)
      .flatMap(sellerId -> sellerService.applyStore(AuthUtils.SELLER_PREFIX + sellerId, store));
  }

  @GetMapping("/seller/u/{id}")
  public Mono<User> apiGetSellerById(@PathVariable("id") Long id) {
    return sellerUserService.getSellerById(id)
      .filter(Optional::isPresent)
      .map(Optional::get);
  }

  @PostMapping("/seller/register")
  public Mono<User> apiSellerRegister(@RequestBody User user) {
    return sellerUserService.register(user);
  }

  @PostMapping("/seller/login")
  public Mono<LoginVO> apiSellerLogin(@RequestBody User user) {
    return sellerUserService.login(user.getUsername(), user.getPassword());
  }

  @GetMapping("/seller/my_store")
  public Mono<Store> apiGetStoreBySeller(ServerWebExchange exchange) {
    return AuthUtils.getSellerId(exchange)
      .flatMap(sellerId -> storeService.getBySellerId(sellerId)
        .filter(Optional::isPresent)
        .map(Optional::get)
      );
  }

}
