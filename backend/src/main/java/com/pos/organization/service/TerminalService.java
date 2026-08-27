package com.pos.organization.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.organization.domain.Store;
import com.pos.organization.domain.Terminal;
import com.pos.organization.dto.TerminalRequest;
import com.pos.organization.dto.TerminalResponse;
import com.pos.organization.repository.StoreRepository;
import com.pos.organization.repository.TerminalRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TerminalService {

    private final TerminalRepository terminalRepository;
    private final StoreRepository storeRepository;
    private final AuditRecorder auditRecorder;

    public TerminalService(TerminalRepository terminalRepository, StoreRepository storeRepository, AuditRecorder auditRecorder) {
        this.terminalRepository = terminalRepository;
        this.storeRepository = storeRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<TerminalResponse> listTerminals(UUID storeId) {
        // Assume storeId authorization happens at the controller level
        return terminalRepository.findByStoreId(storeId).stream()
                .map(TerminalResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TerminalResponse getTerminal(UUID storeId, UUID id) {
        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Terminal not found"));
        
        if (!terminal.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Terminal not found in this store");
        }
        
        return TerminalResponse.from(terminal);
    }

    public TerminalResponse createTerminal(UUID storeId, TerminalRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        Terminal terminal = new Terminal(store, request.code(), request.name(), request.status());
        Terminal savedTerminal = terminalRepository.save(terminal);

        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "TERMINAL_CREATED",
                "Terminal",
                savedTerminal.getId()
        ));

        return TerminalResponse.from(savedTerminal);
    }

    public TerminalResponse updateTerminal(UUID storeId, UUID id, TerminalRequest request) {
        Terminal terminal = terminalRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Terminal not found"));

        if (!terminal.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Terminal not found in this store");
        }

        terminal.setCode(request.code());
        terminal.setName(request.name());
        terminal.setStatus(request.status());

        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "TERMINAL_UPDATED",
                "Terminal",
                terminal.getId()
        ));

        return TerminalResponse.from(terminal);
    }
}
