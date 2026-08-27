package com.pos.catalog.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.catalog.dto.UnitRequest;
import com.pos.catalog.dto.UnitResponse;
import com.pos.catalog.entity.Unit;
import com.pos.catalog.repository.UnitRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UnitService {
    private final UnitRepository unitRepository;
    private final AuditRecorder auditRecorder;

    public UnitService(UnitRepository unitRepository, AuditRecorder auditRecorder) {
        this.unitRepository = unitRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<UnitResponse> listUnits() {
        return unitRepository.findAll().stream()
                .map(UnitResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public UnitResponse createUnit(UnitRequest request) {
        if (unitRepository.existsByCode(request.code())) {
            throw new ApiException(ErrorCode.CONFLICT, "Unit with this code already exists.");
        }
        if (unitRepository.existsByName(request.name())) {
            throw new ApiException(ErrorCode.CONFLICT, "Unit with this name already exists.");
        }

        Unit unit = new Unit();
        unit.setCode(request.code());
        unit.setName(request.name());
        if (request.isActive() != null) {
            unit.setActive(request.isActive());
        }

        Unit saved = unitRepository.save(unit);

        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "UNIT_CREATED",
                "Unit",
                saved.getId()
        ));
        return UnitResponse.from(saved);
    }

    @Transactional
    public UnitResponse updateUnit(UUID id, UnitRequest request) {
        Unit unit = unitRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Unit not found."));

        if (unitRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Unit with this code already exists.");
        }
        if (unitRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "Unit with this name already exists.");
        }

        unit.setCode(request.code());
        unit.setName(request.name());
        if (request.isActive() != null) {
            unit.setActive(request.isActive());
        }

        Unit saved = unitRepository.save(unit);

        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "UNIT_UPDATED",
                "Unit",
                saved.getId()
        ));
        return UnitResponse.from(saved);
    }
}
