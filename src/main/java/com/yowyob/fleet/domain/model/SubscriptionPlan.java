package com.yowyob.fleet.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class SubscriptionPlan {
    private UUID id;
    private String name;
    private String description;
    private int maxFleets;
    private int maxVehicles;
    private int maxDrivers;
    private BigDecimal monthlyPrice;
    private BigDecimal annualPrice;
    private String currency;
    private String features;
    private boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    /** Ordre d'affichage (croissant) dans le catalogue. */
    private int sortOrder;
    /** Plan mis en avant (« recommandé ») sur les pages de choix. */
    private boolean recommended;
    /** Clés des fonctionnalités activées (source de vérité : fleet.plan_features). */
    private java.util.List<String> includedFeatures = java.util.List.of();

    public SubscriptionPlan(UUID id, String name, String description,
                            int maxFleets, int maxVehicles, int maxDrivers,
                            BigDecimal monthlyPrice, BigDecimal annualPrice, String currency,
                            String features, boolean isActive, Instant createdAt, Instant updatedAt) {
        this.id = id; this.name = name; this.description = description;
        this.maxFleets = maxFleets; this.maxVehicles = maxVehicles; this.maxDrivers = maxDrivers;
        this.monthlyPrice = monthlyPrice; this.annualPrice = annualPrice; this.currency = currency;
        this.features = features; this.isActive = isActive;
        this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getMaxFleets() { return maxFleets; }
    public int getMaxVehicles() { return maxVehicles; }
    public int getMaxDrivers() { return maxDrivers; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public BigDecimal getAnnualPrice() { return annualPrice; }
    public String getCurrency() { return currency; }
    public String getFeatures() { return features; }
    @JsonProperty("isActive")
    public boolean isActive() { return isActive; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setMaxFleets(int maxFleets) { this.maxFleets = maxFleets; }
    public void setMaxVehicles(int maxVehicles) { this.maxVehicles = maxVehicles; }
    public void setMaxDrivers(int maxDrivers) { this.maxDrivers = maxDrivers; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }
    public void setAnnualPrice(BigDecimal annualPrice) { this.annualPrice = annualPrice; }
    public void setFeatures(String features) { this.features = features; }
    public void setActive(boolean active) { isActive = active; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean recommended) { this.recommended = recommended; }
    public java.util.List<String> getIncludedFeatures() { return includedFeatures; }
    public void setIncludedFeatures(java.util.List<String> includedFeatures) { this.includedFeatures = includedFeatures; }

    /** Plan gratuit : aucun paiement, abonnement sans date de fin. */
    @JsonProperty("free")
    public boolean isFree() { return monthlyPrice == null || monthlyPrice.signum() == 0; }
}
