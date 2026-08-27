package com.pos.catalog.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.catalog.dto.CategoryRequest;
import com.pos.catalog.dto.CategoryResponse;
import com.pos.catalog.entity.Category;
import com.pos.catalog.repository.CategoryRepository;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private static final int MAX_DEPTH = 3;

    private final CategoryRepository categoryRepository;
    private final AuditRecorder auditRecorder;

    public CategoryService(CategoryRepository categoryRepository, AuditRecorder auditRecorder) {
        this.categoryRepository = categoryRepository;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category parent = null;
        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Parent category not found."));
            validateDepth(parent);
            if (categoryRepository.existsByNameAndParentId(request.name(), request.parentId())) {
                throw new ApiException(ErrorCode.CONFLICT, "Sibling category with this name already exists.");
            }
        } else {
            if (categoryRepository.existsByNameAndParentIsNull(request.name())) {
                throw new ApiException(ErrorCode.CONFLICT, "Root category with this name already exists.");
            }
        }

        Category category = new Category();
        category.setName(request.name());
        category.setDescription(request.description());
        category.setParent(parent);
        if (request.isActive() != null) {
            category.setActive(request.isActive());
        }

        Category saved = categoryRepository.save(category);

        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "CATEGORY_CREATED",
                "Category",
                saved.getId()
        ));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Category not found."));

        if (request.parentId() != null) {
            if (request.parentId().equals(category.getId())) {
                throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Category cannot be its own parent.");
            }
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Parent category not found."));

            // Check for circular dependency
            Category curr = parent;
            while (curr != null) {
                if (curr.getId().equals(category.getId())) {
                    throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "Circular parent dependency detected.");
                }
                curr = curr.getParent();
            }

            validateDepth(parent);

            if (categoryRepository.existsByNameAndParentIdAndIdNot(request.name(), request.parentId(), id)) {
                throw new ApiException(ErrorCode.CONFLICT, "Sibling category with this name already exists.");
            }
            category.setParent(parent);
        } else {
            if (categoryRepository.existsByNameAndParentIsNullAndIdNot(request.name(), id)) {
                throw new ApiException(ErrorCode.CONFLICT, "Root category with this name already exists.");
            }
            category.setParent(null);
        }

        category.setName(request.name());
        category.setDescription(request.description());
        if (request.isActive() != null) {
            category.setActive(request.isActive());
        }

        Category saved = categoryRepository.save(category);

        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(userDetails.getId()),
                "CATEGORY_UPDATED",
                "Category",
                saved.getId()
        ));
        return CategoryResponse.from(saved);
    }

    private void validateDepth(Category parent) {
        int depth = 1; // The new category itself is level 1
        Category curr = parent;
        while (curr != null) {
            depth++;
            curr = curr.getParent();
        }
        if (depth > MAX_DEPTH) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Category hierarchy cannot exceed " + MAX_DEPTH + " levels.");
        }
    }
}
