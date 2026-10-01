package zw.co.zimfete.assetfinance.catalogue;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** An asset ZimFete finances, with its standard (list) price. */
@Entity
@Table(name = "asset_catalogue_item")
public class AssetCatalogueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private AssetCategory category;
    private String description;
    private BigDecimal standardCost;
    private String currency;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    protected AssetCatalogueItem() {
    }

    public AssetCatalogueItem(String code, String name, AssetCategory category, String description,
                              BigDecimal standardCost, String currency) {
        this.code = code;
        this.active = true;
        update(name, category, description, standardCost, currency, true);
    }

    public void update(String name, AssetCategory category, String description, BigDecimal standardCost,
                       String currency, boolean active) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.standardCost = standardCost;
        this.currency = currency;
        this.active = active;
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
    public String getCode() { return code; }
    public String getName() { return name; }
    public AssetCategory getCategory() { return category; }
    public String getDescription() { return description; }
    public BigDecimal getStandardCost() { return standardCost; }
    public String getCurrency() { return currency; }
    public boolean isActive() { return active; }
}
