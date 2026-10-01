package zw.co.zimfete.assetfinance.catalogue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A supplier's price for a catalogue item, valid until a given date. */
@Entity
@Table(name = "supplier_quote")
public class SupplierQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "catalogue_item_id")
    private AssetCatalogueItem catalogueItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
    private BigDecimal price;
    private String currency;
    private LocalDate quoteDate;
    private LocalDate validUntil;
    private String reference;
    private String createdBy;
    private Instant createdAt;

    protected SupplierQuote() {
    }

    public SupplierQuote(AssetCatalogueItem catalogueItem, Supplier supplier, BigDecimal price, String currency,
                         LocalDate quoteDate, LocalDate validUntil, String reference, String createdBy) {
        this.catalogueItem = catalogueItem;
        this.supplier = supplier;
        this.price = price;
        this.currency = currency;
        this.quoteDate = quoteDate;
        this.validUntil = validUntil;
        this.reference = reference;
        this.createdBy = createdBy;
    }

    public boolean isValidOn(LocalDate date) {
        return !date.isBefore(quoteDate) && !date.isAfter(validUntil);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AssetCatalogueItem getCatalogueItem() { return catalogueItem; }
    public Supplier getSupplier() { return supplier; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public LocalDate getQuoteDate() { return quoteDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getReference() { return reference; }
}
