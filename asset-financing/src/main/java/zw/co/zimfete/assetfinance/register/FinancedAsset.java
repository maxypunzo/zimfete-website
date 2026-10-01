package zw.co.zimfete.assetfinance.register;

import java.math.BigDecimal;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import zw.co.zimfete.assetfinance.application.AssetApplication;
import zw.co.zimfete.assetfinance.catalogue.AssetCatalogueItem;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;

/** A physical asset that ZimFete financed, where it is, and who owns it. */
@Entity
@Table(name = "financed_asset")
public class FinancedAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private AssetApplication application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalogue_item_id")
    private AssetCatalogueItem catalogueItem;
    private long officeId;
    private long fineractClientId;
    private String serialNumber;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDate deliveredOn;
    private String deliveredBy;
    private boolean memberAcknowledged;
    private String notes;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private Ownership ownership;
    private Instant ownershipChangedAt;
    private String ownershipNote;
    private Instant createdAt;
    @Version
    private long version;

    protected FinancedAsset() {
    }

    public FinancedAsset(AssetApplication application, String serialNumber, BigDecimal latitude,
                         BigDecimal longitude, LocalDate deliveredOn, String deliveredBy, boolean memberAcknowledged,
                         String notes, Instant now) {
        this.application = application;
        this.catalogueItem = application.getCatalogueItem();
        this.officeId = application.getOfficeId();
        this.fineractClientId = application.getFineractClientId();
        this.serialNumber = serialNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.deliveredOn = deliveredOn;
        this.deliveredBy = deliveredBy;
        this.memberAcknowledged = memberAcknowledged;
        this.notes = notes;
        this.ownership = Ownership.SACCO_OWNED;
        this.ownershipChangedAt = now;
    }

    public void transferToMember(Instant now) {
        if (ownership != Ownership.SACCO_OWNED) {
            throw new BusinessRuleException("Asset is " + ownership + "; it cannot be transferred");
        }
        ownership = Ownership.TRANSFERRED_TO_MEMBER;
        ownershipChangedAt = now;
        ownershipNote = "Fully paid";
    }

    public void repossess(String reason, Instant now) {
        if (ownership != Ownership.SACCO_OWNED) {
            throw new BusinessRuleException("Only assets still owned by ZimFete can be repossessed (this one is "
                    + ownership + ")");
        }
        ownership = Ownership.REPOSSESSED;
        ownershipChangedAt = now;
        ownershipNote = reason;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AssetApplication getApplication() { return application; }
    public AssetCatalogueItem getCatalogueItem() { return catalogueItem; }
    public long getOfficeId() { return officeId; }
    public long getFineractClientId() { return fineractClientId; }
    public String getSerialNumber() { return serialNumber; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public LocalDate getDeliveredOn() { return deliveredOn; }
    public String getDeliveredBy() { return deliveredBy; }
    public boolean isMemberAcknowledged() { return memberAcknowledged; }
    public String getNotes() { return notes; }
    public Ownership getOwnership() { return ownership; }
    public Instant getOwnershipChangedAt() { return ownershipChangedAt; }
    public String getOwnershipNote() { return ownershipNote; }
}
