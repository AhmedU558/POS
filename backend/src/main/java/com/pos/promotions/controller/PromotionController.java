package com.pos.promotions.controller;

import com.pos.promotions.dto.PromotionRequest;
import com.pos.promotions.dto.PromotionResponse;
import com.pos.promotions.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTION_READ')")
    public Page<PromotionResponse> list(Pageable pageable) {
        return promotionService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROMOTION_WRITE')")
    public ResponseEntity<PromotionResponse> create(@RequestBody @Valid PromotionRequest request) {
        return ResponseEntity.ok(promotionService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROMOTION_READ')")
    public ResponseEntity<PromotionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(promotionService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROMOTION_WRITE')")
    public ResponseEntity<PromotionResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid PromotionRequest request) {
        return ResponseEntity.ok(promotionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PROMOTION_WRITE')")
    public ResponseEntity<Void> endEarly(@PathVariable UUID id) {
        promotionService.endEarly(id);
        return ResponseEntity.noContent().build();
    }
}
