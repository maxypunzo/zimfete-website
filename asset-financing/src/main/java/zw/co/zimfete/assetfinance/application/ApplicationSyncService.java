package zw.co.zimfete.assetfinance.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import zw.co.zimfete.assetfinance.config.ZimfeteProperties;
import zw.co.zimfete.assetfinance.fineract.FineractClient;
import zw.co.zimfete.assetfinance.fineract.FineractClient.LoanInfo;
import zw.co.zimfete.assetfinance.fineract.FineractClient.SavingsAccountInfo;

/**
 * Pulls balances and loan status from Fineract into applications. Runs on a schedule, and
 * immediately when Fineract sends a hook for a deposit or repayment.
 */
@Service
public class ApplicationSyncService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationSyncService.class);

    private final AssetApplicationRepository applications;
    private final FineractClient fineract;
    private final MemberNotifier notifier;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int windowMonths;

    public ApplicationSyncService(AssetApplicationRepository applications, FineractClient fineract,
                                  MemberNotifier notifier, ApplicationEventPublisher events, TransactionTemplate tx,
                                  Clock clock, ZimfeteProperties properties) {
        this.applications = applications;
        this.fineract = fineract;
        this.notifier = notifier;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
        this.windowMonths = properties.policy().forecastWindowMonths();
    }

    /** Must be called inside a transaction. Returns the latest progress for open applications. */
    public Progress sync(AssetApplication app) {
        if (app.getStatus().isOpen()) {
            SavingsAccountInfo account = fineract.getSavingsAccount(app.getFineractSavingsAccountId());
            Progress progress = Progress.calculate(app.getDepositTarget(), account.balance(), account.transactions(),
                    LocalDate.now(clock), windowMonths);
            if (app.recordBalance(progress, clock.instant())) {
                notifier.depositTargetReached(app);
            }
            return progress;
        }
        if (app.getStatus() == ApplicationStatus.REPAYING && app.getFineractLoanId() != null) {
            LoanInfo loan = fineract.getLoan(app.getFineractLoanId());
            if (loan.closed()) {
                app.markPaidOff();
                events.publishEvent(new ApplicationPaidOff(app.getId()));
            }
        }
        return null;
    }

    public void syncById(long applicationId) {
        tx.executeWithoutResult(s -> applications.findById(applicationId).ifPresent(this::sync));
    }

    public boolean syncBySavingsAccount(long savingsAccountId) {
        return Boolean.TRUE.equals(tx.execute(s -> applications.findByFineractSavingsAccountId(savingsAccountId)
                .map(a -> { sync(a); return true; }).orElse(false)));
    }

    public boolean syncByLoan(long loanId) {
        return Boolean.TRUE.equals(tx.execute(s -> applications.findByFineractLoanId(loanId)
                .map(a -> { sync(a); return true; }).orElse(false)));
    }

    /** Safety net for missed hooks. Each application is synced in its own transaction. */
    @Scheduled(cron = "${zimfete.sync.cron:0 0 * * * *}", zone = "Africa/Harare")
    public void syncAll() {
        List<Long> ids = applications.findByStatusIn(EnumSet.of(ApplicationStatus.SAVING,
                ApplicationStatus.QUALIFIED, ApplicationStatus.REPAYING)).stream().map(AssetApplication::getId).toList();
        int failed = 0;
        for (Long id : ids) {
            try {
                syncById(id);
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not sync application {}: {}", id, e.getMessage());
            }
        }
        log.info("Synced {} applications from Fineract ({} failed)", ids.size(), failed);
    }
}
