package com.pos.organization.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.organization.domain.Register;
import com.pos.organization.domain.Store;
import com.pos.organization.domain.Terminal;
import com.pos.organization.dto.RegisterRequest;
import com.pos.organization.dto.RegisterResponse;
import com.pos.organization.repository.RegisterRepository;
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
public class RegisterService {

    private final RegisterRepository registerRepository;
    private final TerminalRepository terminalRepository;
    private final StoreRepository storeRepository;
    private final AuditRecorder auditRecorder;

    public RegisterService(RegisterRepository registerRepository, TerminalRepository terminalRepository, StoreRepository storeRepository, AuditRecorder auditRecorder) {
        this.registerRepository = registerRepository;
        this.terminalRepository = terminalRepository;
        this.storeRepository = storeRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<RegisterResponse> listRegisters(UUID storeId) {
        return registerRepository.findByStoreId(storeId).stream()
                .map(RegisterResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RegisterResponse getRegister(UUID storeId, UUID id) {
        Register register = registerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register not found"));
        
        if (!register.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register not found in this store");
        }
        
        return RegisterResponse.from(register);
    }

    public RegisterResponse createRegister(UUID storeId, RegisterRequest request) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found"));

        Terminal terminal = terminalRepository.findById(request.terminalId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Terminal not found"));

        if (!terminal.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Terminal must belong to the same store as the register");
        }

        Register register = new Register(store, terminal, request.code(), request.name(), request.status());
        Register savedRegister = registerRepository.save(register);

        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "REGISTER_CREATED",
                "Register",
                savedRegister.getId()
        ));

        return RegisterResponse.from(savedRegister);
    }

    public RegisterResponse updateRegister(UUID storeId, UUID id, RegisterRequest request) {
        Register register = registerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register not found"));

        if (!register.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register not found in this store");
        }

        Terminal terminal = terminalRepository.findById(request.terminalId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Terminal not found"));

        if (!terminal.getStore().getId().equals(storeId)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Terminal must belong to the same store as the register");
        }

        register.setTerminal(terminal);
        register.setCode(request.code());
        register.setName(request.name());
        register.setStatus(request.status());

        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "REGISTER_UPDATED",
                "Register",
                register.getId()
        ));

        return RegisterResponse.from(register);
    }
}
