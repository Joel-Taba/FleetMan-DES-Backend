package com.yowyob.fleet.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entité de domaine : Détail d'un trajet (ligne du tableau de mission).
 * Représente passagers, marchandises ou autres éléments transportés.
 */
public class TripDetail {

    public enum ItemType { PASSENGER, CARGO, OTHER }

    private UUID id;
    private UUID tripId;
    private ItemType itemType;
    private String description;
    private int quantity;
    private BigDecimal weight;           // En kg, optionnel
    private Integer departureQuantity;   // Quantité au départ
    private Integer returnQuantity;      // Quantité au retour (vérification)
    private Integer deliveredQuantity;   // Quantité livrée (ou ramassée) à l'arrêt
    private UUID stopId;                 // Arrêt de la tournée où la ligne est traitée
    private String shortageReason;       // Motif d'un écart entre prévu et traité
    private int sortOrder;

    public TripDetail(UUID id, UUID tripId, ItemType itemType, String description,
                      int quantity, BigDecimal weight,
                      Integer departureQuantity, Integer returnQuantity, int sortOrder) {
        this.id = id;
        this.tripId = tripId;
        this.itemType = itemType;
        this.description = description;
        this.quantity = quantity;
        this.weight = weight;
        this.departureQuantity = departureQuantity;
        this.returnQuantity = returnQuantity;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public UUID getTripId() { return tripId; }
    public ItemType getItemType() { return itemType; }
    public String getDescription() { return description; }
    public int getQuantity() { return quantity; }
    public BigDecimal getWeight() { return weight; }
    public Integer getDepartureQuantity() { return departureQuantity; }
    public Integer getReturnQuantity() { return returnQuantity; }
    public int getSortOrder() { return sortOrder; }
    public Integer getDeliveredQuantity() { return deliveredQuantity; }
    public void setDeliveredQuantity(Integer deliveredQuantity) { this.deliveredQuantity = deliveredQuantity; }
    public UUID getStopId() { return stopId; }
    public void setStopId(UUID stopId) { this.stopId = stopId; }
    public String getShortageReason() { return shortageReason; }
    public void setShortageReason(String shortageReason) { this.shortageReason = shortageReason; }

    public void setId(UUID id) { this.id = id; }
    public void setReturnQuantity(Integer returnQuantity) { this.returnQuantity = returnQuantity; }
}
