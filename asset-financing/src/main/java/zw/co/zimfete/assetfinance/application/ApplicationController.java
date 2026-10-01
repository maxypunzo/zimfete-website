package zw.co.zimfete.assetfinance.application;

import java.math.BigDecimal;
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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.co.zimfete.assetfinance.security.AppUser;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    public record OpenRequest(@NotNull Long clientId, @NotNull Long catalogueItemId, Long quoteId) {
    }

    public record RepriceRequest(Long quoteId, @DecimalMin("0.01") BigDecimal assetCost) {
    }

    public record CancelRequest(@NotBlank String reason) {
    }

    public record ApplicationWithProgress(ApplicationView application, Progress progress) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER')")
    public ApplicationView open(@Valid @RequestBody OpenRequest r, @AuthenticationPrincipal AppUser user) {
        return ApplicationView.of(service.open(r.clientId(), r.catalogueItemId(), r.quoteId(), user));
    }

    @GetMapping
    public List<ApplicationView> search(@RequestParam(required = false) ApplicationStatus status,
                                        @RequestParam(required = false) Long officeId,
                                        @RequestParam(required = false) Long clientId,
                                        @AuthenticationPrincipal AppUser user) {
        return service.search(status, officeId, clientId, user).stream().map(ApplicationView::of).toList();
    }

    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        return ApplicationView.of(service.get(id, user));
    }

    /** Fetches the live balance from Fineract, updates the queue status and returns progress. */
    @PostMapping("/{id}/refresh")
    public ApplicationWithProgress refresh(@PathVariable long id, @AuthenticationPrincipal AppUser user) {
        Progress progress = service.refresh(id, user);
        return new ApplicationWithProgress(ApplicationView.of(service.get(id, user)), progress);
    }

    @PostMapping("/{id}/reprice")
    @PreAuthorize("hasRole('MANAGER')")
    public ApplicationView reprice(@PathVariable long id, @Valid @RequestBody RepriceRequest r,
                                   @AuthenticationPrincipal AppUser user) {
        return ApplicationView.of(service.reprice(id, r.quoteId(), r.assetCost(), user));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public ApplicationView cancel(@PathVariable long id, @Valid @RequestBody CancelRequest r,
                                  @AuthenticationPrincipal AppUser user) {
        return ApplicationView.of(service.cancel(id, r.reason(), user));
    }
}
