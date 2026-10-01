package zw.co.zimfete.assetfinance.procurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.co.zimfete.assetfinance.security.AppUser;

@RestController
@RequestMapping("/api")
public class ProcurementController {

    private final ProcurementService service;

    public ProcurementController(ProcurementService service) {
        this.service = service;
    }

    public record CreateRequest(@NotNull Long supplierId, Long quoteId, String queueOverrideReason, String notes) {
    }

    public record CancelRequest(@NotBlank String reason) {
    }

    public record PurchaseOrderView(Long id, Long applicationId, String applicationReference, String memberName,
                                    long officeId, Long supplierId, String supplierName, Long quoteId,
                                    BigDecimal amount, String currency, PurchaseOrderStatus status,
                                    String queueOverrideReason, String notes, String createdBy, Instant createdAt,
                                    String approvedBy, Instant approvedAt, String cancelledBy, String cancelReason,
                                    Instant completedAt) {
        static PurchaseOrderView of(PurchaseOrder p) {
            var a = p.getApplication();
            return new PurchaseOrderView(p.getId(), a.getId(), a.getReference(), a.getMemberName(), a.getOfficeId(),
                    p.getSupplier().getId(), p.getSupplier().getName(),
                    p.getQuote() == null ? null : p.getQuote().getId(), p.getAmount(), p.getCurrency(), p.getStatus(),
                    p.getQueueOverrideReason(), p.getNotes(), p.getCreatedBy(), p.getCreatedAt(), p.getApprovedBy(),
                    p.getApprovedAt(), p.getCancelledBy(), p.getCancelReason(), p.getCompletedAt());
        }
    }

    @PostMapping("/applications/{applicationId}/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER')")
    public PurchaseOrderView create(@PathVariable long applicationId, @Valid @RequestBody CreateRequest r,
                                    @AuthenticationPrincipal AppUser user) {
        return PurchaseOrderView.of(service.create(applicationId, r.supplierId(), r.quoteId(),
                r.queueOverrideReason(), r.notes(), user));
    }

    @GetMapping("/purchase-orders")
    public List<PurchaseOrderView> search(@RequestParam(required = false) PurchaseOrderStatus status,
                                          @RequestParam(required = false) Long officeId,
                                          @AuthenticationPrincipal AppUser user) {
        return service.search(status, officeId, user).stream().map(PurchaseOrderView::of).toList();
    }

    @GetMapping("/purchase-orders/{id}")
    public PurchaseOrderView get(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        return PurchaseOrderView.of(service.get(id, user));
    }

    @PostMapping("/purchase-orders/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public PurchaseOrderView approve(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        return PurchaseOrderView.of(service.approve(id, user));
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public PurchaseOrderView cancel(@PathVariable long id, @Valid @RequestBody CancelRequest r,
                                    @AuthenticationPrincipal AppUser user) {
        return PurchaseOrderView.of(service.cancel(id, r.reason(), user));
    }
}
