package com.pos.orders.controller;

import com.pos.orders.dto.OnlineOrderRequest;
import com.pos.orders.dto.OnlineOrderResponse;
import com.pos.orders.service.OnlineOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/online-orders")
public class OnlineOrderController {

    private final OnlineOrderService onlineOrderService;

    public OnlineOrderController(OnlineOrderService onlineOrderService) {
        this.onlineOrderService = onlineOrderService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public Page<OnlineOrderResponse> list(Pageable pageable) {
        return onlineOrderService.list(pageable);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    public ResponseEntity<OnlineOrderResponse> create(
            @RequestBody @Valid OnlineOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(onlineOrderService.create(request, idempotencyKey));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<OnlineOrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(onlineOrderService.get(id));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    public ResponseEntity<Void> confirm(@PathVariable UUID id) {
        onlineOrderService.confirm(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    public ResponseEntity<Void> fulfill(@PathVariable UUID id) {
        // According to the spec, PATCH /status might be used for fulfilling.
        onlineOrderService.fulfill(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        onlineOrderService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('ORDER_REFUND')")
    public ResponseEntity<Void> refund(@PathVariable UUID id) {
        onlineOrderService.refund(id);
        return ResponseEntity.noContent().build();
    }
}
