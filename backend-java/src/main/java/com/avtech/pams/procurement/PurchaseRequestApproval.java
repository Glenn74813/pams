package com.avtech.pams.procurement;

import com.avtech.pams.common.Timestamped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One signature in a purchase request's routing. */
@Entity
@Table(name = "purchase_request_approvals")
public class PurchaseRequestApproval extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_request_id")
    private PurchaseRequest request;

    @Column(nullable = false)
    private int step;

    @Column(name = "role_key", nullable = false)
    private String roleKey;

    @Column(nullable = false)
    private String label;

    @Column(name = "required_permission", nullable = false)
    private String requiredPermission;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "acted_by")
    private Long actedBy;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    private String remarks;

    protected PurchaseRequestApproval() {
    }

    public PurchaseRequestApproval(PurchaseRequest request, int step, String[] stage) {
        this.request = request;
        this.step = step;
        this.roleKey = stage[0];
        this.label = stage[1];
        this.requiredPermission = stage[2];
    }

    public void act(String status, Long by, LocalDateTime at, String remarks) {
        this.status = status;
        this.actedBy = by;
        this.actedAt = at;
        this.remarks = remarks;
    }

    public Long getId() { return id; }
    public int getStep() { return step; }
    public String getRoleKey() { return roleKey; }
    public String getLabel() { return label; }
    public String getRequiredPermission() { return requiredPermission; }
    public String getStatus() { return status; }
    public Long getActedBy() { return actedBy; }
    public LocalDateTime getActedAt() { return actedAt; }
    public String getRemarks() { return remarks; }
}
