package com.pos.register.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import com.pos.common.security.StoreScopeEvaluator;
import com.pos.organization.domain.Register;
import com.pos.organization.domain.RegisterSession;
import com.pos.organization.repository.RegisterRepository;
import com.pos.organization.repository.RegisterSessionRepository;
import com.pos.register.dto.RegisterSessionOpenRequest;
import com.pos.register.dto.RegisterSessionResponse;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.UUID;

@Service
public class RegisterSessionService {

    private final RegisterRepository registerRepository;
    private final RegisterSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final AuditRecorder auditRecorder;

    public RegisterSessionService(
            RegisterRepository registerRepository,
            RegisterSessionRepository sessionRepository,
            UserRepository userRepository,
            StoreScopeEvaluator storeScopeEvaluator,
            AuditRecorder auditRecorder) {
        this.registerRepository = registerRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.storeScopeEvaluator = storeScopeEvaluator;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public RegisterSessionResponse get(UUID id) {
        RegisterSession session = sessionRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register session not found"));
        if (!storeScopeEvaluator.canAccess(session.getRegister().getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return RegisterSessionResponse.fromEntity(session);
    }

    @Transactional
    public RegisterSessionResponse open(UUID registerId, RegisterSessionOpenRequest request) {
        Register register = registerRepository.findById(registerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register not found"));
        if (!storeScopeEvaluator.canAccess(register.getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        if (!"ACTIVE".equals(register.getStatus())) {
            throw new ApiException(ErrorCode.RESOURCE_INACTIVE, "Register is inactive");
        }
        if (sessionRepository.existsByRegister_IdAndStatus(registerId, RegisterSession.STATUS_OPEN)) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Register already has an open session");
        }

        User cashier = currentUser();
        RegisterSession session = new RegisterSession();
        session.setRegister(register);
        session.setCashier(cashier);
        session.setStatus(RegisterSession.STATUS_OPEN);
        session.setOpeningCash(request.openingCash().setScale(4, RoundingMode.HALF_UP));

        RegisterSession saved;
        try {
            saved = sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Register already has an open session");
        }

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(cashier.getId()),
                "REGISTER_SESSION_OPENED",
                "RegisterSession",
                saved.getId()));

        return RegisterSessionResponse.fromEntity(sessionRepository.findDetailedById(saved.getId()).orElse(saved));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));
    }
}
