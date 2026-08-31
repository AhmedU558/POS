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
import com.pos.register.domain.RegisterClosing;
import com.pos.register.dto.CashMovementRequest;
import com.pos.register.dto.CashMovementResponse;
import com.pos.register.dto.RegisterClosingReportResponse;
import com.pos.register.dto.RegisterSessionCloseRequest;
import com.pos.register.dto.RegisterSessionOpenRequest;
import com.pos.register.dto.RegisterSessionResponse;
import com.pos.register.dto.RegisterSessionSummaryResponse;
import com.pos.register.repository.RegisterClosingRepository;
import com.pos.sales.domain.CashTransaction;
import com.pos.sales.repository.CashTransactionRepository;
import com.pos.users.domain.User;
import com.pos.users.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.UUID;

@Service
public class RegisterSessionService {

    private final RegisterRepository registerRepository;
    private final RegisterSessionRepository sessionRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final RegisterClosingRepository closingRepository;
    private final UserRepository userRepository;
    private final StoreScopeEvaluator storeScopeEvaluator;
    private final AuditRecorder auditRecorder;

    public RegisterSessionService(
            RegisterRepository registerRepository,
            RegisterSessionRepository sessionRepository,
            CashTransactionRepository cashTransactionRepository,
            RegisterClosingRepository closingRepository,
            UserRepository userRepository,
            StoreScopeEvaluator storeScopeEvaluator,
            AuditRecorder auditRecorder) {
        this.registerRepository = registerRepository;
        this.sessionRepository = sessionRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.closingRepository = closingRepository;
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

    @Transactional(readOnly = true)
    public RegisterSessionSummaryResponse summary(UUID id) {
        RegisterSession session = sessionRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register session not found"));
        if (!storeScopeEvaluator.canAccess(session.getRegister().getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        return toSummary(session);
    }

    @Transactional
    public RegisterClosingReportResponse close(UUID id, RegisterSessionCloseRequest request) {
        RegisterSession session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register session not found"));
        if (!storeScopeEvaluator.canAccess(session.getRegister().getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        if (!session.isOpen()) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Register session is already closed");
        }

        RegisterSessionSummaryResponse live = toSummary(session);
        BigDecimal actual = money(request.actualCash());
        BigDecimal expected = live.expectedCash();
        BigDecimal variance = money(actual.subtract(expected));

        session.setExpectedCash(expected);
        session.setActualCash(actual);
        session.setVariance(variance);
        session.setClosedAt(OffsetDateTime.now());
        session.setStatus(RegisterSession.STATUS_CLOSED);
        sessionRepository.save(session);

        User actor = currentUser();
        RegisterClosing closing = new RegisterClosing();
        closing.setRegisterSession(session);
        closing.setZReportNumber("Z-" + Year.now() + "-" + String.format("%06d", closingRepository.nextZReportSequence()));
        closing.setNotes(blankToNull(request.notes()));
        RegisterClosing saved = closingRepository.save(closing);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(actor.getId()),
                "REGISTER_SESSION_CLOSED",
                "RegisterSession",
                session.getId()));

        return toClosingReport(session, saved, live);
    }

    @Transactional(readOnly = true)
    public RegisterClosingReportResponse closingReport(UUID id) {
        RegisterSession session = sessionRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register session not found"));
        if (!storeScopeEvaluator.canAccess(session.getRegister().getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        RegisterClosing closing = closingRepository.findByRegisterSession_Id(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Closing report not found"));
        return toClosingReport(session, closing, toSummary(session));
    }

    private RegisterClosingReportResponse toClosingReport(
            RegisterSession session,
            RegisterClosing closing,
            RegisterSessionSummaryResponse live) {
        return new RegisterClosingReportResponse(
                session.getId(),
                closing.getZReportNumber(),
                session.getStatus(),
                live.openingCash(),
                live.cashInTotal(),
                live.cashOutTotal(),
                live.cashSalesTotal(),
                session.getExpectedCash() == null ? live.expectedCash() : session.getExpectedCash(),
                session.getActualCash(),
                session.getVariance(),
                closing.getNotes(),
                session.getOpenedAt(),
                session.getClosedAt());
    }

    private RegisterSessionSummaryResponse toSummary(RegisterSession session) {
        BigDecimal opening = money(session.getOpeningCash() == null ? BigDecimal.ZERO : session.getOpeningCash());
        BigDecimal cashIn = money(cashTransactionRepository.sumAmount(session.getId(), CashTransaction.TYPE_CASH_IN));
        BigDecimal cashOut = money(cashTransactionRepository.sumAmount(session.getId(), CashTransaction.TYPE_CASH_OUT));
        BigDecimal cashSales = money(cashTransactionRepository.sumAmount(session.getId(), CashTransaction.TYPE_SALE));
        BigDecimal expected = money(opening.add(cashIn).subtract(cashOut).add(cashSales));
        return new RegisterSessionSummaryResponse(
                session.getId(),
                session.getRegister().getId(),
                session.getRegister().getStore().getId(),
                session.getRegister().getTerminal().getId(),
                session.getCashier().getId(),
                session.getStatus(),
                opening,
                cashIn,
                cashOut,
                cashSales,
                expected,
                session.getOpenedAt(),
                session.getClosedAt());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
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

    @Transactional
    public CashMovementResponse cashIn(UUID sessionId, CashMovementRequest request) {
        return recordMovement(sessionId, request, CashTransaction.TYPE_CASH_IN, "CASH_IN_RECORDED");
    }

    @Transactional
    public CashMovementResponse cashOut(UUID sessionId, CashMovementRequest request) {
        return recordMovement(sessionId, request, CashTransaction.TYPE_CASH_OUT, "CASH_OUT_RECORDED");
    }

    private CashMovementResponse recordMovement(
            UUID sessionId,
            CashMovementRequest request,
            String type,
            String auditAction) {
        RegisterSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Register session not found"));
        if (!storeScopeEvaluator.canAccess(session.getRegister().getStore().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No access to this store");
        }
        if (!session.isOpen()) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Register session is closed");
        }

        User actor = currentUser();
        CashTransaction transaction = new CashTransaction();
        transaction.setRegisterSession(session);
        transaction.setTransactionType(type);
        transaction.setAmount(request.amount().setScale(4, RoundingMode.HALF_UP));
        transaction.setReason(blankToNull(request.reason()));
        transaction.setCreatedBy(actor);
        CashTransaction saved = cashTransactionRepository.save(transaction);

        auditRecorder.record(AuditEvent.of(
                AuditActor.user(actor.getId()),
                auditAction,
                "CashTransaction",
                saved.getId()));

        return CashMovementResponse.fromEntity(saved, session.getId());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTHENTICATION_REQUIRED, "User not found"));
    }
}
