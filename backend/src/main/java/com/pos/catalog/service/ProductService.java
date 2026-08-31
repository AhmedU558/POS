package com.pos.catalog.service;

import com.pos.audit.domain.AuditActor;
import com.pos.audit.domain.AuditEvent;
import com.pos.audit.service.AuditRecorder;
import com.pos.auth.security.CustomUserDetails;
import com.pos.catalog.dto.*;
import com.pos.catalog.entity.*;
import com.pos.catalog.repository.*;
import com.pos.common.exception.ApiException;
import com.pos.common.response.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductBarcodeRepository barcodeRepository;
    private final ProductPriceRepository priceRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UnitRepository unitRepository;
    private final AuditRecorder auditRecorder;

    public ProductService(ProductRepository productRepository,
                          ProductBarcodeRepository barcodeRepository,
                          ProductPriceRepository priceRepository,
                          CategoryRepository categoryRepository,
                          BrandRepository brandRepository,
                          UnitRepository unitRepository,
                          AuditRecorder auditRecorder) {
        this.productRepository = productRepository;
        this.barcodeRepository = barcodeRepository;
        this.priceRepository = priceRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.unitRepository = unitRepository;
        this.auditRecorder = auditRecorder;
    }

    private CustomUserDetails getUser() {
        return (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String query, UUID categoryId, UUID brandId, Boolean isActive, Pageable pageable) {
        return productRepository.searchProducts(query == null ? "" : query.trim(), categoryId, brandId, isActive, pageable)
                .map(ProductResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID id) {
        return productRepository.findById(id)
                .map(ProductResponse::fromEntity)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
    }

    public ProductResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new ApiException(ErrorCode.CONFLICT, "SKU already exists");
        }

        Product p = new Product();
        p.setSku(request.sku());
        p.setName(request.name());
        p.setDescription(request.description());

        if (request.categoryId() != null) {
            p.setCategory(categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Category not found")));
        }
        if (request.brandId() != null) {
            p.setBrand(brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Brand not found")));
        }
        if (request.unitId() != null) {
            p.setUnit(unitRepository.findById(request.unitId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Unit not found")));
        }

        p.setPurchasePrice(request.purchasePrice());
        p.setSellingPrice(request.sellingPrice());
        p.setWholesalePrice(request.wholesalePrice());
        p.setTaxRate(request.taxRate());
        p.setMinStock(request.minStock());
        p.setMaxStock(request.maxStock());
        p.setTrackBatch(request.trackBatch());
        p.setTrackExpiry(request.trackExpiry());
        p.setActive(request.isActive());
        p.setImageUrl(request.imageUrl());

        Product saved = productRepository.save(p);
        
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(getUser().getId()),
                "PRODUCT_CREATED",
                "Product",
                saved.getId()
        ));

        return ProductResponse.fromEntity(saved);
    }

    public ProductResponse updateProduct(UUID id, ProductUpdateRequest request) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));

        if (productRepository.existsBySkuAndIdNot(request.sku(), id)) {
            throw new ApiException(ErrorCode.CONFLICT, "SKU already exists");
        }

        p.setSku(request.sku());
        p.setName(request.name());
        p.setDescription(request.description());

        if (request.categoryId() != null) {
            p.setCategory(categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Category not found")));
        } else {
            p.setCategory(null);
        }
        
        if (request.brandId() != null) {
            p.setBrand(brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Brand not found")));
        } else {
            p.setBrand(null);
        }
        
        if (request.unitId() != null) {
            p.setUnit(unitRepository.findById(request.unitId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Unit not found")));
        } else {
            p.setUnit(null);
        }

        p.setPurchasePrice(request.purchasePrice());
        p.setSellingPrice(request.sellingPrice());
        p.setWholesalePrice(request.wholesalePrice());
        p.setTaxRate(request.taxRate());
        p.setMinStock(request.minStock());
        p.setMaxStock(request.maxStock());
        p.setTrackBatch(request.trackBatch());
        p.setTrackExpiry(request.trackExpiry());
        p.setImageUrl(request.imageUrl());

        Product saved = productRepository.save(p);
        
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(getUser().getId()),
                "PRODUCT_UPDATED",
                "Product",
                saved.getId()
        ));

        return ProductResponse.fromEntity(saved);
    }

    public void updateStatus(UUID id, ProductStatusUpdateRequest request) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        
        p.setActive(request.isActive());
        
        productRepository.save(p);
        
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(getUser().getId()),
                "PRODUCT_STATUS_CHANGED",
                "Product",
                p.getId()
        ));
    }

    @Transactional(readOnly = true)
    public List<BarcodeResponse> getBarcodes(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found");
        }
        return barcodeRepository.findByProductId(id)
                .stream().map(BarcodeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public BarcodeResponse addBarcode(UUID id, BarcodeCreateRequest request) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        
        if (barcodeRepository.existsByBarcode(request.barcode())) {
            throw new ApiException(ErrorCode.CONFLICT, "Barcode already exists");
        }

        ProductBarcode pb = new ProductBarcode();
        pb.setProduct(p);
        pb.setBarcode(request.barcode());
        pb.setPrimary(request.isPrimary());
        
        ProductBarcode saved = barcodeRepository.save(pb);
        
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(getUser().getId()),
                "BARCODE_ADDED",
                "ProductBarcode",
                saved.getId()
        ));
                
        return BarcodeResponse.fromEntity(saved);
    }

    public void removeBarcode(UUID id, UUID barcodeId) {
        ProductBarcode pb = barcodeRepository.findById(barcodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Barcode not found"));
                
        if (!pb.getProduct().getId().equals(id)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Barcode does not belong to product");
        }
        
        barcodeRepository.delete(pb);
        
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(getUser().getId()),
                "BARCODE_REMOVED",
                "ProductBarcode",
                barcodeId
        ));
    }

    @Transactional(readOnly = true)
    public List<PriceResponse> getPrices(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found");
        }
        return priceRepository.findByProductIdOrderByEffectiveFromDesc(id)
                .stream().map(PriceResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public PriceResponse addPrice(UUID id, PriceCreateRequest request) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
                
        ProductPrice pp = new ProductPrice();
        pp.setProduct(p);
        pp.setPriceType(request.priceType());
        pp.setAmount(request.amount());
        pp.setEffectiveFrom(request.effectiveFrom());
        pp.setEffectiveTo(request.effectiveTo());
        
        ProductPrice saved = priceRepository.save(pp);
        
        auditRecorder.record(AuditEvent.of(
                AuditActor.user(getUser().getId()),
                "PRICE_RECORD_CREATED",
                "ProductPrice",
                saved.getId()
        ));
                
        return PriceResponse.fromEntity(saved);
    }
}
