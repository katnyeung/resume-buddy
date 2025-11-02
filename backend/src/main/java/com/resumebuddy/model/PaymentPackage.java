package com.resumebuddy.model;

import java.math.BigDecimal;

/**
 * Enum defining available credit packages for purchase
 * Prices in GBP (British Pounds)
 */
public enum PaymentPackage {
    SMALL("200", new BigDecimal("2.00"), new BigDecimal("200")),
    MEDIUM("500", new BigDecimal("5.00"), new BigDecimal("500")),
    LARGE("1000", new BigDecimal("8.00"), new BigDecimal("1000"));

    private final String packageId;
    private final BigDecimal priceGbp;
    private final BigDecimal credits;

    PaymentPackage(String packageId, BigDecimal priceGbp, BigDecimal credits) {
        this.packageId = packageId;
        this.priceGbp = priceGbp;
        this.credits = credits;
    }

    public String getPackageId() {
        return packageId;
    }

    public BigDecimal getPriceGbp() {
        return priceGbp;
    }

    public BigDecimal getCredits() {
        return credits;
    }

    /**
     * Get package by ID (200, 500, 1000)
     */
    public static PaymentPackage fromPackageId(String packageId) {
        for (PaymentPackage pkg : values()) {
            if (pkg.packageId.equals(packageId)) {
                return pkg;
            }
        }
        throw new IllegalArgumentException("Invalid package ID: " + packageId);
    }
}
