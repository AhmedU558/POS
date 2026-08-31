package com.pos.promotions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "promotion_rules")
public class PromotionRule {

    public static final String RULE_MIN_AMOUNT = "MIN_AMOUNT";
    public static final String RULE_SPECIFIC_PRODUCT = "SPECIFIC_PRODUCT";
    public static final String RULE_SPECIFIC_CATEGORY = "SPECIFIC_CATEGORY";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Column(name = "rule_type", nullable = false, length = 30)
    private String ruleType;

    @Column(name = "rule_value", nullable = false, length = 255)
    private String ruleValue;

    public UUID getId() {
        return id;
    }

    public Promotion getPromotion() {
        return promotion;
    }

    public void setPromotion(Promotion promotion) {
        this.promotion = promotion;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getRuleValue() {
        return ruleValue;
    }

    public void setRuleValue(String ruleValue) {
        this.ruleValue = ruleValue;
    }
}
