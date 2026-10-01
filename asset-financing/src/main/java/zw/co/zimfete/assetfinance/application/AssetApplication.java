package zw.co.zimfete.assetfinance.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import zw.co.zimfete.assetfinance.catalogue.AssetCatalogueItem;
import zw.co.zimfete.assetfinance.catalogue.SupplierQuote;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;

/**
 * A member's application to acquire one asset. The deposit balance itself lives in a dedicated
 * Fineract savings account; this record tracks the workflow around it and every status rule.
 */
@Entity
@Table(name = "asset_application")
public class AssetApplication {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String reference;
    private long fineractClientId;
    private String memberName;
    private long officeId;
    private Long officerStaffId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalogue_item_id")
    private AssetCatalogueItem catalogueItem;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id")
    private SupplierQuote quote;
    private BigDecimal assetCost;
    private String currency;
    private BigDecimal depositPercent;
    private BigDecimal depositTarget;
    private long fineractSavingsAccountId;
    private BigDecimal depositedAmount;
    private BigDecimal avgMonthlyDeposit;
    private LocalDate estimatedTargetDate;
    private Instant balanceSyncedAt;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ApplicationStatus status;
    private LocalDate openedOn;
    private Instant qualifiedAt;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private ConversionStep conversionStep;
    private BigDecimal depositApplied;
    private Long depositWithdrawalTxId;
    private BigDecimal financedAmount;
    private Long fineractLoanId;
    private String cancelReason;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    @Version
    private long version;

    protected AssetApplication() {
    }

    public AssetApplication(String reference, long fineractClientId, String memberName, long officeId,
                            Long officerStaffId, AssetCatalogueItem catalogueItem, SupplierQuote quote,
                            BigDecimal assetCost, String currency, BigDecimal depositPercent,
                            long fineractSavingsAccountId, LocalDate openedOn, String createdBy) {
        this.reference = reference;
        this.fineractClientId = fineractClientId;
        this.memberName = memberName;
        this.officeId = officeId;
        this.officerStaffId = officerStaffId;
        this.catalogueItem = catalogueItem;
        this.quote = quote;
        this.currency = currency;
        this.fineractSavingsAccountId = fineractSavingsAccountId;
        this.openedOn = openedOn;
        this.createdBy = createdBy;
        this.status = ApplicationStatus.SAVING;
        this.conversionStep = ConversionStep.NOT_STARTED;
        this.depositedAmount = BigDecimal.ZERO;
        this.avgMonthlyDeposit = BigDecimal.ZERO;
        setCost(assetCost, depositPercent);
    }

    public static BigDecimal depositTarget(BigDecimal cost, BigDecimal percent) {
        return cost.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private void setCost(BigDecimal cost, BigDecimal percent) {
        if (cost.signum() <= 0) {
            throw new BusinessRuleException("Asset cost must be positive");
        }
        this.assetCost = cost;
        this.depositPercent = percent;
        this.depositTarget = depositTarget(cost, percent);
    }

    /**
     * Records the latest balance from Fineract and moves the member into or out of the queue.
     *
     * @return true if this update made the member qualify (so they can be notified once).
     */
    public boolean recordBalance(Progress progress, Instant now) {
        this.depositedAmount = progress.balance();
        this.avgMonthlyDeposit = progress.averageMonthlyDeposit();
        this.estimatedTargetDate = progress.estimatedTargetDate();
        this.balanceSyncedAt = now;
        return evaluateQualification(now);
    }

    private boolean evaluateQualification(Instant now) {
        boolean reached = depositedAmount.compareTo(depositTarget) >= 0;
        if (status == ApplicationStatus.SAVING && reached) {
            status = ApplicationStatus.QUALIFIED;
            qualifiedAt = now;
            return true;
        }
        if (status == ApplicationStatus.QUALIFIED && !reached) {
            // A withdrawal or a price increase took the member back below target: they lose their place.
            status = ApplicationStatus.SAVING;
            qualifiedAt = null;
        }
        return false;
    }

    /** Applies a new supplier price to an application that has not gone to procurement yet. */
    public boolean reprice(BigDecimal newCost, SupplierQuote newQuote, Instant now) {
        requireStatus("reprice", ApplicationStatus.SAVING, ApplicationStatus.QUALIFIED);
        setCost(newCost, depositPercent);
        this.quote = newQuote;
        return evaluateQualification(now);
    }

    public void startProcurement() {
        requireStatus("start procurement", ApplicationStatus.QUALIFIED);
        status = ApplicationStatus.PROCUREMENT;
    }

    /** A purchase order was cancelled before delivery: back into the queue at the same position. */
    public void returnToQueue() {
        requireStatus("return to queue", ApplicationStatus.PROCUREMENT);
        status = ApplicationStatus.QUALIFIED;
    }

    public void markDelivered() {
        requireStatus("record delivery", ApplicationStatus.PROCUREMENT);
        status = ApplicationStatus.DELIVERED;
    }

    public void cancel(String reason) {
        if (!status.isOpen()) {
            throw new BusinessRuleException("Only applications that are saving or in the queue can be cancelled");
        }
        status = ApplicationStatus.CANCELLED;
        cancelReason = reason;
        qualifiedAt = null;
    }

    // --- Conversion (see ConversionService) ---

    public void beginApplyingDeposit(BigDecimal amount) {
        requireStatus("convert", ApplicationStatus.DELIVERED);
        if (conversionStep != ConversionStep.NOT_STARTED) {
            throw new IllegalStateException("Deposit already being applied");
        }
        if (amount.compareTo(depositTarget) < 0) {
            throw new BusinessRuleException("Deposit balance " + amount + " is below the target " + depositTarget
                    + "; the member must top up before conversion");
        }
        this.depositApplied = amount.min(assetCost);
        this.financedAmount = assetCost.subtract(depositApplied);
        this.conversionStep = ConversionStep.APPLYING_DEPOSIT;
    }

    public void depositApplied(long withdrawalTxId) {
        this.depositWithdrawalTxId = withdrawalTxId;
        this.conversionStep = ConversionStep.DEPOSIT_APPLIED;
        if (financedAmount.signum() == 0) {
            // The member deposited the full cost: nothing to finance.
            status = ApplicationStatus.PAID_OFF;
        }
    }

    public void loanCreated(long loanId) {
        this.fineractLoanId = loanId;
        this.conversionStep = ConversionStep.LOAN_CREATED;
    }

    public void loanApproved() {
        this.conversionStep = ConversionStep.LOAN_APPROVED;
    }

    public void loanDisbursed() {
        this.conversionStep = ConversionStep.LOAN_DISBURSED;
        this.status = ApplicationStatus.REPAYING;
    }

    public void markPaidOff() {
        requireStatus("mark paid off", ApplicationStatus.REPAYING);
        status = ApplicationStatus.PAID_OFF;
    }

    private void requireStatus(String action, ApplicationStatus... allowed) {
        for (ApplicationStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new BusinessRuleException("Cannot " + action + " application " + reference + " while it is " + status);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public long getFineractClientId() { return fineractClientId; }
    public String getMemberName() { return memberName; }
    public long getOfficeId() { return officeId; }
    public Long getOfficerStaffId() { return officerStaffId; }
    public AssetCatalogueItem getCatalogueItem() { return catalogueItem; }
    public SupplierQuote getQuote() { return quote; }
    public BigDecimal getAssetCost() { return assetCost; }
    public String getCurrency() { return currency; }
    public BigDecimal getDepositPercent() { return depositPercent; }
    public BigDecimal getDepositTarget() { return depositTarget; }
    public long getFineractSavingsAccountId() { return fineractSavingsAccountId; }
    public BigDecimal getDepositedAmount() { return depositedAmount; }
    public BigDecimal getAvgMonthlyDeposit() { return avgMonthlyDeposit; }
    public LocalDate getEstimatedTargetDate() { return estimatedTargetDate; }
    public Instant getBalanceSyncedAt() { return balanceSyncedAt; }
    public ApplicationStatus getStatus() { return status; }
    public LocalDate getOpenedOn() { return openedOn; }
    public Instant getQualifiedAt() { return qualifiedAt; }
    public ConversionStep getConversionStep() { return conversionStep; }
    public BigDecimal getDepositApplied() { return depositApplied; }
    public Long getDepositWithdrawalTxId() { return depositWithdrawalTxId; }
    public BigDecimal getFinancedAmount() { return financedAmount; }
    public Long getFineractLoanId() { return fineractLoanId; }
    public String getCancelReason() { return cancelReason; }
    public String getCreatedBy() { return createdBy; }
}
