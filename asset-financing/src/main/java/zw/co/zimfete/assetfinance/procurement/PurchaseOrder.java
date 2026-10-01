package zw.co.zimfete.assetfinance.procurement;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import zw.co.zimfete.assetfinance.application.AssetApplication;
import zw.co.zimfete.assetfinance.catalogue.Supplier;
import zw.co.zimfete.assetfinance.catalogue.SupplierQuote;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private AssetApplication application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id")
    private SupplierQuote quote;
    private BigDecimal amount;
    private String currency;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private PurchaseOrderStatus status;
    private String queueOverrideReason;
    private String notes;
    private String createdBy;
    private Instant createdAt;
    private String approvedBy;
    private Instant approvedAt;
    private String cancelledBy;
    private String cancelReason;
    private Instant completedAt;
    @Version
    private long version;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(AssetApplication application, Supplier supplier, SupplierQuote quote,
                         String queueOverrideReason, String notes, String createdBy) {
        this.application = application;
        this.supplier = supplier;
        this.quote = quote;
        this.amount = application.getAssetCost();
        this.currency = application.getCurrency();
        this.queueOverrideReason = queueOverrideReason;
        this.notes = notes;
        this.createdBy = createdBy;
        this.status = PurchaseOrderStatus.DRAFT;
    }

    /** Maker-checker: the person who raised the order cannot approve it. */
    public void approve(String approver, Instant now) {
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new BusinessRuleException("Only draft purchase orders can be approved (this one is " + status + ")");
        }
        if (approver.equals(createdBy)) {
            throw new BusinessRuleException("A purchase order must be approved by someone other than " + createdBy);
        }
        status = PurchaseOrderStatus.APPROVED;
        approvedBy = approver;
        approvedAt = now;
    }

    public void cancel(String by, String reason) {
        if (!status.isActive()) {
            throw new BusinessRuleException("Purchase order is already " + status);
        }
        status = PurchaseOrderStatus.CANCELLED;
        cancelledBy = by;
        cancelReason = reason;
    }

    public void complete(Instant now) {
        if (status != PurchaseOrderStatus.APPROVED) {
            throw new BusinessRuleException("Only approved purchase orders can be completed");
        }
        status = PurchaseOrderStatus.COMPLETED;
        completedAt = now;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AssetApplication getApplication() { return application; }
    public Supplier getSupplier() { return supplier; }
    public SupplierQuote getQuote() { return quote; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PurchaseOrderStatus getStatus() { return status; }
    public String getQueueOverrideReason() { return queueOverrideReason; }
    public String getNotes() { return notes; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getCancelledBy() { return cancelledBy; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCompletedAt() { return completedAt; }
}
