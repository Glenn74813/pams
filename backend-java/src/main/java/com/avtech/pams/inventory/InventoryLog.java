package com.avtech.pams.inventory;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A stock movement or catalogue event. The stock card is rebuilt from these rows, so actions and
 * reference types keep the values the Laravel app writes (e.g. "stock_in", "App\Models\Stock").
 */
@Entity
@Table(name = "inventory_logs")
public class InventoryLog extends Timestamped {

    public static final String STOCK_IN = "stock_in";
    public static final String STOCK_EDITED = "stock_edited";
    public static final String TRANSFER = "transfer";
    public static final String ITEM_CREATED = "item_created";
    public static final String ITEM_UPDATED = "item_updated";
    public static final String ITEM_DELETED = "item_deleted";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String action;

    private Integer quantity;

    @Column(name = "previous_quantity")
    private Integer previousQuantity;

    @Column(name = "new_quantity")
    private Integer newQuantity;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    private String notes;

    protected InventoryLog() {
    }

    /** A quantity movement. */
    public static InventoryLog movement(Long itemId, Long userId, String action, int quantity, int before, int after,
                                        String referenceType, Long referenceId, String notes) {
        InventoryLog l = new InventoryLog();
        l.itemId = itemId;
        l.userId = userId;
        l.action = action;
        l.quantity = quantity;
        l.previousQuantity = before;
        l.newQuantity = after;
        l.referenceType = referenceType;
        l.referenceId = referenceId;
        l.notes = notes;
        return l;
    }

    /** A catalogue event with no quantity (item created, edited, deleted). */
    public static InventoryLog event(Long itemId, Long userId, String action, String referenceType, Long referenceId,
                                     String notes) {
        InventoryLog l = new InventoryLog();
        l.itemId = itemId;
        l.userId = userId;
        l.action = action;
        l.referenceType = referenceType;
        l.referenceId = referenceId;
        l.notes = notes;
        return l;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getPreviousQuantity() {
        return previousQuantity;
    }

    public Integer getNewQuantity() {
        return newQuantity;
    }

    public String getNotes() {
        return notes;
    }
}
