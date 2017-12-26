package io.spm.parknshop.advertisement.service.impl;

import io.spm.parknshop.advertisement.domain.AdApplyEventType;
import io.spm.parknshop.advertisement.domain.AdType;
import io.spm.parknshop.advertisement.domain.Advertisement;
import io.spm.parknshop.advertisement.event.AdApplyEventAggregator;
import io.spm.parknshop.advertisement.event.AdApplyEventNotifier;
import io.spm.parknshop.advertisement.service.AdvertisementService;
import io.spm.parknshop.advertisement.service.AdvertisementWorkflowService;
import io.spm.parknshop.apply.domain.AdvertisementDO;
import io.spm.parknshop.apply.domain.Apply;
import io.spm.parknshop.apply.domain.ApplyEvent;
import io.spm.parknshop.apply.domain.ApplyResult;
import io.spm.parknshop.apply.domain.ApplyStatus;
import io.spm.parknshop.apply.repository.ApplyEventRepository;
import io.spm.parknshop.apply.repository.ApplyMetadataRepository;
import io.spm.parknshop.apply.service.ApplyDataService;
import io.spm.parknshop.apply.service.ApplyProcessService;
import io.spm.parknshop.apply.service.ApplyService;
import io.spm.parknshop.common.exception.ServiceException;
import io.spm.parknshop.common.functional.Tuple2;
import io.spm.parknshop.common.state.StateMachine;
import io.spm.parknshop.common.util.ExceptionUtils;
import io.spm.parknshop.common.util.JsonUtils;
import io.spm.parknshop.product.service.ProductService;
import io.spm.parknshop.store.service.StoreService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static io.spm.parknshop.common.async.ReactorAsyncWrapper.*;
import static io.spm.parknshop.common.exception.ErrorConstants.*;

/**
 * @author Eric Zhao
 * @author four
 */
@Service("adApplyService")
public class AdvertisementWorkflowServiceImpl implements AdvertisementWorkflowService, ApplyService<AdvertisementDO, String>, ApplyProcessService {

  @Autowired
  private ApplyMetadataRepository applyMetadataRepository;
  @Autowired
  private ApplyEventRepository applyEventRepository;

  @Autowired
  private ProductService productService;
  @Autowired
  private StoreService storeService;
  @Autowired
  private ApplyDataService applyDataService;
  @Autowired
  private AdvertisementService advertisementService;

  @Autowired
  private AdApplyEventNotifier eventNotifier;
  @Autowired
  private AdApplyEventAggregator applyEventAggregator;

  @Autowired
  @Qualifier(value = "adStateMachine")
  private StateMachine stateMachine;

  @Override
  public Mono<String> applyFor(String proposerId, AdvertisementDO advertisement) {
    return checkApplyParams(proposerId, advertisement)
      .map(this::wrapAdvertisement)
      .flatMap(ad -> checkTargetExists(ad)
        .flatMap(v -> submitNewApply(proposerId, ad))
        .flatMap(t -> eventNotifier.doNotify(t.r2, t.r1)
          .map(v -> t.r1.getId().toString()) // Return the apply id.
        )
      );
  }

  private Advertisement wrapAdvertisement(AdvertisementDO advertisementDO) {
    return new Advertisement()
      .setAdType(advertisementDO.getAdType())
      .setAdOwner(advertisementDO.getAdOwner())
      .setAdTarget(advertisementDO.getAdTarget())
      .setDescription(advertisementDO.getDescription())
      .setAdUrl(advertisementDO.getAdUrl())
      .setStartDate(advertisementDO.getStartDate())
      .setEndDate(advertisementDO.getEndDate())
      .setAdTotalPrice(advertisementDO.getAdType() == AdType.AD_PRODUCT ? 1000 : 500);
  }

  private Mono<?> checkTargetExists(Advertisement advertisement) {
    switch (advertisement.getAdType()) {
      case AdType.AD_PRODUCT:
        return checkProductExists(advertisement.getAdTarget());
      case AdType.AD_STORE:
        return checkStoreExists(advertisement.getAdTarget());
      default:
        return Mono.error(new ServiceException(AD_UNKNOWN_TYPE, "Unknown advertisement type"));
    }
  }

  private Mono<Tuple2<Apply, ApplyEvent>> submitNewApply(String proposerId, Advertisement advertisement) {
    Apply applyMetadata = new Apply().setApplyType(advertisement.getAdType())
      .setApplyData(JsonUtils.toJson(advertisement))
      .setProposerId(proposerId)
      .setStatus(ApplyStatus.NEW_APPLY);
    ApplyEvent applyEvent = new ApplyEvent().setApplyEventType(AdApplyEventType.SUBMIT_APPLY)
      .setProcessorId(proposerId);
    return async(() -> saveNewApply(applyMetadata, applyEvent));
  }

  @Transactional
  protected Tuple2<Apply, ApplyEvent> saveNewApply(Apply applyMetadata, ApplyEvent applyEvent) {
    Apply apply = applyMetadataRepository.save(applyMetadata);
    applyEvent.setApplyId(apply.getId());
    ApplyEvent newEvent = applyEventRepository.save(applyEvent);
    return Tuple2.of(apply, newEvent);
  }

  @Override
  public Mono<Long> rejectApply(Long applyId, String processorId, ApplyResult applyResult) {
    return operateApply(AdApplyEventType.REJECT_APPLY, applyId, processorId, applyResult);
  }

  @Override
  public Mono<Long> approveApply(Long applyId, String processorId, ApplyResult applyResult) {
    return operateApply(AdApplyEventType.APPROVE_APPLY, applyId, processorId, applyResult)
      .flatMap(e -> finishPay(applyId, 12233L)).map(e -> 0L); // TODO: For test
  }

  @Override
  public Mono<Long> cancelApply(Long applyId, String processorId) {
    return operateApply(AdApplyEventType.WITHDRAW_APPLY, applyId, processorId, new ApplyResult());
  }

  private Mono<Long> operateApply(int event, Long applyId, String processorId, ApplyResult applyResult) {
    if (Objects.isNull(applyId) || Objects.isNull(processorId)) {
      return Mono.error(ExceptionUtils.invalidParam("apply"));
    }
    ApplyEvent applyEvent = new ApplyEvent().setApplyId(applyId)
      .setApplyEventType(event)
      .setProcessorId(processorId)
      .setExtraInfo(JsonUtils.toJson(applyResult));
    return applyEventAggregator.aggregate(applyDataService.getEventStream(applyId))
      .map(curState -> stateMachine.transform(curState, event))
      .flatMap(nextState -> asyncExecute(() -> saveAndUpdate(applyId, applyEvent, nextState)));
  }

  @Transactional
  protected void saveAndUpdate(long applyId, ApplyEvent applyEvent, int nextStatus) {
    applyEventRepository.save(applyEvent);
    applyMetadataRepository.updateStatus(applyId, nextStatus);
  }

  @Override
  public Mono<String> startPay(String processorId, Long applyId) {
    // TODO: not implemented
    return Mono.empty();
  }

  @Override
  public Mono<Advertisement> finishPay(Long applyId, Long outerPaymentId) {
    return getApplyMetadata(applyId)
      .flatMap(apply -> operateApply(AdApplyEventType.FINISH_PAY, applyId, apply.getProposerId(), new ApplyResult().setMessage("Payment ID: " + outerPaymentId))
        .flatMap(v -> addAdvertisement(apply))
      );
  }

  private Mono<Advertisement> addAdvertisement(Apply apply) {
    Advertisement advertisement = JsonUtils.parse(apply.getApplyData(), Advertisement.class)
      .setGmtModified(new Date())
      .setApplyId(apply.getId());
    return advertisementService.addNewAdvertisement(advertisement);
  }

  private Mono<Apply> getApplyMetadata(Long applyId) {
    return async(() -> applyMetadataRepository.getById(applyId))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .switchIfEmpty(Mono.error(new ServiceException(APPLY_NOT_EXIST, "Apply does not exist")));
  }

  private Mono<?> checkProductExists(Long productId) {
    return productService.getById(productId)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .switchIfEmpty(Mono.error(new ServiceException(PRODUCT_NOT_EXIST, "Target product does not exist")));
  }

  private Mono<?> checkStoreExists(Long storeId) {
    return storeService.getById(storeId)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .switchIfEmpty(Mono.error(new ServiceException(STORE_NOT_EXIST, "Target store does not exist")));
  }

  private Mono<AdvertisementDO> checkApplyParams(String proposerId, AdvertisementDO advertisement) {
    if (StringUtils.isEmpty(proposerId)) {
      return Mono.error(ExceptionUtils.invalidParam("proposerId"));
    }
    boolean valid = Optional.ofNullable(advertisement)
      .map(e -> advertisement.getAdOwner())
      .map(e -> advertisement.getAdTarget())
      .map(e -> advertisement.getAdUrl())
      .map(e -> advertisement.getDescription())
      .map(e -> advertisement.getStartDate())
      .map(e -> advertisement.getEndDate())
      .isPresent();
    if (!valid) {
      return Mono.error(ExceptionUtils.invalidParam("id"));
    }
    return Mono.just(advertisement);
  }
}
