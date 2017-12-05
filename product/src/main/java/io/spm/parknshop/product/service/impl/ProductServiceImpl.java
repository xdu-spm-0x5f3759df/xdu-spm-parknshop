package io.spm.parknshop.product.service.impl;

import io.spm.parknshop.common.util.ExceptionUtils;
import io.spm.parknshop.product.domain.Product;
import io.spm.parknshop.product.repository.ProductRepository;
import io.spm.parknshop.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;

import static io.spm.parknshop.common.async.ReactorAsyncWrapper.*;
import static io.spm.parknshop.common.exception.ErrorConstants.*;

@Service
public class ProductServiceImpl implements ProductService {

  @Autowired
  private ProductRepository productRepository;

  @Override
  public Mono<Product> add(Product product) {
    if (!isValidNewProduct(product)) {
      return Mono.error(ExceptionUtils.invalidParam("product"));
    }
    if (Objects.nonNull(product.getId())) {
      return Mono.error(ExceptionUtils.invalidParam("productId should not be provided"));
    }
    return async(() -> productRepository.save(product));
  }

  @Override
  public Mono<Product> modify(Long productId, Product product) {
    if (Objects.isNull(productId) || productId <= 0) {
      return Mono.error(ExceptionUtils.invalidParam("productId"));
    }
    if (!isValidProduct(product)) {
      return Mono.error(ExceptionUtils.invalidParam("product"));
    }
    if (!productId.equals(product.getId())) {
      return Mono.error(ExceptionUtils.idNotMatch());
    }
    return async(() -> productRepository.save(product));
  }

  @Override
  public Mono<Optional<Product>> getById(final Long id) {
    if (Objects.isNull(id) || id <= 0) {
      return Mono.error(ExceptionUtils.invalidParam("id"));
    }
    return async(() -> productRepository.findById(id));
  }

  @Override
  public Flux<Product> getByStoreId(Long storeId) {
    if (Objects.isNull(storeId) || storeId <= 0) {
      return Flux.error(ExceptionUtils.invalidParam("storeId"));
    }

    return asyncIterable(() -> productRepository.getByStoreId(storeId));
  }

  @Override
  public Mono<Long> remove(Long id) {
    if (Objects.isNull(id) || id <= 0) {
      return Mono.error(ExceptionUtils.invalidParam("id"));
    }
    return asyncExecute(() -> productRepository.deleteById(id));
  }

  private boolean isValidNewProduct(final Product product) {
    return Optional.ofNullable(product)
      .map(e -> product.getCatalogId())
      .map(e -> product.getName())
      .map(e -> product.getStoreId())
      .map(e -> product.getDescription())
      .isPresent();
  }

  private boolean isValidProduct(final Product product) {
    return Optional.ofNullable(product)
      .map(e -> product.getId())
      .map(e -> product.getCatalogId())
      .map(e -> product.getName())
      .map(e -> product.getStoreId())
      .map(e -> product.getDescription())
      .isPresent();
  }
}
