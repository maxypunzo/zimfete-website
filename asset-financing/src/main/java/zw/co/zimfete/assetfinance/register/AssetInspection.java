package zw.co.zimfete.assetfinance.register;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
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

/** A field visit to check a financed asset. */
@Entity
@Table(name = "asset_inspection")
public class AssetInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id")
    private FinancedAsset asset;
    private LocalDate inspectedOn;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "asset_condition")
    private AssetCondition condition;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String notes;
    private String inspectedBy;
    private Instant createdAt;

    protected AssetInspection() {
    }

    public AssetInspection(FinancedAsset asset, LocalDate inspectedOn, AssetCondition condition, BigDecimal latitude,
                           BigDecimal longitude, String notes, String inspectedBy) {
        this.asset = asset;
        this.inspectedOn = inspectedOn;
        this.condition = condition;
        this.latitude = latitude;
        this.longitude = longitude;
        this.notes = notes;
        this.inspectedBy = inspectedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public LocalDate getInspectedOn() { return inspectedOn; }
    public AssetCondition getCondition() { return condition; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getNotes() { return notes; }
    public String getInspectedBy() { return inspectedBy; }
}
