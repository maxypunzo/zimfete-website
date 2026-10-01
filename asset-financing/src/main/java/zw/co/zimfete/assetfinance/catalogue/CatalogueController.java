package zw.co.zimfete.assetfinance.catalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import zw.co.zimfete.assetfinance.security.AppUser;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;
import zw.co.zimfete.assetfinance.web.NotFoundException;

/** Asset catalogue, suppliers and supplier quotes. Everyone can read; only ADMIN can change. */
@RestController
@RequestMapping("/api")
@Transactional
public class CatalogueController {

    private final AssetCatalogueItemRepository items;
    private final SupplierRepository suppliers;
    private final SupplierQuoteRepository quotes;

    public CatalogueController(AssetCatalogueItemRepository items, SupplierRepository suppliers,
                               SupplierQuoteRepository quotes) {
        this.items = items;
        this.suppliers = suppliers;
        this.quotes = quotes;
    }

    public record ItemRequest(@NotBlank String code, @NotBlank String name, @NotNull AssetCategory category,
                              String description, @NotNull @DecimalMin("0.01") BigDecimal standardCost,
                              @NotNull @Pattern(regexp = "[A-Z]{3}") String currency, Boolean active) {
    }

    public record ItemView(Long id, String code, String name, AssetCategory category, String description,
                           BigDecimal standardCost, String currency, boolean active) {
        static ItemView of(AssetCatalogueItem i) {
            return new ItemView(i.getId(), i.getCode(), i.getName(), i.getCategory(), i.getDescription(),
                    i.getStandardCost(), i.getCurrency(), i.isActive());
        }
    }

    @GetMapping("/catalogue/items")
    @Transactional(readOnly = true)
    public List<ItemView> listItems(@RequestParam(defaultValue = "false") boolean includeInactive) {
        List<AssetCatalogueItem> result = includeInactive ? items.findAll() : items.findByActiveTrueOrderByName();
        return result.stream().map(ItemView::of).toList();
    }

    @PostMapping("/catalogue/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ItemView createItem(@Valid @RequestBody ItemRequest r) {
        if (items.existsByCode(r.code())) {
            throw new BusinessRuleException("Catalogue code " + r.code() + " already exists");
        }
        return ItemView.of(items.save(new AssetCatalogueItem(r.code(), r.name(), r.category(), r.description(),
                r.standardCost(), r.currency())));
    }

    /** Changing the standard cost does not affect existing applications; use reprice for those. */
    @PutMapping("/catalogue/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ItemView updateItem(@PathVariable long id, @Valid @RequestBody ItemRequest r) {
        AssetCatalogueItem item = items.findById(id).orElseThrow(() -> new NotFoundException("Catalogue item", id));
        item.update(r.name(), r.category(), r.description(), r.standardCost(), r.currency(),
                r.active() == null || r.active());
        return ItemView.of(item);
    }

    public record SupplierRequest(@NotBlank String name, String phone, String email, String address, Boolean active) {
    }

    public record SupplierView(Long id, String name, String phone, String email, String address, boolean active) {
        static SupplierView of(Supplier s) {
            return new SupplierView(s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getAddress(), s.isActive());
        }
    }

    @GetMapping("/suppliers")
    @Transactional(readOnly = true)
    public List<SupplierView> listSuppliers() {
        return suppliers.findAll().stream().map(SupplierView::of).toList();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierView createSupplier(@Valid @RequestBody SupplierRequest r) {
        return SupplierView.of(suppliers.save(new Supplier(r.name(), r.phone(), r.email(), r.address())));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierView updateSupplier(@PathVariable long id, @Valid @RequestBody SupplierRequest r) {
        Supplier s = suppliers.findById(id).orElseThrow(() -> new NotFoundException("Supplier", id));
        s.update(r.name(), r.phone(), r.email(), r.address(), r.active() == null || r.active());
        return SupplierView.of(s);
    }

    public record QuoteRequest(@NotNull Long supplierId, @NotNull @DecimalMin("0.01") BigDecimal price,
                               @NotNull LocalDate quoteDate, @NotNull LocalDate validUntil, String reference) {
    }

    public record QuoteView(Long id, Long catalogueItemId, Long supplierId, String supplierName, BigDecimal price,
                            String currency, LocalDate quoteDate, LocalDate validUntil, String reference) {
        static QuoteView of(SupplierQuote q) {
            return new QuoteView(q.getId(), q.getCatalogueItem().getId(), q.getSupplier().getId(),
                    q.getSupplier().getName(), q.getPrice(), q.getCurrency(), q.getQuoteDate(),
                    q.getValidUntil(), q.getReference());
        }
    }

    @GetMapping("/catalogue/items/{id}/quotes")
    @Transactional(readOnly = true)
    public List<QuoteView> listQuotes(@PathVariable long id) {
        return quotes.findByCatalogueItemIdOrderByQuoteDateDesc(id).stream().map(QuoteView::of).toList();
    }

    @PostMapping("/catalogue/items/{id}/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public QuoteView addQuote(@PathVariable long id, @Valid @RequestBody QuoteRequest r,
                              @AuthenticationPrincipal AppUser user) {
        AssetCatalogueItem item = items.findById(id).orElseThrow(() -> new NotFoundException("Catalogue item", id));
        Supplier supplier = suppliers.findById(r.supplierId())
                .orElseThrow(() -> new NotFoundException("Supplier", r.supplierId()));
        if (r.validUntil().isBefore(r.quoteDate())) {
            throw new BusinessRuleException("Quote valid-until date is before the quote date");
        }
        return QuoteView.of(quotes.save(new SupplierQuote(item, supplier, r.price(), item.getCurrency(),
                r.quoteDate(), r.validUntil(), r.reference(), user.username())));
    }
}
