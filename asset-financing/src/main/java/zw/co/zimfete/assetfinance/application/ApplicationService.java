package zw.co.zimfete.assetfinance.application;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.co.zimfete.assetfinance.catalogue.AssetCatalogueItem;
import zw.co.zimfete.assetfinance.catalogue.AssetCatalogueItemRepository;
import zw.co.zimfete.assetfinance.catalogue.SupplierQuote;
import zw.co.zimfete.assetfinance.catalogue.SupplierQuoteRepository;
import zw.co.zimfete.assetfinance.config.ZimfeteProperties;
import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractClient.ClientInfo;
import zw.co.zimfete.assetfinance.security.AppUser;
import zw.co.zimfete.assetfinance.security.OfficeAccess;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;
import zw.co.zimfete.assetfinance.web.NotFoundException;

@Service
public class ApplicationService {

    private static final String REF_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AssetApplicationRepository applications;
    private final AssetCatalogueItemRepository items;
    private final SupplierQuoteRepository quotes;
    private final FineractClient fineract;
    private final ApplicationSyncService sync;
    private final OfficeAccess officeAccess;
    private final ZimfeteProperties properties;
    private final Clock clock;

    public ApplicationService(AssetApplicationRepository applications, AssetCatalogueItemRepository items,
                              SupplierQuoteRepository quotes, FineractClient fineract, ApplicationSyncService sync,
                              OfficeAccess officeAccess, ZimfeteProperties properties, Clock clock) {
        this.applications = applications;
        this.items = items;
        this.quotes = quotes;
        this.fineract = fineract;
        this.sync = sync;
        this.officeAccess = officeAccess;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Opens an application and its Asset Deposit Account in Fineract. The account opening fee is
     * charged by Fineract as the product's activation charge. The price is locked from the quote
     * (if given) or the catalogue's standard cost.
     */
    @Transactional
    public AssetApplication open(long clientId, long catalogueItemId, Long quoteId, AppUser user) {
        LocalDate today = LocalDate.now(clock);
        ClientInfo client = fineract.getClient(clientId);
        if (!client.active()) {
            throw new BusinessRuleException("Member " + clientId + " is not active in Fineract");
        }
        officeAccess.check(user, client.officeId());

        AssetCatalogueItem item = items.findById(catalogueItemId)
                .filter(AssetCatalogueItem::isActive)
                .orElseThrow(() -> new NotFoundException("Active catalogue item", catalogueItemId));
        SupplierQuote quote = quoteId == null ? null : validQuote(quoteId, item, today);
        BigDecimal cost = quote == null ? item.getStandardCost() : quote.getPrice();

        String reference = newReference();
        long savingsId = fineract.openAssetDepositAccount(clientId, reference, today);
        return applications.save(new AssetApplication(reference, clientId, client.displayName(), client.officeId(),
                client.staffId(), item, quote, cost, item.getCurrency(), properties.policy().depositPercent(),
                savingsId, today, user.username()));
    }

    @Transactional(readOnly = true)
    public AssetApplication get(long id, AppUser user) {
        AssetApplication app = applications.findById(id).orElseThrow(() -> new NotFoundException("Application", id));
        officeAccess.check(user, app.getOfficeId());
        return app;
    }

    @Transactional(readOnly = true)
    public List<AssetApplication> search(ApplicationStatus status, Long officeId, Long clientId, AppUser user) {
        return applications.search(status, officeAccess.scope(user, officeId), clientId);
    }

    /** Refreshes from Fineract and returns live progress (null once the application is past the queue). */
    @Transactional
    public Progress refresh(long id, AppUser user) {
        return sync.sync(get(id, user));
    }

    @Transactional
    public AssetApplication reprice(long id, Long quoteId, BigDecimal newCost, AppUser user) {
        AssetApplication app = get(id, user);
        SupplierQuote quote = null;
        if (quoteId != null) {
            quote = validQuote(quoteId, app.getCatalogueItem(), LocalDate.now(clock));
            newCost = quote.getPrice();
        }
        if (newCost == null) {
            throw new BusinessRuleException("Give either a quote or a new asset cost");
        }
        app.reprice(newCost, quote, clock.instant());
        return app;
    }

    /**
     * Cancels the application. The member's deposit stays in the Fineract account; refund or
     * transfer it in Mifos according to the cancellation policy.
     */
    @Transactional
    public AssetApplication cancel(long id, String reason, AppUser user) {
        AssetApplication app = get(id, user);
        app.cancel(reason);
        return app;
    }

    private SupplierQuote validQuote(long quoteId, AssetCatalogueItem item, LocalDate today) {
        SupplierQuote quote = quotes.findById(quoteId).orElseThrow(() -> new NotFoundException("Quote", quoteId));
        if (!quote.getCatalogueItem().getId().equals(item.getId())) {
            throw new BusinessRuleException("Quote " + quoteId + " is for a different asset");
        }
        if (!quote.isValidOn(today)) {
            throw new BusinessRuleException("Quote " + quoteId + " expired on " + quote.getValidUntil());
        }
        return quote;
    }

    private static String newReference() {
        StringBuilder sb = new StringBuilder("AF-");
        for (int i = 0; i < 8; i++) {
            sb.append(REF_CHARS.charAt(RANDOM.nextInt(REF_CHARS.length())));
        }
        return sb.toString();
    }
}
