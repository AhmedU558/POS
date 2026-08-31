package com.pos.promotions.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.organization.domain.Store;
import com.pos.organization.repository.StoreRepository;
import com.pos.promotions.domain.Promotion;
import com.pos.promotions.domain.PromotionRule;
import com.pos.promotions.dto.PromotionRequest;
import com.pos.promotions.dto.PromotionResponse;
import com.pos.promotions.dto.PromotionRuleRequest;
import com.pos.promotions.repository.PromotionRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final StoreRepository storeRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final UserRepository userRepository;
    private final AuditRecorder auditRecorder;

    public PromotionService(
            PromotionRepository promotionRepository,
            StoreRepository storeRepository,
            StoreScopeEvaluator storeScopeEvaluator,
            UserRepository userRepository,
            AuditRecorder auditRecorder) {
        this.promotionRepository = promotionRepository;
        this.storeRepository = storeRepository;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.userRepository = userRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public Page<PromotionResponse> list(Pageable pageable) {
        var storeIds = storeScopeEvaluator.permittedStoreIds();
        if (storeIds.isEmpty()) return Page.empty(pageable);
        return promotionRepository.search(storeIds, pageable).map(PromotionResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public PromotionResponse get(UUID id) {
        return PromotionResponse.fromEntity(requireAccessible(id));
    }

    private Promotion requireAccessible(UUID id) {
        Promotion promotion = promotionRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Promotion not found"));
        if (!storeScopeEvaluator.canAccess(promotion.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return promotion;
    }

    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        if (!storeScopeEvaluator.canAccess(request.storeId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }

        Store store = storeRepository.findById(request.storeId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        Promotion promotion = new Promotion();
        promotion.setStore(store);
        promotion.setName(request.name());
        promotion.setDescription(request.description());
        promotion.setType(request.type());
        promotion.setDiscountValue(request.discountValue());
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());
        promotion.setActive(request.active());
        promotion.setPriority(request.priority());
        promotion.setStackable(request.stackable());
        promotion.setCreatedBy(currentUser());

        if (request.rules() != null) {
            for (PromotionRuleRequest ruleReq : request.rules()) {
                PromotionRule rule = new PromotionRule();
                rule.setRuleType(ruleReq.ruleType());
                rule.setRuleValue(ruleReq.ruleValue());
                promotion.addRule(rule);
            }
        }

        Promotion saved = promotionRepository.save(promotion);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "PROMOTION_CREATED",
                "Promotion",
                saved.getId()));

        return PromotionResponse.fromEntity(saved);
    }

    @Transactional
    public PromotionResponse update(UUID id, PromotionRequest request) {
        Promotion promotion = requireAccessible(id);
        
        promotion.setName(request.name());
        promotion.setDescription(request.description());
        promotion.setType(request.type());
        promotion.setDiscountValue(request.discountValue());
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());
        promotion.setActive(request.active());
        promotion.setPriority(request.priority());
        promotion.setStackable(request.stackable());

        promotion.getRules().clear();
        if (request.rules() != null) {
            for (PromotionRuleRequest ruleReq : request.rules()) {
                PromotionRule rule = new PromotionRule();
                rule.setRuleType(ruleReq.ruleType());
                rule.setRuleValue(ruleReq.ruleValue());
                promotion.addRule(rule);
            }
        }

        Promotion saved = promotionRepository.save(promotion);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "PROMOTION_UPDATED",
                "Promotion",
                saved.getId()));

        return PromotionResponse.fromEntity(saved);
    }

    @Transactional
    public void endEarly(UUID id) {
        Promotion promotion = requireAccessible(id);
        promotion.setActive(false);
        promotionRepository.save(promotion);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(currentUser().getId()),
                "PROMOTION_ENDED",
                "Promotion",
                promotion.getId()));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));
    }
}
