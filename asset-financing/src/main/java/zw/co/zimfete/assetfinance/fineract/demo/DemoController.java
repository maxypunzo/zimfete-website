package zw.co.zimfete.assetfinance.fineract.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import zw.co.zimfete.assetfinance.application.ApplicationSyncService;
import zw.co.zimfete.assetfinance.fineract.FineractClient;

/** Demo mode only: stands in for the teller actions that would normally happen in Mifos. */
@RestController
@ConditionalOnProperty(name = "zimfete.fineract.mode", havingValue = "demo")
public class DemoController {

    private final DemoFineractClient fineract;
    private final ApplicationSyncService sync;
    private final Clock clock;

    public DemoController(FineractClient fineract, ApplicationSyncService sync, Clock clock) {
        this.fineract = (DemoFineractClient) fineract;
        this.sync = sync;
        this.clock = clock;
    }

    public record DepositRequest(@NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    @PostMapping("/api/demo/savings/{savingsId}/deposit")
    @PreAuthorize("hasRole('OFFICER')")
    public ResponseEntity<Void> deposit(@PathVariable long savingsId, @Valid @RequestBody DepositRequest r) {
        fineract.deposit(savingsId, r.amount(), LocalDate.now(clock));
        sync.syncBySavingsAccount(savingsId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/demo/loans/{loanId}/repay")
    @PreAuthorize("hasRole('OFFICER')")
    public ResponseEntity<Void> repay(@PathVariable long loanId) {
        fineract.repayInFull(loanId);
        sync.syncByLoan(loanId);
        return ResponseEntity.noContent().build();
    }
}
