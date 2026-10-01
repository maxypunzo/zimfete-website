package zw.co.zimfete.assetfinance.procurement;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import zw.co.zimfete.assetfinance.application.ApplicationStatus;
import zw.co.zimfete.assetfinance.application.AssetApplication;
import zw.co.zimfete.assetfinance.application.AssetApplicationRepository;
import zw.co.zimfete.assetfinance.catalogue.Supplier;
import zw.co.zimfete.assetfinance.catalogue.SupplierQuote;
import zw.co.zimfete.assetfinance.catalogue.SupplierQuoteRepository;
import zw.co.zimfete.assetfinance.catalogue.SupplierRepository;
import zw.co.zimfete.assetfinance.queue.QueueService;
import zw.co.zimfete.assetfinance.security.AppUser;
import zw.co.zimfete.assetfinance.security.OfficeAccess;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;
import zw.co.zimfete.assetfinance.web.NotFoundException;

@Service
@Transactional
public class ProcurementService {

    private final PurchaseOrderRepository orders;
    private final AssetApplicationRepository applications;
    private final SupplierRepository suppliers;
    private final SupplierQuoteRepository quotes;
    private final QueueService queue;
    private final OfficeAccess officeAccess;
    private final Clock clock;

    public ProcurementService(PurchaseOrderRepository orders, AssetApplicationRepository applications,
                              SupplierRepository suppliers, SupplierQuoteRepository quotes, QueueService queue,
                              OfficeAccess officeAccess, Clock clock) {
        this.orders = orders;
        this.applications = applications;
        this.suppliers = suppliers;
        this.quotes = quotes;
        this.queue = queue;
        this.officeAccess = officeAccess;
        this.clock = clock;
    }

    /**
     * Raises a purchase order for a qualified member. The amount is always the application's
     * locked asset cost; if the supplier's price changed, reprice the application first so the
     * deposit target and the loan stay consistent. Serving a member out of queue order requires
     * a written reason, which is kept for audit.
     */
    public PurchaseOrder create(long applicationId, long supplierId, Long quoteId, String overrideReason,
                                String notes, AppUser user) {
        AssetApplication app = applications.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application", applicationId));
        officeAccess.check(user, app.getOfficeId());
        if (app.getStatus() != ApplicationStatus.QUALIFIED) {
            throw new BusinessRuleException("Application " + app.getReference() + " is " + app.getStatus()
                    + "; only members in the queue can be served");
        }
        if (orders.existsByApplicationIdAndStatusIn(app.getId(),
                EnumSet.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.APPROVED))) {
            throw new BusinessRuleException("Application " + app.getReference() + " already has an open purchase order");
        }
        boolean blank = overrideReason == null || overrideReason.isBlank();
        if (!queue.isFirstInQueue(app) && blank) {
            throw new BusinessRuleException("Application " + app.getReference()
                    + " is not first in the queue; give a reason to serve it out of order");
        }

        Supplier supplier = suppliers.findById(supplierId)
                .filter(Supplier::isActive)
                .orElseThrow(() -> new NotFoundException("Active supplier", supplierId));
        SupplierQuote quote = null;
        if (quoteId != null) {
            quote = quotes.findById(quoteId).orElseThrow(() -> new NotFoundException("Quote", quoteId));
            if (!quote.getSupplier().getId().equals(supplier.getId())
                    || !quote.getCatalogueItem().getId().equals(app.getCatalogueItem().getId())) {
                throw new BusinessRuleException("Quote " + quoteId + " is not from this supplier for this asset");
            }
            if (!quote.isValidOn(LocalDate.now(clock))) {
                throw new BusinessRuleException("Quote " + quoteId + " expired on " + quote.getValidUntil());
            }
            if (quote.getPrice().compareTo(app.getAssetCost()) != 0) {
                throw new BusinessRuleException("Quote price " + quote.getPrice() + " differs from the application cost "
                        + app.getAssetCost() + "; reprice the application first");
            }
        }
        return orders.save(new PurchaseOrder(app, supplier, quote, blank ? null : overrideReason, notes,
                user.username()));
    }

    public PurchaseOrder approve(long orderId, AppUser user) {
        PurchaseOrder po = get(orderId, user);
        po.approve(user.username(), clock.instant());
        po.getApplication().startProcurement();
        return po;
    }

    public PurchaseOrder cancel(long orderId, String reason, AppUser user) {
        PurchaseOrder po = get(orderId, user);
        boolean wasApproved = po.getStatus() == PurchaseOrderStatus.APPROVED;
        AssetApplication app = po.getApplication();
        if (wasApproved && app.getStatus() != ApplicationStatus.PROCUREMENT) {
            throw new BusinessRuleException("The asset has already been delivered; this order can no longer be cancelled");
        }
        po.cancel(user.username(), reason);
        if (wasApproved) {
            app.returnToQueue();
        }
        return po;
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(long orderId, AppUser user) {
        PurchaseOrder po = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Purchase order", orderId));
        officeAccess.check(user, po.getApplication().getOfficeId());
        return po;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> search(PurchaseOrderStatus status, Long officeId, AppUser user) {
        return orders.search(status, officeAccess.scope(user, officeId));
    }
}
